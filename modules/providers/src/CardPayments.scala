package tokenz.providers

import zio.*

import tokenz.data.{ Authorization, Card, DeclineCode, Money, UnexpectedError }

trait CardPayments {
  def authorize(request: CardPayments.Request): IO[UnexpectedError, CardPayments.Result]
}

object CardPayments {

  final case class Request(reference: Authorization.Id, amount: Money, card: Card)

  enum Result {
    case Authorized
    case Declined(code: DeclineCode)
    case Failed(reason: String, errorCode: Option[String])
  }

}
