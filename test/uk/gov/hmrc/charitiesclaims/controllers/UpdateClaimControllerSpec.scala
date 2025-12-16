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
  given ExecutionContext = global

  private val claimId = "12345"

  private def putClaims[A : Format](body: A) = testRequest(
    "PUT",
    "/claims",
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
    doYouHaveUKAddress = Some(true),
    nameOfCorporateTrustee = Some("test"),
    corporateTrusteePostcode = Some("test"),
    corporateTrusteeDaytimeTelephoneNumber = Some("test"),
    corporateTrusteeTitle = Some("test"),
    corporateTrusteeFirstName = Some("test"),
    corporateTrusteeLastName = Some("test")
  )

  private val gasds = GiftAidScheduleData(
    earliestDonationDate = "test",
    prevOverclaimedGiftAid = 1.0,
    totalDonations = 2.0,
    donations = List(
      Donation(
        donationItem = 1,
        donationDate = "test donation",
        donationAmount = 3.0,
        donorTitle = Some("test-title"),
        donorFirstName = Some("test-firstname"),
        donorLastName = Some("test-lastname"),
        donorHouse = Some("test house"),
        donorPostcode = Some("test postcode"),
        sponsoredEvent = Some(true),
        aggregatedDonations = Some("test agg")
      )
    )
  )

  private val requestRepaymentClaimDetails =
    putClaims(
      UpdateClaimRequest(
        claimId,
        repaymentClaimDetails = Some(repaymentClaimDetails)
      )
    )

  private val requestUpdateOrgDetails =
    putClaims(
      UpdateClaimRequest(
        claimId,
        organisationDetails = Some(orgDetails)
      )
    )

  val requestUpdateClaimGiftAidSmallDonationsScheme =
    putClaims(
      UpdateClaimRequest(
        claimId,
        giftAidScheduleData = Some(gasds)
      )
    )

  val claimsService               = new TestClaimsService(initialClaims = Seq.empty)
  val claimsServiceExistingClaims = new TestClaimsService(initialClaims = Seq.empty)

  val existingClaim = Claim(
    claimId = claimId,
    userId = organisation1,
    claimSubmitted = false,
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

      private val result = controller.updateClaim()(requestRepaymentClaimDetails)
      status(result)                                shouldBe Status.OK
      contentAsJson(result).as[UpdateClaimResponse] shouldBe UpdateClaimResponse(success = true)

      captured.value shouldBe expectedUpdate
    }

    "return 200 when claim is updated for org details" in new AuthorisedOrganisationFixture {
      val mockClaimsService: ClaimsService = mock[ClaimsService]

      val expectedUpdate: Claim = existingClaim.copy(
        claimData = existingClaim.claimData.copy(
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

      private val result = controller.updateClaim()(requestUpdateOrgDetails)
      status(result)                                shouldBe Status.OK
      contentAsJson(result).as[UpdateClaimResponse] shouldBe UpdateClaimResponse(success = true)

      captured.value shouldBe expectedUpdate
    }

    "return 404 when claim is not found" in new AuthorisedOrganisationFixture {

      val controller = new UpdateClaimController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val result = controller.updateClaim()(requestUpdateOrgDetails)
      status(result) shouldBe Status.NOT_FOUND
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

      val result = controller.updateClaim()(requestUpdateOrgDetails)
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

      val result = controller.updateClaim()(malformedRequest)
      status(result) shouldBe Status.BAD_REQUEST
      val errorResponse = contentAsJson(result).as[JsObject]
      errorResponse.value.get("errorCode") shouldBe Some(JsString("INVALID_JSON_FORMAT"))
    }

    "return 400 when malformed JSON request" in new AuthorisedOrganisationFixture {

      val mockClaimsService: ClaimsService = mock[ClaimsService]

      val malformedRequest = testRequest("PUT", "/claims", "{\"claimingGiftAid\": true")

      val controller =
        new UpdateClaimController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      val result = controller.updateClaim()(malformedRequest)
      status(result) shouldBe Status.BAD_REQUEST
      val errorResponse = contentAsJson(result).as[JsObject]
      errorResponse.value.get("errorMessage") shouldBe Some(JsString("\"{\\\"claimingGiftAid\\\": true\""))
      errorResponse.value.get("errorCode")    shouldBe Some(JsString("MALFORMED_JSON"))
    }
  }
}
