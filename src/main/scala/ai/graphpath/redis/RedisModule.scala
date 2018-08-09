package ai.graphpath.redis

import akka.actor.ActorSystem
import com.google.inject.{AbstractModule, Provides}
import com.typesafe.config.{Config, ConfigException}
import com.typesafe.scalalogging.StrictLogging
import io.lettuce.core.RedisClient
import javax.inject.Singleton

import scala.concurrent.ExecutionContext

object RedisModule extends AbstractModule with StrictLogging {
  override def configure(): Unit = ()

  @Provides
  @Singleton
  def clientProvider(config: Config): Option[RedisClient] = try {
    val uri = config.getString("redis.uri")
    val client = RedisClient.create(uri)
    logger.info(s"Redis cache enabled, uri: '$uri'.")
    Some(client)
  } catch {
    case e: ConfigException.Missing => {
      logger.info("Redis cache disabled, uri not found.")
      None
    }
  }

  @Provides
  @Singleton
  def cacheProvider(client: Option[RedisClient], config: Config,
                    ec: ExecutionContext, as: ActorSystem): Option[Cache] = {
    client.map(new Cache(_, config)(ec, as))
  }
}
