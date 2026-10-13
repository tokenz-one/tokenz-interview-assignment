package tokenz.data

import zio.prelude.Subtype

object Currency extends Subtype[String]
type Currency = Currency.Type

final case class Money(value: Long, currency: Currency)
