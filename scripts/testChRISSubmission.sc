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
import sttp.client4.MultipartBody
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.time.Instant

// This test assumes that:
// - CHARITIES_CLAIMS microservice is running on port 8031 
// - AUTH_LOGIN_STUB microservice is running on port 9949
// - PORTAL_CHRIS_STUB microservice is running on port 9712

println
printlnInfoMessage("This manual test verifies the functionality of the ChRIS submission feature.")
println

val claimFile: String = requiredScriptParameter('i',"input-claim-file")(args)

val isAgentUser: Boolean = optionalScriptFlag('a',"agent")(args)

val claimFilePath: os.Path =
  if claimFile.startsWith("/") then os.Path(claimFile)
  else os.pwd / os.RelPath(claimFile)

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

def authenticateAgentUser(userId: String): Response[String] = 
  basicRequest  
  .response(asStringAlways)
  .post(uri"http://localhost:9949/auth-login-stub/gg-sign-in")
  .body(s"""authorityId=$userId&redirectionUrl=%2Fauth-login-stub%2Fsession&credentialStrength=strong&confidenceLevel=50&affinityGroup=Agent&usersName=&email=agent%40test.com&credentialRole=User&enrolment%5B0%5D.name=HMRC-CHAR-AGENT&enrolment%5B0%5D.taxIdentifier%5B0%5D.name=AGENTCHARID&enrolment%5B0%5D.taxIdentifier%5B0%5D.value=agent-123&enrolment%5B0%5D.state=Activated&enrolment%5B1%5D.name=&enrolment%5B1%5D.taxIdentifier%5B0%5D.name=&enrolment%5B1%5D.taxIdentifier%5B0%5D.value=&enrolment%5B1%5D.state=Activated&enrolment%5B2%5D.name=&enrolment%5B2%5D.taxIdentifier%5B0%5D.name=&enrolment%5B2%5D.taxIdentifier%5B0%5D.value=&enrolment%5B2%5D.state=Activated&enrolment%5B3%5D.name=&enrolment%5B3%5D.taxIdentifier%5B0%5D.name=&enrolment%5B3%5D.taxIdentifier%5B0%5D.value=&enrolment%5B3%5D.state=Activated""")
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

case class UploadRequest(href: String, fields: Map[String, String])

object UploadRequest {
  given Format[UploadRequest] = Json.format[UploadRequest]
}

case class UpscanInitiateResponse(
  reference: String,
  uploadRequest: UploadRequest
)

object UpscanInitiateResponse {
  given Format[UpscanInitiateResponse] = Json.format[UpscanInitiateResponse]
}

def initiateUpscanUpload(claimId: String) = 
  basicRequest
  .response(asStringAlways)
  .post(uri"http://localhost:9570/upscan/v2/initiate")
  .body(s"""|{
           |  "successRedirect" : "http://localhost:8030/charities-claims/upload-gift-aid-schedule/success",
           |  "errorRedirect" : "http://localhost:8030/charities-claims/upload-gift-aid-schedule/error",
           |  "callbackUrl" : "http://localhost:8032/charities-claims-validation/${claimId}/upscan-callback",
           |  "minimumFileSize" : 1,
           |  "maximumFileSize" : 256000,
           |  "consumingService" : "charities-claims-frontend"
           |}""".stripMargin)
  .contentType("application/json")
  .send(s"initiate an upscan upload")
  .body
  .readPlayJsonAs[UpscanInitiateResponse]

final case class CreateUploadTrackingRequest(
  reference: String,
  validationType: String,
  uploadUrl: String,
  initiateTimestamp: String,
  fields: Map[String, String]
)

object CreateUploadTrackingRequest {
  given Format[CreateUploadTrackingRequest] = Json.format[CreateUploadTrackingRequest]
}

object ISODateTime {
  val formatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    .withZone(ZoneOffset.UTC)

  def timestampNow(): String = formatter.format(Instant.now())
}

def createUploadTracking(claimId: String, request: CreateUploadTrackingRequest)(using authorization: Authorization) = 
  basicRequest
  .response(asStringAlways)
  .headers(Map("Authorization" -> authorization.bearerToken, "X-Session-ID" -> authorization.sessionId))
  .post(uri"http://localhost:8032/charities-claims-validation/${claimId}/create-upload-tracking")
  .body(Json.toJson(request).toString)
  .contentType("application/json")
  .send(s"create an upload tracking")
  .body

def uploadFile(url: String, reference: String, fields: Map[String, String], path: os.Path)(using authorization: Authorization) = 
  val absolutePath = new File(path.toString).toPath
  basicRequest
  .response(asStringAlways) 
  .post(uri"$url")
  .multipartBody(
    fields.toSeq.map((key, value) => Part(name = key, body = StringBody(value, encoding = "UTF-8")))
    :+ multipartFile(name = "file", data = absolutePath)
  )
  .followRedirects(false)
  .send(s"upload a file")
  .body

def uploadScheduleFile(scheduleFile: String, validationType: String): FileUploadReference = {
  val scheduleFilePath: os.Path =
        if scheduleFile.startsWith("/") then os.Path(scheduleFile)
        else os.pwd / os.RelPath(scheduleFile)

  if(!os.exists(scheduleFilePath)) {
    printlnErrorMessage(s"Schedule file $scheduleFilePath not found")
    sys.exit(1)
  }
  val upscanInitiateResponse = initiateUpscanUpload(saveClaimResponse.claimId)
  createUploadTracking(saveClaimResponse.claimId, CreateUploadTrackingRequest(
    reference = upscanInitiateResponse.reference,
    validationType = validationType,
    uploadUrl = upscanInitiateResponse.uploadRequest.href,
    initiateTimestamp = ISODateTime.timestampNow(),
    fields = upscanInitiateResponse.uploadRequest.fields
  ))
  uploadFile(
    url = upscanInitiateResponse.uploadRequest.href, 
    reference = upscanInitiateResponse.reference, 
    fields = upscanInitiateResponse.uploadRequest.fields, 
    path = scheduleFilePath
  )
  FileUploadReference(upscanInitiateResponse.reference)
}

def getUploadSummary(claimId: String)(using authorization: Authorization) = 
  basicRequest
  .response(asStringAlways)
  .headers(Map("Authorization" -> authorization.bearerToken, "X-Session-ID" -> authorization.sessionId))
  .get(uri"http://localhost:8032/charities-claims-validation/${claimId}/upload-results")
  .send(s"get upload summary")
  .body

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
  getSessionDetails {
    if(isAgentUser) 
    then authenticateAgentUser(userId) 
    else authenticateOrganisationUser(userId)
  }.extractAuthorization

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

val giftAidScheduleFileUploadReference = 
  if claim.claimData.repaymentClaimDetails.claimingGiftAid 
  then
    optionalScriptParameter('g',"gift-aid-schedule")(args)
    .map { scheduleFile => uploadScheduleFile(scheduleFile, "GiftAid") }
  else None

val otherIncomeScheduleFileUploadReference = 
  if claim.claimData.repaymentClaimDetails.claimingTaxDeducted 
  then
    optionalScriptParameter('o',"other-income-schedule")(args)
    .map { scheduleFile => uploadScheduleFile(scheduleFile, "OtherIncome") }
  else None

val communityBuildingsScheduleFileUploadReference = 
  if claim.claimData.repaymentClaimDetails.claimingDonationsCollectedInCommunityBuildings.contains(true) 
  then
    optionalScriptParameter('c',"community-buildings-schedule")(args)
    .map { scheduleFile => uploadScheduleFile(scheduleFile, "CommunityBuildings") }
  else None

val connectedCharitiesScheduleFileUploadReference = 
  if claim.claimData.repaymentClaimDetails.connectedToAnyOtherCharities.contains(true)
  then
    optionalScriptParameter('c',"connected-charities-schedule")(args)
    .map { scheduleFile => uploadScheduleFile(scheduleFile, "ConnectedCharities") }
  else None

Thread.sleep(10000)
getUploadSummary(saveClaimResponse.claimId)

val updateClaimResponse: UpdateClaimResponse = 
  updateClaim(saveClaimResponse.claimId, UpdateClaimRequest(
  lastUpdatedReference = saveClaimResponse.lastUpdatedReference,
  repaymentClaimDetails = claim.claimData.repaymentClaimDetails,
  organisationDetails = claim.claimData.organisationDetails,
  giftAidSmallDonationsSchemeDonationDetails = claim.claimData.giftAidSmallDonationsSchemeDonationDetails,
  understandFalseStatements = claim.claimData.understandFalseStatements,
  includedAnyAdjustmentsInClaimPrompt = claim.claimData.includedAnyAdjustmentsInClaimPrompt,
  giftAidScheduleFileUploadReference = giftAidScheduleFileUploadReference,
  otherIncomeScheduleFileUploadReference = otherIncomeScheduleFileUploadReference,
  communityBuildingsScheduleFileUploadReference = communityBuildingsScheduleFileUploadReference,
  connectedCharitiesScheduleFileUploadReference = connectedCharitiesScheduleFileUploadReference,
  adjustmentForOtherIncomePreviousOverClaimed = claim.claimData.adjustmentForOtherIncomePreviousOverClaimed,
  prevOverclaimedGiftAid = claim.claimData.prevOverclaimedGiftAid
  ))

submitClaim(ChRISSubmissionRequest(
  claimId = saveClaimResponse.claimId,
  lastUpdatedReference = updateClaimResponse.lastUpdatedReference,
  declarationLanguage = "en"
))


printlnInfoMessage("ALL TESTS PASSED!")










