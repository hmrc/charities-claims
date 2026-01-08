/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.charitiesclaims.controllers

import org.scalamock.matchers.ArgCapture.CaptureOne
import play.api.http.Status
import play.api.libs.json.{Format, JsObject, JsString, Json, Writes}
import play.api.test.Helpers
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.models.*
import uk.gov.hmrc.charitiesclaims.services.ClaimsService
import uk.gov.hmrc.charitiesclaims.util.{ControllerSpec, TestClaimsService, TestClaimsServiceHelper}

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class UpdateClaimControllerSpec extends ControllerSpec with TestClaimsServiceHelper {

  private val claimId = "12345"

  private def putClaims[A : Format](claimId: String, body: A) = testRequest(
    "PUT",
    s"/claims/$claimId",
    body
  )

  private val repaymentClaimDetails = RepaymentClaimDetails(
    claimingGiftAid = true,
    claimingTaxDeducted = true,
    claimingUnderGiftAidSmallDonationsScheme = true,
    claimReferenceNumber = Some("123")
  )

  private val orgDetails = OrganisationDetails(
    nameOfCharityRegulator = "test",
    reasonNotRegisteredWithRegulator = Some("test"),
    charityRegistrationNumber = Some("test"),
    areYouACorporateTrustee = true,
    doYouHaveCorporateTrusteeUKAddress = Some(true),
    doYouHaveAuthorisedOfficialTrusteeUKAddress = Some(true),
    nameOfCorporateTrustee = Some("test"),
    corporateTrusteePostcode = Some("test"),
    corporateTrusteeDaytimeTelephoneNumber = Some("test"),
    authorisedOfficialTrusteePostcode = Some("test"),
    authorisedOfficialTrusteeDaytimeTelephoneNumber = Some("test"),
    authorisedOfficialTrusteeTitle = Some("test"),
    authorisedOfficialTrusteeFirstName = Some("test"),
    authorisedOfficialTrusteeLastName = Some("test")
  )

  private val giftAidSmallDonationsSchemeDonationDetails =
    GiftAidSmallDonationsSchemeDonationDetails(
      adjustmentForGiftAidOverClaimed = 1.0,
      claims = List(
        GiftAidSmallDonationsSchemeClaim(
          taxYear = 2024,
          amountOfDonationsReceived = 1.0
        )
      ),
      connectedCharitiesScheduleData = List(
        ConnectedCharity(
          charityItem = 1,
          charityName = "test charity",
          charityReference = "test reference"
        )
      ),
      communityBuildingsScheduleData = List(
        CommunityBuilding(
          buildingItem = 1,
          buildingName = "test building",
          firstLineOfAddress = "test address",
          postcode = "test postcode",
          taxYearOneEnd = 2024,
          taxYearOneAmount = 1.0,
          taxYearTwoEnd = 2025,
          taxYearTwoAmount = 1.0,
          taxYearThreeEnd = 2026,
          taxYearThreeAmount = 1.0
        )
      )
    )

  private val requestRepaymentClaimDetails =
    putClaims(
      claimId,
      UpdateClaimRequest(
        repaymentClaimDetails = repaymentClaimDetails,
        lastUpdatedReference = "0123456789"
      )
    )

  private val requestUpdateOrgDetails =
    putClaims(
      claimId,
      UpdateClaimRequest(
        repaymentClaimDetails = repaymentClaimDetails,
        organisationDetails = Some(orgDetails),
        lastUpdatedReference = "0123456789"
      )
    )

  val requestUpdateClaimGiftAidSmallDonationsSchemeDonationDetails =
    putClaims(
      claimId,
      UpdateClaimRequest(
        repaymentClaimDetails = repaymentClaimDetails,
        giftAidSmallDonationsSchemeDonationDetails = Some(giftAidSmallDonationsSchemeDonationDetails),
        lastUpdatedReference = "0123456789"
      )
    )

  val claimsService = new TestClaimsService(initialClaims = Seq.empty)

  val existingClaim = Claim(
    claimId = claimId,
    userId = organisation1,
    claimSubmitted = false,
    lastUpdatedReference = "0123456789",
    creationTimestamp = LocalDateTime.now().toString,
    claimData = ClaimData(
      repaymentClaimDetails = RepaymentClaimDetails(
        claimingGiftAid = true,
        claimingTaxDeducted = false,
        claimingUnderGiftAidSmallDonationsScheme = false,
        claimReferenceNumber = Some("1234567890"),
        claimingDonationsNotFromCommunityBuilding = None,
        claimingDonationsCollectedInCommunityBuildings = None,
        connectedToAnyOtherCharities = None,
        makingAdjustmentToPreviousClaim = None
      )
    )
  )

  "PUT /claims" - {

    "return 200 when claim is updated for repayment claim details" in new AuthorisedOrganisationFixture {
      val mockClaimsService: ClaimsService = mock[ClaimsService]

      val expectedUpdate: Claim = existingClaim.copy(
        claimData = existingClaim.claimData.copy(
          repaymentClaimDetails = repaymentClaimDetails
        )
      )

      val captured = CaptureOne[Claim]()

      (mockClaimsService
        .getClaim(_: String))
        .expects(*)
        .returning(Future.successful(Some(existingClaim)))

      (mockClaimsService
        .putClaim(_: Claim))
        .expects(capture(captured))
        .returning(Future.successful(()))

      val controller =
        new UpdateClaimController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      private val result = controller.updateClaim(claimId)(requestRepaymentClaimDetails)
      status(result) shouldBe Status.OK
      val response = contentAsJson(result).as[UpdateClaimResponse]
      response.success shouldBe true

      captured.value shouldBe expectedUpdate.copy(lastUpdatedReference = response.lastUpdatedReference)
    }

    "return 200 when claim is updated for org details" in new AuthorisedOrganisationFixture {
      val mockClaimsService: ClaimsService = mock[ClaimsService]

      val expectedUpdate: Claim = existingClaim.copy(
        claimData = existingClaim.claimData.copy(
          repaymentClaimDetails = repaymentClaimDetails,
          organisationDetails = Some(orgDetails)
        )
      )

      val captured = CaptureOne[Claim]()

      (mockClaimsService
        .getClaim(_: String))
        .expects(*)
        .returning(Future.successful(Some(existingClaim)))

      (mockClaimsService
        .putClaim(_: Claim))
        .expects(capture(captured))
        .returning(Future.successful(()))

      val controller =
        new UpdateClaimController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      private val result = controller.updateClaim(claimId)(requestUpdateOrgDetails)
      status(result) shouldBe Status.OK
      val response = contentAsJson(result).as[UpdateClaimResponse]
      response.success shouldBe true

      captured.value shouldBe expectedUpdate.copy(lastUpdatedReference = response.lastUpdatedReference)
    }

    "return 200 if claim updated for giftAidSmallDonationsSchemeDonationDetails" in new AuthorisedOrganisationFixture {
      val mockClaimsService: ClaimsService = mock[ClaimsService]

      val expectedUpdate: Claim = existingClaim.copy(
        claimData = existingClaim.claimData.copy(
          repaymentClaimDetails = repaymentClaimDetails,
          giftAidSmallDonationsSchemeDonationDetails = Some(giftAidSmallDonationsSchemeDonationDetails)
        )
      )

      val captured = CaptureOne[Claim]()

      (mockClaimsService
        .getClaim(_: String))
        .expects(*)
        .returning(Future.successful(Some(existingClaim)))

      (mockClaimsService
        .putClaim(_: Claim))
        .expects(capture(captured))
        .returning(Future.successful(()))

      val controller =
        new UpdateClaimController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      private val result = controller.updateClaim(claimId)(requestUpdateClaimGiftAidSmallDonationsSchemeDonationDetails)
      status(result) shouldBe Status.OK
      val response = contentAsJson(result).as[UpdateClaimResponse]
      response.success shouldBe true

      captured.value shouldBe expectedUpdate.copy(lastUpdatedReference = response.lastUpdatedReference)
    }

    "return 404 when claim is not found" in new AuthorisedOrganisationFixture {

      val controller = new UpdateClaimController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val result = controller.updateClaim(claimId)(requestUpdateOrgDetails)
      status(result) shouldBe Status.NOT_FOUND
    }

    "return 400 when claim is already submitted" in new AuthorisedOrganisationFixture {
      val mockClaimsService: ClaimsService = mock[ClaimsService]

      (mockClaimsService
        .getClaim(_: String))
        .expects(*)
        .returning(Future.successful(Some(existingClaim.copy(claimSubmitted = true))))

      val controller =
        new UpdateClaimController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      private val result = controller.updateClaim(claimId)(requestRepaymentClaimDetails)
      status(result)                                               shouldBe Status.BAD_REQUEST
      contentAsJson(result).as[JsObject].value.get("errorMessage") shouldBe Some(
        JsString("Claim with claimId 12345 has already been submitted and cannot be updated")
      )
    }

    "return 400 when claim is already updated by another user" in new AuthorisedOrganisationFixture {
      val mockClaimsService: ClaimsService = mock[ClaimsService]

      (mockClaimsService
        .getClaim(_: String))
        .expects(*)
        .returning(Future.successful(Some(existingClaim.copy(lastUpdatedReference = "foobar"))))

      val controller =
        new UpdateClaimController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      private val result = controller.updateClaim(claimId)(requestRepaymentClaimDetails)
      status(result)                                               shouldBe Status.BAD_REQUEST
      contentAsJson(result).as[JsObject].value.get("errorMessage") shouldBe Some(
        JsString("Claim with claimId 12345 has already been updated by another user")
      )
      contentAsJson(result).as[JsObject].value.get("errorCode")    shouldBe Some(
        JsString("UPDATED_BY_ANOTHER_USER")
      )
    }

    "return 500 when the claims service returns an error" in new AuthorisedOrganisationFixture {

      val mockClaimsService: ClaimsService = mock[ClaimsService]

      (mockClaimsService
        .getClaim(_: String))
        .expects(*)
        .returning(Future.successful(Some(existingClaim)))

      (mockClaimsService
        .putClaim(_: Claim))
        .expects(*)
        .returning(Future.failed(new RuntimeException("Error message")))

      val controller =
        new UpdateClaimController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      val result = controller.updateClaim(claimId)(requestUpdateOrgDetails)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      val errorResponse = contentAsJson(result).as[JsObject]
      errorResponse.value.get("errorMessage") shouldBe Some(JsString("Error message"))
      errorResponse.value.get("errorCode")    shouldBe Some(JsString("CLAIM_SERVICE_ERROR"))
    }

    "return 400 when wrong entity format" in new AuthorisedOrganisationFixture {

      val mockClaimsService: ClaimsService = mock[ClaimsService]

      val malformedRequest = testRequest("PUT", "/claims", Json.obj("claimingGiftAid" -> true))

      val controller =
        new UpdateClaimController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      val result = controller.updateClaim(claimId)(malformedRequest)
      status(result) shouldBe Status.BAD_REQUEST
      val errorResponse = contentAsJson(result).as[JsObject]
      errorResponse.value.get("errorCode") shouldBe Some(JsString("INVALID_JSON_FORMAT"))
    }

    "return 400 when malformed JSON request" in new AuthorisedOrganisationFixture {

      val mockClaimsService: ClaimsService = mock[ClaimsService]

      val malformedRequest = testRequest("PUT", "/claims", "{\"claimingGiftAid\": true")

      val controller =
        new UpdateClaimController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      val result = controller.updateClaim(claimId)(malformedRequest)
      status(result) shouldBe Status.BAD_REQUEST
      val errorResponse = contentAsJson(result).as[JsObject]
      errorResponse.value.get("errorMessage") shouldBe Some(JsString("\"{\\\"claimingGiftAid\\\": true\""))
      errorResponse.value.get("errorCode")    shouldBe Some(JsString("MALFORMED_JSON"))
    }
  }
}
