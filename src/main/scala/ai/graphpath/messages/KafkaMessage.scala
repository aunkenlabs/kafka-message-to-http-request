package ai.graphpath.messages

import java.net.URL

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import play.api.libs.ws.{StandaloneWSClient, StandaloneWSRequest}

case class KafkaMessage(id: Option[String],
                        method: Option[String],
                        url: URL,
                        headers: Option[Map[String, String]],
                        body: Option[JsonNode]) {
  def buildRequest(ws: StandaloneWSClient)(implicit mapper: ObjectMapper): StandaloneWSRequest = {
    ws.url(url.toURI.toASCIIString)
      .withMethod(method.getOrElse("POST"))
      .withHttpHeaders(
        headers.map(_.toSeq).getOrElse(Seq.empty): _*
      )
      .withBody(body)
  }
}