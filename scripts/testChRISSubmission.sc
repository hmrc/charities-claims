#!/usr/bin/env -S scala shebang --quiet

//> using scala 3.7.4
//> using toolkit 0.7.0 
//> using dep org.encalmo::sttp-utils:0.9.4
//> using dep org.playframework::play-json:3.0.6
//> using file ../app/uk/gov/hmrc/charitiesclaims/models/SaveClaimRequest.scala
//> using file ../app/uk/gov/hmrc/charitiesclaims/models/SaveClaimResponse.scala
//> using file ../app/uk/gov/hmrc/charitiesclaims/models/UpdateClaimRequest.scala
//> using file ../app/uk/gov/hmrc/charitiesclaims/models/UpdateClaimResponse.scala
//> using file ../app/uk/gov/hmrc/charitiesclaims/models/ChRISSubmissionRequest.scala
//> using file ../app/uk/gov/hmrc/charitiesclaims/models/ChRISSubmissionResponse.scala
//> using file ../app/uk/gov/hmrc/charitiesclaims/models/Claim.scala

import scala.util.Random
import sttp.model.*
import sttp.client4.quick.*
import sttp.client4.Response
import org.encalmo.utils.RequestUtils.*
import org.encalmo.utils.JsonUtils.*
import org.encalmo.utils.ConsoleUtils.*
import org.encalmo.utils.CommandLineUtils.*
import upickle.default.*
import uk.gov.hmrc.charitiesclaims.models.*
import play.api.libs.json.Json
import play.api.libs.json.Format

// This test assumes that:
// - CHARITIES_CLAIMS microservice is running on port 8031 
// - AUTH_LOGIN_STUB microservice is running on port 9949
// - PORTAL_CHRIS_STUB microservice is running on port 9712

println
printlnInfoMessage("This manual test verifies the functionality of the ChRIS submission feature.")
println

val claimFile: String = requiredScriptParameter('i',"input-claim-file")(args)

val claimFilePath: os.Path = os.pwd / claimFile.split("/")

if(!os.exists(claimFilePath)) {
  printlnErrorMessage(s"Claim file $claimFilePath not found")
  sys.exit(1)
}

case class Authorization(bearerToken: String, sessionId: String)

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

def createClaim(request: SaveClaimRequest)(using authorization: Authorization) = 
  basicRequest
  .response(asStringAlways)
  .headers(Map("Authorization" -> authorization.bearerToken, "X-Session-ID" -> authorization.sessionId))
  .post(uri"http://localhost:8031/charities-claims/claims")
  .body(Json.toJson(request).toString)
  .contentType("application/json")
  .send(s"create a claim")
  .body
  .readPlayJsonAs[SaveClaimResponse]


def updateClaim(claimId: String, request: UpdateClaimRequest)(using authorization: Authorization) = 
  basicRequest
  .response(asStringAlways)
  .headers(Map("Authorization" -> authorization.bearerToken, "X-Session-ID" -> authorization.sessionId))
  .put(uri"http://localhost:8031/charities-claims/claims/${claimId}")
  .body(Json.toJson(request).toString)
  .contentType("application/json")
  .send(s"update a claim")
  .body
  .readPlayJsonAs[UpdateClaimResponse]

def submitClaim(request: ChRISSubmissionRequest)(using authorization: Authorization) = 
  basicRequest
  .response(asStringAlways)
  .headers(Map("Authorization" -> authorization.bearerToken, "X-Session-ID" -> authorization.sessionId))
  .post(uri"http://localhost:8031/charities-claims/chris")
  .body(Json.toJson(request).toString)
  .contentType("application/json")
  .send(s"submit a claim to ChRIS")
  .body
  .readPlayJsonAs[ChRISSubmissionResponse]

extension (string: String) {  
    inline def readPlayJsonAs[T: Format]: T =
      Json.parse(string).as[T]
}

// TEST

val userId = s"user-${Random.nextInt(Int.MaxValue)}"

val claimJson = Json.parse(os.read(claimFilePath))
println("Inout claim JSON:")
println(Json.prettyPrint(claimJson))

val claim: Claim = claimJson.as[Claim]

given Authorization = 
  getSessionDetails(authenticateOrganisationUser(userId))
  .extractAuthorization

val saveClaimResponse: SaveClaimResponse = 
  createClaim(SaveClaimRequest(
  claimingGiftAid = claim.claimData.repaymentClaimDetails.claimingGiftAid, 
  claimingTaxDeducted = claim.claimData.repaymentClaimDetails.claimingTaxDeducted, 
  claimingUnderGiftAidSmallDonationsScheme = claim.claimData.repaymentClaimDetails.claimingUnderGiftAidSmallDonationsScheme, 
  claimReferenceNumber = claim.claimData.repaymentClaimDetails.claimReferenceNumber,
  claimingDonationsNotFromCommunityBuilding = claim.claimData.repaymentClaimDetails.claimingDonationsNotFromCommunityBuilding,
  claimingDonationsCollectedInCommunityBuildings = claim.claimData.repaymentClaimDetails.claimingDonationsCollectedInCommunityBuildings,
  connectedToAnyOtherCharities = claim.claimData.repaymentClaimDetails.connectedToAnyOtherCharities,
  makingAdjustmentToPreviousClaim = claim.claimData.repaymentClaimDetails.makingAdjustmentToPreviousClaim
  ))

val updateClaimResponse: UpdateClaimResponse = 
  updateClaim(saveClaimResponse.claimId, UpdateClaimRequest(
  lastUpdatedReference = saveClaimResponse.lastUpdatedReference,
  repaymentClaimDetails = claim.claimData.repaymentClaimDetails,
  organisationDetails = claim.claimData.organisationDetails,
  giftAidSmallDonationsSchemeDonationDetails = claim.claimData.giftAidSmallDonationsSchemeDonationDetails,
  declarationDetails = claim.claimData.declarationDetails
  ))

submitClaim(ChRISSubmissionRequest(
  claimId = saveClaimResponse.claimId,
  lastUpdateReference = updateClaimResponse.lastUpdatedReference
))


printlnInfoMessage("ALL TESTS PASSED!")










