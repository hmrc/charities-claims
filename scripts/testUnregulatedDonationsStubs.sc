#!/usr/bin/env -S scala shebang --quiet

//> using scala 3.7.4
//> using toolkit 0.7.0 
//> using dep org.encalmo::sttp-utils:0.9.4

import scala.util.Random
import sttp.model.*
import sttp.client4.quick.*
import sttp.client4.Response
import org.encalmo.utils.RequestUtils.*
import org.encalmo.utils.JsonUtils.*
import org.encalmo.utils.ConsoleUtils.*
import upickle.default.*

// This test assumes that:
// - charities-claims microservice is running on port 8031 
// - formp-proxy microservice is running on port 8034
// - auth-login-stub microservice is running on port 9949
// - prh-oracle-xe/databases/charities-db is running on port 1521

println
printlnInfoMessage("This manual test verifies the functionality of the unregulated donations feature.")
println

case class Authorization(bearerToken: String, sessionId: String)
case class TotalUnregulatedDonations(unregulatedDonationsTotal: Double) derives ReadWriter

def authenticateOrganisationUser(userId: String): Response[String] = 
  basicRequest  
  .response(asStringAlways)
  .post(uri"http://localhost:9949/auth-login-stub/gg-sign-in")
  .body(s"""authorityId=$userId&redirectionUrl=%2Fauth-login-stub%2Fsession&credentialStrength=strong&confidenceLevel=50&affinityGroup=Organisation&usersName=&email=user%40test.com&credentialRole=User&enrolment%5B0%5D.name=HMRC-CHAR-ORG&enrolment%5B0%5D.taxIdentifier%5B0%5D.name=CHARID&enrolment%5B0%5D.taxIdentifier%5B0%5D.value=org-123&enrolment%5B0%5D.state=Activated&enrolment%5B1%5D.name=&enrolment%5B1%5D.taxIdentifier%5B0%5D.name=&enrolment%5B1%5D.taxIdentifier%5B0%5D.value=&enrolment%5B1%5D.state=Activated&enrolment%5B2%5D.name=&enrolment%5B2%5D.taxIdentifier%5B0%5D.name=&enrolment%5B2%5D.taxIdentifier%5B0%5D.value=&enrolment%5B2%5D.state=Activated&enrolment%5B3%5D.name=&enrolment%5B3%5D.taxIdentifier%5B0%5D.name=&enrolment%5B3%5D.taxIdentifier%5B0%5D.value=&enrolment%5B3%5D.state=Activated""")
  .contentType("application/x-www-form-urlencoded")
  .followRedirects(false)
  .send("authenticate as organisation user")

def getSessionDetails(authenticateResponse: Response[String]) = 
  basicRequest
  .response(asStringAlways)
  .cookies(authenticateResponse)
  .get(uri"http://localhost:9949/auth-login-stub/session")
  .send("get session details")

extension (response: Response[String])
  def extractAuthorization: Authorization = 
    val body = response.body
    val bearerTokenStartIndex = body.indexOf("Bearer")
    val bearerTokenEndIndex = body.indexOf("</code>", bearerTokenStartIndex)
    val bearerToken = body.substring(bearerTokenStartIndex, bearerTokenEndIndex)
    val sessionIdStartIndex = body.indexOf(">session-") + 1
    val sessionIdEndIndex = body.indexOf("</code>", sessionIdStartIndex)
    val sessionId = body.substring(sessionIdStartIndex, sessionIdEndIndex)
    Authorization(bearerToken, sessionId)

def saveUnregulatedDonationRequest(charityReference: String, claimId: Int, amount: BigDecimal)(using authorization: Authorization) = 
  basicRequest
  .response(asStringAlways)
  .headers(Map("Authorization" -> authorization.bearerToken, "X-Session-ID" -> authorization.sessionId))
  .post(uri"http://localhost:8034/formp-proxy/charities/$charityReference/unregulated-donations")
  .body(s"""{"claimId": $claimId, "amount": $amount}""")
  .contentType("application/json")
  .send(s"save unregulated donation for charity reference $charityReference")

def getTotalUnregulatedDonations(charityReference: String)(using authorization: Authorization): TotalUnregulatedDonations = 
  basicRequest
  .response(asStringAlways)
  .headers(Map("Authorization" -> authorization.bearerToken, "X-Session-ID" -> authorization.sessionId))
  .get(uri"http://localhost:8031/charities-claims/charities/$charityReference/unregulated-donations")
  .send(s"get total unregulated donations for charity reference $charityReference")
  .body
  .readAs[TotalUnregulatedDonations]

// TEST

val charityReference = s"charity-${Random.nextInt(Int.MaxValue)}"

locally {
  given Authorization = 
    getSessionDetails(authenticateOrganisationUser("user-123"))
    .extractAuthorization

  saveUnregulatedDonationRequest(charityReference, 1, 100.00)
  val total1 = getTotalUnregulatedDonations(charityReference)
  assert(total1.unregulatedDonationsTotal == 100.00)

  saveUnregulatedDonationRequest(charityReference, 2, 50.00)
  val total2 = getTotalUnregulatedDonations(charityReference)
  assert(total2.unregulatedDonationsTotal == 150.00)

  saveUnregulatedDonationRequest(charityReference, 3, 25.00)
  val total3 = getTotalUnregulatedDonations(charityReference)
  assert(total3.unregulatedDonationsTotal == 175.00)
}

locally {
  given Authorization = 
    getSessionDetails(authenticateOrganisationUser("user-456"))
    .extractAuthorization
    
  saveUnregulatedDonationRequest(charityReference, 4, 10.00)
  val total1 = getTotalUnregulatedDonations(charityReference)
  assert(total1.unregulatedDonationsTotal == 185.00)

  saveUnregulatedDonationRequest(charityReference, 5, 20.00)
  val total2 = getTotalUnregulatedDonations(charityReference)
  assert(total2.unregulatedDonationsTotal == 205.00)

  saveUnregulatedDonationRequest(charityReference, 6, 30.00)
  val total3 = getTotalUnregulatedDonations(charityReference)
  assert(total3.unregulatedDonationsTotal == 235.00)
}

printlnInfoMessage("ALL TESTS PASSED!")










