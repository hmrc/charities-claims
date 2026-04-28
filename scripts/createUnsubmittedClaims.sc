#!/usr/bin/env -S scala-cli shebang --quiet

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
//> using file ../app/uk/gov/hmrc/charitiesclaims/models/Enumerable.scala
//> using file ../app/uk/gov/hmrc/charitiesclaims/models/UploadSummary.scala
//> using file ../app/uk/gov/hmrc/charitiesclaims/models/NameOfCharityRegulator.scala
//> using file ../app/uk/gov/hmrc/charitiesclaims/models/ReasonNotRegisteredWithRegulator.scala

import java.io.File
import sttp.client4.InputStreamBody
import sttp.client4.RequestOptions
import sttp.client4.StringBody
import sttp.client4.internal.SttpFile
import sttp.client4.FileBody
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
import play.api.libs.json.JsObject
import sttp.client4.MultipartBody
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.time.Instant

// This test assumes that:
// - CHARITIES_CLAIMS microservice is running on port 8031 
// - AUTH_LOGIN_STUB microservice is running on port 9949

println
printlnInfoMessage("This will create unsubmitted claims for the given claim file and user")
println

val claimFile: String = requiredScriptParameter('i',"input-claim-file")(args)
val userId: String = requiredScriptParameter('u',"user-id")(args)
val agentReference: String = requiredScriptParameter('r',"agent-reference")(args)
val numberOfClaims: Int = requiredScriptParameter('n',"number-of-claims")(args).toInt

val claimFilePath: os.Path =
  if claimFile.startsWith("/") then os.Path(claimFile)
  else os.pwd / os.RelPath(claimFile)

if(!os.exists(claimFilePath)) {
  printlnErrorMessage(s"Claim file $claimFilePath not found")
  sys.exit(1)
}

case class Authorization(bearerToken: String, sessionId: String)

def authenticateAgentUser(userId: String): Response[String] = 
  basicRequest  
  .response(asStringAlways)
  .post(uri"http://localhost:9949/auth-login-stub/gg-sign-in")
  .body(s"""authorityId=$userId&redirectionUrl=%2Fauth-login-stub%2Fsession&credentialStrength=strong&confidenceLevel=50&affinityGroup=Agent&usersName=&email=agent%40test.com&credentialRole=User&enrolment%5B0%5D.name=HMRC-CHAR-AGENT&enrolment%5B0%5D.taxIdentifier%5B0%5D.name=AGENTCHARID&enrolment%5B0%5D.taxIdentifier%5B0%5D.value=${agentReference}&enrolment%5B0%5D.state=Activated&enrolment%5B1%5D.name=&enrolment%5B1%5D.taxIdentifier%5B0%5D.name=&enrolment%5B1%5D.taxIdentifier%5B0%5D.value=&enrolment%5B1%5D.state=Activated&enrolment%5B2%5D.name=&enrolment%5B2%5D.taxIdentifier%5B0%5D.name=&enrolment%5B2%5D.taxIdentifier%5B0%5D.value=&enrolment%5B2%5D.state=Activated&enrolment%5B3%5D.name=&enrolment%5B3%5D.taxIdentifier%5B0%5D.name=&enrolment%5B3%5D.taxIdentifier%5B0%5D.value=&enrolment%5B3%5D.state=Activated""")
  .contentType("application/x-www-form-urlencoded")
  .followRedirects(false)
  .send("authenticate as agent user")

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

def getOrganisationName(hmrcCharitiesReference: String)(using authorization: Authorization): String = 
  basicRequest
  .response(asStringAlways)
  .headers(Map("Authorization" -> authorization.bearerToken, "X-Session-ID" -> authorization.sessionId))
  .get(uri"http://localhost:8031/charities-claims/charities/organisations/$hmrcCharitiesReference")
  .send(s"get organisation name for hmrc charities reference $hmrcCharitiesReference")
  .body
  .readPlayJsonAs[JsObject]
  .value("organisationName").as[String]

case class UploadRequest(href: String, fields: Map[String, String])

object ISODateTime {
  val formatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    .withZone(ZoneOffset.UTC)

  def timestampNow(): String = formatter.format(Instant.now())
}

extension (string: String) {  
    inline def readPlayJsonAs[T: Format]: T =
      Json.parse(string).as[T]
}

// TEST

val claimJson = Json.parse(os.read(claimFilePath))
println("Inout claim JSON:")
println(Json.prettyPrint(claimJson))

val claimTemplate: Claim = claimJson.as[Claim]

for(i <- 1 to numberOfClaims) {

  printlnInfoMessage(s"CREATING CLAIM NO. ${i}")

  given Authorization = 
    getSessionDetails {
      authenticateAgentUser(userId) 
    }.extractAuthorization

  val hmrcCharitysReference: String = (i.toString*10).take(10)
  val organisationName: String = getOrganisationName(hmrcCharitysReference)

  val claim = claimTemplate.copy(claimData = claimTemplate.claimData.copy(
    repaymentClaimDetails = claimTemplate.claimData.repaymentClaimDetails.copy(
      hmrcCharitiesReference = Some(hmrcCharitysReference),
      nameOfCharity = Some(organisationName)
    )
  ))

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
    understandFalseStatements = claim.claimData.understandFalseStatements,
    includedAnyAdjustmentsInClaimPrompt = claim.claimData.includedAnyAdjustmentsInClaimPrompt,
    adjustmentForOtherIncomePreviousOverClaimed = claim.claimData.adjustmentForOtherIncomePreviousOverClaimed,
    prevOverclaimedGiftAid = claim.claimData.prevOverclaimedGiftAid
    ))
}

printlnInfoMessage(s"ALL $numberOfClaims CLAIMS CREATED!")










