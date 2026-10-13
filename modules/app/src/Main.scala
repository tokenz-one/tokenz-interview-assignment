package tokenz.app

import com.typesafe.config.ConfigFactory
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.*
import zio.config.magnolia.deriveConfig
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.Server
import zio.logging.{ ConsoleLoggerConfig, consoleLogger }

import tokenz.api.AuthorizationRoutes
import tokenz.providers.ProxyCardPayments
import tokenz.scenarios.AuthorizeCardScenario
import tokenz.stores.InMemoryAuthorizationStore

object Main extends ZIOAppDefault {

  final case class ServerConfig(port: Int)

  private val serverConfig = deriveConfig[ServerConfig].nested("server")

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    (Runtime.removeDefaultLoggers >>> consoleLogger(ConsoleLoggerConfig.default)) ++
      Runtime.setConfigProvider(
        TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load("local.conf"))
      )

  override def run =
    (for {
      config <- ZIO.config(serverConfig)
      routes <- ZIO.serviceWith[AuthorizationRoutes](_.routes)
      _      <- ZIO.logInfo(s"Listening on port ${config.port}")
      _      <- Server.serve(routes).provide(Server.defaultWithPort(config.port))
    } yield ()).provide(
      HttpClientZioBackend.layer(),
      InMemoryAuthorizationStore.live,
      ProxyCardPayments.live,
      AuthorizeCardScenario.live,
      AuthorizationRoutes.live,
    )

}
