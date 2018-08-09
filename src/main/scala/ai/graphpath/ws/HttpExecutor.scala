package ai.graphpath.ws

import ai.graphpath.messages.KafkaMessage
import ai.graphpath.utils.DurationOps._
import ai.graphpath.utils.FutureOps.Retry
import akka.actor.ActorSystem
import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import javax.inject.{Inject, Singleton}
import play.api.libs.ws.StandaloneWSResponse
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class HttpExecutor @Inject()(ws: StandaloneAhcWSClient, config: Config)
                            (implicit mapper: ObjectMapper, ec: ExecutionContext, actorSystem: ActorSystem)
  extends Retry with StrictLogging {

  private implicit val httpConfig: Config = config.getConfig("http")
  private val timeout = httpConfig.getDuration("retry.timeout")

  def syncExecute(message: KafkaMessage): StandaloneWSResponse =
    Await.result(execute(message), timeout)

  def execute(message: KafkaMessage): Future[StandaloneWSResponse] = retry {
    val request = message.buildRequest(ws)
    logger.info(s"${request.method} ${request.headers} - Headers: ${request.headers}")

    request
      .execute()
      .map(only2xx)
  }

  private def only2xx(response: StandaloneWSResponse): StandaloneWSResponse =
    if ((200 until 300).contains(response.status))
      response
    else
      throw WsException(response)

  def close(): Unit = {
    logger.info("Stopping http executor...")
    ws.close()
    actorSystem.terminate()
  }

}
