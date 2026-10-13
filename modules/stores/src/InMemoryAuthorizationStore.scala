package tokenz.stores

import zio.*

import tokenz.data.Authorization

final class InMemoryAuthorizationStore(state: Ref[Map[Authorization.Id, Authorization]])
    extends AuthorizationStore {

  def create(authorization: Authorization): UIO[Unit] =
    state.update(_.updated(authorization.id, authorization))

  def update(authorization: Authorization): IO[AuthorizationStore.NotFound, Unit] =
    state
      .modify { all =>
        if (all.contains(authorization.id)) (true, all.updated(authorization.id, authorization))
        else (false, all)
      }
      .flatMap(found => ZIO.fail(AuthorizationStore.NotFound(authorization.id)).unless(found))
      .unit

  def get(id: Authorization.Id): UIO[Option[Authorization]] =
    state.get.map(_.get(id))

}

object InMemoryAuthorizationStore {

  val live: ULayer[AuthorizationStore] =
    ZLayer.fromZIO(
      Ref.make(Map.empty[Authorization.Id, Authorization]).map(new InMemoryAuthorizationStore(_))
    )

}
