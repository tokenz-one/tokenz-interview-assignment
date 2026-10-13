package tokenz.api

import sttp.model.StatusCode
import sttp.tapir.generic.auto.*
import sttp.tapir.json.jsoniter.*
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.ziohttp.{ ZioHttpInterpreter, ZioHttpServerOptions }
import sttp.tapir.ztapir.*
import zio.*
import zio.http.{ Response, Routes }

import tokenz.api.AuthorizationEndpoints.given
import tokenz.data.Authorization
import tokenz.scenarios.AuthorizeCardScenario
import tokenz.stores.AuthorizationStore

final class AuthorizationRoutes(scenario: AuthorizeCardScenario, store: AuthorizationStore) {

  private val create = AuthorizationEndpoints.create.zServerLogic[Any] { request =>
    for {
      input <- ZIO
                 .fromEither(AuthorizationMapping.toInput(request))
                 .mapError(message => (StatusCode.BadRequest, ErrorResponse(message)))
      authorization <-
        scenario
          .run(input)
          .tapError(error =>
            ZIO.logErrorCause("Authorization failed unexpectedly", Cause.fail(error))
          )
          .orElseFail((StatusCode.InternalServerError, ErrorResponse("Internal error")))
    } yield AuthorizationMapping.toResponse(authorization)
  }

  private val get = AuthorizationEndpoints.get.zServerLogic[Any] { id =>
    store
      .get(Authorization.Id.wrap(id))
      .someOrFail((StatusCode.NotFound, ErrorResponse(s"Authorization $id not found")))
      .map(AuthorizationMapping.toResponse)
  }

  private val options: ZioHttpServerOptions[Any] =
    ZioHttpServerOptions
      .customiseInterceptors[Any]
      .serverLog(
        ZioHttpServerOptions
          .defaultServerLog[Any]
          .copy(logWhenHandled = false, logAllDecodeFailures = false)
      )
      .defaultHandlers(message =>
        ValuedEndpointOutput(jsonBody[ErrorResponse], ErrorResponse(message))
      )
      .options

  val routes: Routes[Any, Response] = ZioHttpInterpreter(options).toHttp(List(create, get))

}

object AuthorizationRoutes {
  val live = ZLayer.derive[AuthorizationRoutes]
}
