package tokenz.data

final case class UnexpectedError(message: String, cause: Option[Throwable])
    extends Exception(message, cause.orNull)
