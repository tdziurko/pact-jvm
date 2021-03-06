package au.com.dius.pact.model

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scala.collection.JavaConversions
import org.json.JSONObject

object Pact {
  def apply(provider: Provider, consumer: Consumer, interactions: java.util.List[Interaction]): Pact = {
    Pact(provider, consumer, JavaConversions.collectionAsScalaIterable(interactions).toSeq)
  }

  def from(source: JsonInput): Pact = {
    from(parse(source))
  }

  def from(json:JValue) = {
    implicit val formats = DefaultFormats
    json.transformField { case ("provider_state", value) => ("providerState", value)}.extract[Pact]
  }

  trait MergeResult

  case class MergeSuccess(result: Pact) extends MergeResult
  case class MergeConflict(result: Seq[(Interaction, Interaction)]) extends MergeResult
}

@deprecated("Use PactFragment where possible, same functionality but more appropriate language.  The serialized file is the true pact")
case class Pact(provider: Provider, consumer: Consumer, interactions: Seq[Interaction]) extends PactSerializer {
  import Pact._
  
  def merge(other: Pact): MergeResult = {
    val failures: Seq[(Interaction, Interaction)] = for {
      a <- interactions
      b <- other.interactions
      if a conflictsWith b
    } yield (a, b)

    if(failures.isEmpty) {
      val mergedInteractions = interactions ++ other.interactions.filterNot(interactions.contains)
      MergeSuccess(Pact(provider, consumer, mergedInteractions))
    } else {
      MergeConflict(failures)
    }
  }
  
  def sortInteractions: Pact = 
    copy(interactions = interactions.sortBy(i => s"${i.providerState}${i.description}"))
  
  def interactionFor(description:String, providerState:String) = interactions.find { i =>
    i.description == description && i.providerState == providerState
  }
}

case class Provider(name:String)

case class Consumer(name:String)

case class Interaction(description: String,
                       providerState: String, //TODO: state should be Optional
                       request: Request,
                       response: Response) {
  
  override def toString: String = {
    s"Interaction: $description\n\tin state $providerState\nrequest:\n$request\n\nresponse:\n$response"
  }
  
  def conflictsWith(other: Interaction): Boolean = {
    description == other.description &&
    providerState == other.providerState &&
    (request != other.request || response != other.response)
  }
}

object HttpMethod {
  val Get    = "GET"
  val Post   = "POST"
  val Put    = "PUT"
  val Delete = "DELETE"
  val Head   = "HEAD"
  val Patch  = "PATCH"
}

//TODO: support duplicate headers
case class Request(method: String,
                   path: String,
                   headers: Option[Map[String, String]],
                   body: Option[JValue],
                   matchers: Option[JSONObject]) {
  def bodyString:Option[String] = body.map{ b => compact(render(b))}

  def cookie: Option[List[String]] = cookieHeader.map(_._2.split(";").map(_.trim).toList)

  def headersWithoutCookie: Option[Map[String, String]] = cookieHeader match {
    case Some(cookie) => headers.map(_ - cookie._1)
    case _ => headers
  }

  private def cookieHeader = findHeaderByCaseInsensitiveKey("cookie")

  private def findHeaderByCaseInsensitiveKey(key: String): Option[(String, String)] = headers.flatMap(_.find(_._1.toLowerCase == key.toLowerCase))

  override def toString: String = {
    s"\tmethod: $method\n\tpath: $path\n\theaders: $headers\n\trequestMatchers: $matchers\n\tbody:\n${body.map{b => pretty(render(b))}}"
  }
}

trait Optionals {
  def optional(headers: Map[String, String]): Option[Map[String,String]] = {
    if(headers == null | headers.isEmpty) {
      None
    } else {
      Some(headers)
    }
  }

  def optional(body: String): Option[JValue] = {
    if(body == null || body.trim().size == 0) {
      None
    } else {
      Some(parse(body))
    }
  }

  def optional(body: JValue): Option[JValue] = {
    if(body == null) {
      None
    } else {
      Some(body)
    }
  }

  def optional(matchers: JSONObject): Option[JSONObject] = {
    if(matchers == null) {
      None
    } else {
      Some(matchers)
    }
  }
}

object Request extends Optionals {
  def apply(method: String, path: String, headers: Map[String, String], body: String, matchers: JSONObject): Request = {
    Request(method, path, optional(headers), optional(body), optional(matchers))
  }

  def apply(method: String, path: String, headers: Map[String, String], body: JValue, matchers: JSONObject): Request = {
    Request(method, path, optional(headers), optional(body), optional(matchers))
  }

  def apply(method: String, path: String, headers: java.util.Map[String,String], body: String, matchers: JSONObject): Request = {
    Request(method, path, optional(JavaConversions.mapAsScalaMap(headers).toMap), optional(body), optional(matchers))
  }
}

//TODO: support duplicate headers
case class Response(status: Int,
                    headers: Option[Map[String, String]],
                    body: Option[JValue], matchers: Option[JSONObject]) {
  def bodyString:Option[String] = body.map{ b => compact(render(b)) }

  override def toString: String = {
    s"\tstatus: $status \n\theaders: $headers \n\tmatchers: $matchers \n\tbody: \n${body.map{b => pretty(render(b))}}"
  }
}

object Response extends Optionals {

  val CrossSiteHeaders = Map[String, String]("Access-Control-Allow-Origin" -> "*")

  def apply(status: Int, headers: Map[String, String], body: String, matchers: JSONObject): Response = {
    Response(status, optional(headers), optional(body), optional(matchers))
  }

  def apply(status: Int, headers: Map[String, String], body: JValue, matchers: JSONObject): Response = {
    Response(status, optional(headers), optional(body), optional(matchers))
  }

  def apply(status: Int, headers: java.util.Map[String, String], body: String, matchers: JSONObject): Response = {
    Response(status, optional(JavaConversions.mapAsScalaMap(headers).toMap), optional(body), optional(matchers))
  }

  def invalidRequest(request: Request) = {
    Response(500, CrossSiteHeaders, "error"-> s"Unexpected request : $request", null)
  }
}

