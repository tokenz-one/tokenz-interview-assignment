package tokenz.api

import tokenz.data.{ Authorization, Card, Currency, Money }
import tokenz.scenarios.AuthorizeCardScenario

object AuthorizationMapping {

  def toInput(request: CreateAuthorizationRequest): Either[String, AuthorizeCardScenario.Input] = {
    val amount = request.amount
    val card   = request.card
    val errors = List(
      Option.when(amount.value <= 0)("amount.value must be positive"),
      Option.when(!amount.currency.matches("[A-Z]{3}"))(
        "amount.currency must be a 3-letter uppercase ISO 4217 code"
      ),
      Option.when(!card.number.matches("[0-9]{12,19}"))("card.number must be 12-19 digits"),
      Option.when(card.expiryMonth < 1 || card.expiryMonth > 12)(
        "card.expiryMonth must be between 1 and 12"
      ),
      Option.when(card.expiryYear < 1000 || card.expiryYear > 9999)(
        "card.expiryYear must be a four-digit year"
      ),
      Option.when(!card.cvc.matches("[0-9]{3,4}"))("card.cvc must be 3-4 digits"),
      Option.when(card.holderName.isBlank)("card.holderName must not be blank"),
    ).flatten

    if (errors.nonEmpty) Left(errors.mkString("; "))
    else
      Right(
        AuthorizeCardScenario.Input(
          Money(amount.value, Currency.wrap(amount.currency)),
          Card(
            Card.Number.wrap(card.number),
            Card.ExpiryMonth.wrap(card.expiryMonth),
            Card.ExpiryYear.wrap(card.expiryYear),
            Card.Cvc.wrap(card.cvc),
            Card.HolderName.wrap(card.holderName),
          ),
        )
      )
  }

  def toResponse(authorization: Authorization): AuthorizationResponse = {
    val (status, declineCode, failureReason, errorCode) = authorization.status match {
      case Authorization.Status.Pending                   => ("pending", None, None, None)
      case Authorization.Status.Authorized                => ("authorized", None, None, None)
      case Authorization.Status.Rejected(code)            => ("rejected", Some(code), None, None)
      case Authorization.Status.Failed(reason, errorCode) =>
        ("failed", None, Some(reason), errorCode)
    }
    AuthorizationResponse(
      authorization.id,
      status,
      CreateAuthorizationRequest.Amount(authorization.amount.value, authorization.amount.currency),
      authorization.card.number.takeRight(4),
      declineCode,
      failureReason,
      errorCode,
      authorization.createdAt,
      authorization.updatedAt,
    )
  }

}
