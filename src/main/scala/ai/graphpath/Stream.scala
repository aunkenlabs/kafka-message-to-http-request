package ai.graphpath

import java.util.Properties
import java.util.concurrent.TimeUnit

import ai.graphpath.messages.KafkaMessage
import ai.graphpath.redis.Cache
import ai.graphpath.ws.HttpExecutor
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import javax.inject.{Inject, Singleton}
import org.apache.kafka.streams.KafkaStreams.State
import org.apache.kafka.streams.kstream.ValueMapper
import org.apache.kafka.streams.{KafkaStreams, StreamsBuilder}

import scala.concurrent.Await

@Singleton
class Stream @Inject()(http: HttpExecutor, cache: Option[Cache], mapper: ObjectMapper)
                      (implicit config: Config) extends StrictLogging {

  private val kafkaConfig = config.getConfig("kafka")
  private val topic = kafkaConfig.getString("topic")
  private val topology = {
    val builder = new StreamsBuilder
    // note: type inference is not capable to handle this inline
    val jsonToMessage: ValueMapper[JsonNode, KafkaMessage] = v =>
      mapper.treeToValue(v, classOf[KafkaMessage])

    builder
      .stream[Array[Byte], JsonNode](topic)
      // skip non http messages
      .filter((_, v) => v.path("type").textValue() == "http")
      .mapValues[KafkaMessage](jsonToMessage)
      .foreach((_, message) => process(message))
    builder.build()
  }

  private def process(message: KafkaMessage) = {
    import concurrent.duration._
    val exists = doIfCacheable(message) { (id, c) =>
      Await.result(c.exists(id), 5.seconds)
    }

    if (exists.getOrElse(false)) {
      // send http request
      http.syncExecute(message)
      // if successful, persist id
      doIfCacheable(message)((id, c) => c.insert(id))
    }
  }

  private def doIfCacheable[A](message: KafkaMessage)(f: (String, Cache) => A): Option[A] = {
    for {
      id <- message.id
      c <- cache
    } yield f(id, c)
  }

  private val streamingConfig = kafkaConfig.toProperties
  private val stream = new KafkaStreams(topology, streamingConfig)

  stream.setUncaughtExceptionHandler { (t, e) =>
    logger.error("Uncaught exception on kafka streams", e)
  }

  stream.setStateListener((newState, _) => {
    newState match {
      case State.ERROR => stop()
      case _ => // do nothing
    }
  })

  def start(): Unit = {
    logger.info("Starting kafka stream...")
    stream.start()
  }

  def stop(): Unit = {
    logger.info("Stoping kafka stream...")
    // timeout to avoid deadlock
    stream.close(5, TimeUnit.SECONDS)
    cache.foreach(_.close())
    http.close()
  }

  implicit class ConfigAdapter(config: Config) {

    def toProperties: Properties = {
      val properties = new Properties()
      config.entrySet()
        .forEach(e =>
          properties.setProperty(e.getKey, config.getString(e.getKey))
        )
      properties
    }

  }

}
