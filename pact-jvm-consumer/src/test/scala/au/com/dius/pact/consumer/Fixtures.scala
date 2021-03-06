package au.com.dius.pact.consumer

import au.com.dius.pact.model._

import scala.concurrent.{ExecutionContext, Future}
import au.com.dius.pact.model.dispatch.HttpClient
import java.util.concurrent.Executors

object Fixtures {
  import au.com.dius.pact.model.HttpMethod._
  import org.json4s.JsonDSL._
  val provider = Provider("test_provider")
  val consumer = Consumer("test_consumer")

  val request = Request(Post, "/",
    Map("testreqheader" -> "testreqheadervalue"),
    "test" -> true, null)

  val response = Response(200,
    Map("testreqheader" -> "testreqheaderval", "Access-Control-Allow-Origin" -> "*"),
    "responsetest" -> true, null)

  val interaction = Interaction(
    description = "test interaction",
    providerState = "test state",
    request = request,
    response = response
  )

  val interactions = List(interaction)

  val pact: Pact = Pact(
    provider = provider,
    consumer = consumer,
    interactions = interactions
  )

  case class ConsumerService(serverUrl: String) {
    implicit val executionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)

    private def extractFrom(body: String): Boolean = {
      import org.json4s._
      import org.json4s.jackson.JsonMethods._

      val result:List[Boolean] = for {
        JObject(child) <- parse(body)
        JField("responsetest", JBool(value)) <- child
      } yield value

      result.head
    }

    def extractResponseTest(path: String = request.path): Future[Boolean] = {
      HttpClient.run(request.copy(path = s"$serverUrl$path")).map { response =>
        response.status == 200 &&
        response.bodyString.map(extractFrom).get
      }
    }

    def simpleGet(path: String): Future[(Int, Option[String])] = {
      HttpClient.run(Request(Get, serverUrl + path, None, None, None)).map { response =>
        (response.status, response.bodyString)
      }
    }
  }
}
