package ai.graphpath.redis

import ai.graphpath.utils.DurationOps._
import ai.graphpath.utils.FutureOps.Retry
import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import io.lettuce.core.RedisClient

import scala.compat.java8.FutureConverters._
import scala.concurrent.{Await, ExecutionContext, Future}

class Cache(client: RedisClient, config: Config)
           (implicit ec: ExecutionContext, as: ActorSystem)
  extends StrictLogging with Retry {

  private implicit val redisConfig: Config = config.getConfig("redis")
  private val ttl = redisConfig.getDuration("ttl")
  private val timeout = redisConfig.getDuration("retry.timeout")
  private val connection = client.connect
  private val commands = connection.async

  def syncExists(id: String): Boolean =
    Await.result(exists(id), timeout)

  def exists(id: String): Future[Boolean] = retry {
    commands
      .exists(id).toScala
      .map(_ > 0)
  }

  def syncInsert(id: String): Unit =
    Await.result(insert(id), timeout)

  def insert(id: String): Future[Unit] = retry {
    commands
      .psetex(id, ttl.toMillis, System.currentTimeMillis().toString).toScala
      .map(_ => ())
  }

  def close(): Unit = {
    logger.info("Stopping Redis cache...")
    connection.close()
    client.shutdown()
  }

}
