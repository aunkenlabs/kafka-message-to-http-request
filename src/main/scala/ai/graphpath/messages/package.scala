package ai.graphpath

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import play.api.libs.ws._

package object messages {

  import DefaultBodyWritables._

  implicit def writableOfOption[A: BodyWritable]: BodyWritable[Option[A]] = {
    val aWritable = implicitly[BodyWritable[A]]
    BodyWritable(
      _.map(aWritable.transform).getOrElse(EmptyBody),
      aWritable.contentType
    )
  }

  implicit def writableOfJsonNode(implicit mapper: ObjectMapper): BodyWritable[JsonNode] =
    BodyWritable(json => implicitly[BodyWritable[Array[Byte]]]
      .transform(mapper.writeValueAsBytes(json)),
      "application/json")

}
