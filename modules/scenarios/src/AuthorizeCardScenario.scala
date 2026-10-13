package tokenz.scenarios

import zio.*

import tokenz.data.{ Authorization, Card, Money, UnexpectedError }
import tokenz.providers.CardPayments
import tokenz.stores.AuthorizationStore

final class AuthorizeCardScenario(store: AuthorizationStore, cardPayments: CardPayments) {

  def run(input: AuthorizeCardScenario.Input): IO[UnexpectedError, Authorization] =
    for {
      id        <- Random.nextUUID.map(uuid => Authorization.Id.wrap(uuid.toString))
      createdAt <- Clock.instant
      pending = Authorization(
                  id,
                  input.amount,
                  input.card,
                  Authorization.Status.Pending,
                  createdAt,
                  createdAt,
                )
      _         <- store.create(pending)
      result    <- cardPayments.authorize(CardPayments.Request(id, input.amount, input.card))
      updatedAt <- Clock.instant
      completed =
        pending.copy(status = AuthorizeCardScenario.toStatus(result), updatedAt = updatedAt)
      _ <-
        store
          .update(completed)
          .mapError(notFound => UnexpectedError(s"Authorization ${notFound.id} disappeared", None))
      _ <- ZIO.logInfo(s"Authorization $id completed with ${completed.status}")
    } yield completed

}

object AuthorizeCardScenario {

  final case class Input(amount: Money, card: Card)

  val live = ZLayer.derive[AuthorizeCardScenario]

  private def toStatus(result: CardPayments.Result): Authorization.Status =
    result match {
      case CardPayments.Result.Authorized                => Authorization.Status.Authorized
      case CardPayments.Result.Declined(code)            => Authorization.Status.Rejected(code)
      case CardPayments.Result.Failed(reason, errorCode) =>
        Authorization.Status.Failed(reason, errorCode)
    }

}
