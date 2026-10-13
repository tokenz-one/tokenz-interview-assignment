package tokenz.stores

import zio.*

import tokenz.data.Authorization

trait AuthorizationStore {
  def create(authorization: Authorization): UIO[Unit]
  def update(authorization: Authorization): IO[AuthorizationStore.NotFound, Unit]
  def get(id: Authorization.Id): UIO[Option[Authorization]]
}

object AuthorizationStore {
  final case class NotFound(id: Authorization.Id)
}
