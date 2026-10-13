package tokenz.data

import java.time.Instant

import zio.prelude.Subtype

final case class Authorization(
    id: Authorization.Id,
    amount: Money,
    card: Card,
    status: Authorization.Status,
    createdAt: Instant,
    updatedAt: Instant,
)

object Authorization {

  object Id extends Subtype[String]
  type Id = Id.Type

  enum Status {
    case Pending
    case Authorized
    case Rejected(declineCode: DeclineCode)
    case Failed(reason: String, errorCode: Option[String])
  }

}

object DeclineCode extends Subtype[String]
type DeclineCode = DeclineCode.Type
