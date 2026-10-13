package tokenz.data

import zio.prelude.Subtype

final case class Card(
    number: Card.Number,
    expiryMonth: Card.ExpiryMonth,
    expiryYear: Card.ExpiryYear,
    cvc: Card.Cvc,
    holderName: Card.HolderName,
)

object Card {

  object Number extends Subtype[String]
  type Number = Number.Type

  object ExpiryMonth extends Subtype[Int]
  type ExpiryMonth = ExpiryMonth.Type

  object ExpiryYear extends Subtype[Int]
  type ExpiryYear = ExpiryYear.Type

  object Cvc extends Subtype[String]
  type Cvc = Cvc.Type

  object HolderName extends Subtype[String]
  type HolderName = HolderName.Type

}
