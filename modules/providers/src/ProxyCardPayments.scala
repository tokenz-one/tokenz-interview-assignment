package tokenz.providers

import scala.util.Try

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import sttp.client3.*
import sttp.client3.httpclient.zio.SttpClient
import sttp.model.StatusCode
import zio.*
import zio.config.magnolia.deriveConfig

import tokenz.data.{ DeclineCode, UnexpectedError }

final class ProxyCardPayments(client: SttpClient, config: ProxyCardPayments.Config)
    extends CardPayments {

  import ProxyCardPayments.*

  def authorize(request: CardPayments.Request): IO[UnexpectedError, CardPayments.Result] = {
    val httpRequest = basicRequest
      .post(uri"${config.baseUrl}/providers/${config.providerId}/authorize")
      .contentType("application/json")
      .body(writeToString(toRequest(request)))
      .readTimeout(config.requestTimeout.asScala)
      .response(asStringAlways)

    client.send(httpRequest).either.flatMap {
      case Left(error) =>
        val cause   = Option(error.getCause).getOrElse(error)
        val message = Option(cause.getMessage).getOrElse(cause.getClass.getSimpleName)
        ZIO.succeed(CardPayments.Result.Failed(s"Provider unreachable: $message", None))
      case Right(response) => toResult(response.code, response.body)
    }
  }

  private def toResult(status: StatusCode, body: String): IO[UnexpectedError, CardPayments.Result] =
    if (status == StatusCode.Ok)
      ZIO
        .attempt(readFromString[AuthorizeResponse](body))
        .mapError(e => UnexpectedError("Malformed provider response", Some(e)))
        .flatMap(fromOutcome)
    else {
      val reason =
        if (status == StatusCode.TooManyRequests) "Provider rate limit exceeded"
        else if (status.isServerError) s"Provider error (HTTP ${status.code})"
        else s"Provider rejected the request (HTTP ${status.code})"
      ZIO.succeed(CardPayments.Result.Failed(reason, errorCode(body)))
    }

  private def fromOutcome(response: AuthorizeResponse): IO[UnexpectedError, CardPayments.Result] =
    response.outcome match {
      case "authorized" => ZIO.succeed(CardPayments.Result.Authorized)
      case "declined"   =>
        ZIO.succeed(
          CardPayments.Result.Declined(DeclineCode.wrap(response.declineCode.getOrElse("unknown")))
        )
      case other => ZIO.fail(UnexpectedError(s"Unknown provider outcome: $other", None))
    }

  private def errorCode(body: String): Option[String] =
    Try(readFromString[ErrorResponse](body)).toOption.map(_.error)

}

object ProxyCardPayments {

  final case class Config(baseUrl: String, providerId: String, requestTimeout: Duration)

  object Config {
    val config: zio.Config[Config] = deriveConfig[Config].nested("provider")
  }

  val live: ZLayer[SttpClient, zio.Config.Error, CardPayments] =
    ZLayer.fromZIO(
      for {
        client <- ZIO.service[SttpClient]
        config <- ZIO.config(Config.config)
      } yield new ProxyCardPayments(client, config)
    )

  final private case class WireAmount(value: Long, currency: String)

  final private case class WireCard(
      number: String,
      expiryMonth: Int,
      expiryYear: Int,
      cvc: String,
      holderName: String,
  )

  final private case class AuthorizeRequest(reference: String, amount: WireAmount, card: WireCard)

  final private case class AuthorizeResponse(
      reference: String,
      outcome: String,
      declineCode: Option[String],
  )

  final private case class ErrorResponse(error: String)

  private given JsonValueCodec[AuthorizeRequest]  = JsonCodecMaker.make
  private given JsonValueCodec[AuthorizeResponse] = JsonCodecMaker.make
  private given JsonValueCodec[ErrorResponse]     = JsonCodecMaker.make

  private def toRequest(request: CardPayments.Request): AuthorizeRequest =
    AuthorizeRequest(
      request.reference,
      WireAmount(request.amount.value, request.amount.currency),
      WireCard(
        request.card.number,
        request.card.expiryMonth,
        request.card.expiryYear,
        request.card.cvc,
        request.card.holderName,
      ),
    )

}
