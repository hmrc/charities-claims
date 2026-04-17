#!/usr/bin/env -S scala shebang --quiet

//> using scala 3.7.4
//> using toolkit 0.7.0 
//> using dep org.encalmo::sttp-utils:0.9.4
import scala.collection.mutable.Buffer

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
// - rds-datacache-proxy microservice is running on port 6992
// - auth-login-stub microservice is running on port 9949
// - prh-oracle-xe/databases/charities-db is running on port 1521

println
printlnInfoMessage("This manual test verifies the functionality of the rds datacache proxy service.")
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

def getOrganisationName(charityReference: String)(using authorization: Authorization): String = 
  basicRequest
  .response(asStringAlways)
  .headers(Map("Authorization" -> authorization.bearerToken, "X-Session-ID" -> authorization.sessionId))
  .get(uri"http://localhost:8031/charities-claims/charities/organisations/$charityReference")
  .send(s"get organisation name for charity reference $charityReference")
  .body
  .readAs[ujson.Obj]
  .value("organisationName").str

// TEST

val charityPairs: List[(String, String)] = List(
  ("XR4010", "CHARITY TC088"),
  ("CF00020", "Organisation Name TC012a"),
  ("EE00014", "Charity 96"),
  ("IT00014", "Organization123"),
  ("00013", "Incorrect Charity Ref"),
  ("AA00016", "SHOULD PASS organizationName"),
  ("CH06051", "TEST OFFICIAL"),
  ("BE00047", "SCENARIO2"),
  ("AT00088", "SCENARIO3"),
  ("BE00049", "SCENARIO6"),
  ("CH00038", "Organisation Name TC10"),
  ("CH00035", "Organisation Name IPS CASC TC013"),
  ("ST00003", "Organization TC008 ISIT Run 2"),
  ("IS00003", "Organization TC006 ISIT Run 2"),
  ("CH00037", "Organisation Name TC9"),
  ("IT00071", "ISIT TC03 RUN2"),
  ("FR00002", "Organization TC005 ISIT RUN2"),
  ("CF00017", "Organisation Name TC12"),
  ("IT00073", "Organization TC04 ISIT Run 2"),
  ("CH00036", "Organisation Name TC11"),
  ("IT00072", "TC05 ISIT Duplicate"),
  ("FR00003", "Organization TC007 ISIT Run 2"),
  ("IT00066", "Variation for ISIT"),
  ("BE00050", "SCENARIO7"),
  ("BE00048", "SCENARIO04"),
  ("IT00054", "cccccccccccc"),
  ("IT00062", "Organization ISIT TC010"),
  ("ST12344", "Charity 15 D473"),
  ("CH00029", "Test Reg"),
  ("EE00009", "Charity 72"),
  ("EE00010", "Charity 73"),
  ("CS1004", "TEST 1004"),
  ("AT00030", "testorg"),
  ("IT00060", "EEEEEEE UUUUUU"),
  ("EE00008", "Charity 71"),
  ("IT00063", "sensi.test"),
  ("BE00018", "Charity 70"),
  ("AT00002", "sdfsdfsdf"),
  ("BG00007", "IMS CASE FIFTY SEVEN"),
  ("EE00011", "Charity 75"),
  ("CH00040", "CASC"),
  ("EE00013", "Trains for all Charity"),
  ("BE00022", "Charity 91"),
  ("IT00075", "Charities REG Decision Service ISIT7"),
  ("CF00018", "CASC 91"),
  ("IT00076", "ISIT ORG - TC008"),
  ("CH00039", "ISIT-TC011"),
  ("IT00074", "Charities REG Decision Service ISIT4"),
  ("IT00081", "Organisation Name TC005b"),
  ("CH7", "CHARITY TC086"),
  ("CH12346", "Charity 12 D473"),
  ("IT00080", "ISIT RUN2 TC008a"),
  ("CH00001", "Charity 18 D473"),
  ("IT00078", "Charities REG DecisionService ISIT5"),
  ("IT00082", "Organisation Name TC004a"),
  ("CH11061", "TEST OFFICIAL"),
  ("CH11062", "TEST OFFICIAL"),
  ("PA01106", "TEST OFFICIAL"),
  ("CH11063", "TEST OFFICIAL"),
  ("AP00016", "Pen Tester Agent002"),
  ("AB11111", "Organisation1"),
  ("IT00028", "E-Portal MD001"),
  ("IT00029", "EPortal MD004"),
  ("IT00030", "EPortal MD002"),
  ("IT00031", "EPortal MD005"),
  ("CH00022", "Eportal CASC MD006"),
  ("CF00006", "New Name Charity"),
  ("IT00032", "Eportal MD008"),
  ("AP00015", "Pen Tester Agent001"),
  ("EE00005", "Charity 54"),
  ("BE00008", "Charity 55"),
  ("BE00009", "Charity 56"),
  ("CY00012", "D254 220213"),
  ("CF00008", "CASC 57"),
  ("AT00017", "Charity 58 - Non UK"),
  ("CF00009", "THE BIG CASC COMMUNITY AMATEUR SPORTS CLUB FOR ALL AGES AND ABILITIES "),
  ("BG00002", "Charity 60"),
  ("EE00006", "Charity 61"),
  ("AT00018", "Charity 62"),
  ("IT00041", "aaaaaaaa"),
  ("CF00011", "Test"),
  ("IT00042", "aaaaaa"),
  ("CF00012", "test"),
  ("CZ00002", "ISIT TESTING 01"),
  ("IT00043", "ISIT TESTING 02"),
  ("ZN00001", "ISIT TESTING 03"),
  ("CF00013", "ISIT TESTING 04"),
  ("X123456", "Just Donate"),
  ("CH00025", "Durrington-on-Sea The Community AmateurSports Club Scheme (CASC)"),
  ("CY00013", "Green Recycling Charity"),
  ("IT00025", "TestOrg123"),
  ("IT00040", "Organisation Name 3"),
  ("IT00035", "CHAR TC001"),
  ("RO00001", "Organisation Name8"),
  ("CH00023", "CHAR TC002"),
  ("AT00015", "Sven Goran Erikson100"),
  ("AT00016", "CHAR TEST 22"),
  ("IT00053", "ddddddddddddddddd"),
  ("CY00020", "TC002-CASC-280213"),
  ("AT00005", "AJILON PAYROLL SERVICES LIMITED")
)

given Authorization = 
    getSessionDetails(authenticateOrganisationUser("user-123"))
    .extractAuthorization

val failedTests: Buffer[String] = Buffer()

def assertEquals(actual: String, expected: String) = 
  if (actual != expected) {
    printlnErrorMessage(s"Expected $expected but got $actual")
    failedTests.append(s"Expected $expected but got $actual")
  }

for ((charityReference, expectedOrganisationName) <- charityPairs) {
  val organisationName = getOrganisationName(charityReference)
  assertEquals(organisationName, expectedOrganisationName)
}

if(failedTests.isEmpty) 
then printlnInfoMessage(s"ALL ${charityPairs.size} TESTS PASSED!")
else printlnErrorMessage(s"FAILED TESTS: ${failedTests.mkString("\n")}")










