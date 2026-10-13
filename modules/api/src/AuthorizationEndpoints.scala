package tokenz.api

import java.time.Instant

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.jsoniter.*

final case class CreateAuthorizationRequest(
    amount: CreateAuthorizationRequest.Amount,
    card: CreateAuthorizationRequest.Card,
)

object CreateAuthorizationRequest {

  final case class Amount(value: Long, currency: String)

  final case class Card(
      number: String,
      expiryMonth: Int,
      expiryYear: Int,
      cvc: String,
      holderName: String,
  )

}

final case class AuthorizationResponse(
    id: String,
    status: String,
    amount: CreateAuthorizationRequest.Amount,
    cardLast4: String,
    declineCode: Option[String],
    failureReason: Option[String],
    errorCode: Option[String],
    createdAt: Instant,
    updatedAt: Instant,
)

final case class ErrorResponse(message: String)

object AuthorizationEndpoints {

  given JsonValueCodec[CreateAuthorizationRequest] = JsonCodecMaker.make
  given JsonValueCodec[AuthorizationResponse]      = JsonCodecMaker.make
  given JsonValueCodec[ErrorResponse]              = JsonCodecMaker.make

  private val base =
    endpoint.in("authorizations").errorOut(statusCode.and(jsonBody[ErrorResponse]))

  val create: PublicEndpoint[
    CreateAuthorizationRequest,
    (StatusCode, ErrorResponse),
    AuthorizationResponse,
    Any,
  ] =
    base.post
      .in(jsonBody[CreateAuthorizationRequest])
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[AuthorizationResponse])

  val get: PublicEndpoint[String, (StatusCode, ErrorResponse), AuthorizationResponse, Any] =
    base.get
      .in(path[String]("id"))
      .out(jsonBody[AuthorizationResponse])

}
