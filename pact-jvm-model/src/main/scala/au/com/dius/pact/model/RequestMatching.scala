package au.com.dius.pact.model

import JsonDiff.DiffConfig
import org.json4s.JsonAST.JValue

case class RequestMatching(expectedInteractions: Seq[Interaction]) {
  import RequestMatching._
      
  def matchInteraction(actual: Request): RequestMatch = {
    def compareToActual(expected: Interaction) = compareRequest(expected, actual) 
    val matches = expectedInteractions.map(compareToActual)
    matches.reduceLeft(_ merge _)
  }
      
  def findResponse(actual: Request): Option[Response] = 
    matchInteraction(actual).toOption.map(_.response)
}

object RequestMatching {
  import Matching._

  var diffConfig = DiffConfig(allowUnexpectedKeys = false, structural = false)

  implicit def liftPactForMatching(pact: Pact): RequestMatching = RequestMatching(pact.interactions)
                     
  def isPartialMatch(problems: Seq[RequestPartMismatch]): Boolean = !problems.exists {
    case PathMismatch(_,_) | MethodMismatch(_,_) => true
    case _ => false
  } 
    
  def decideRequestMatch(expected: Interaction, problems: Seq[RequestPartMismatch]): RequestMatch = 
    if (problems.isEmpty) FullRequestMatch(expected)
    else if (isPartialMatch(problems)) PartialRequestMatch(expected, problems) 
    else RequestMismatch
    
  def compareRequest(expected: Interaction, actual: Request): RequestMatch = {
    decideRequestMatch(expected, requestMismatches(expected.request, actual))
  }
                                              
  def requestMismatches(expected: Request, actual: Request): Seq[RequestPartMismatch] = {
    (matchMethod(expected.method, actual.method) 
      ++ matchPath(expected.path, actual.path)
      ++ matchCookie(expected.cookie, actual.cookie)
      ++ matchHeaders(expected.headersWithoutCookie, actual.headersWithoutCookie)
      ++ matchBody(expected.body, actual.body, diffConfig)).toSeq
  }
}

