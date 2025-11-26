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

import play.api.http.Status
import play.api.test.Helpers
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.models.Claim
import uk.gov.hmrc.charitiesclaims.models.ClaimData
import uk.gov.hmrc.charitiesclaims.models.RepaymentClaimDetails
import uk.gov.hmrc.charitiesclaims.models.SaveClaimRequest
import uk.gov.hmrc.charitiesclaims.models.SaveClaimResponse
import uk.gov.hmrc.charitiesclaims.util.ControllerSpec
import uk.gov.hmrc.charitiesclaims.util.TestClaimsService
import uk.gov.hmrc.charitiesclaims.util.TestClaimsServiceHelper

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.charitiesclaims.services.ClaimsService
import scala.concurrent.Future
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json

class SaveClaimControllerSpec extends ControllerSpec with TestClaimsServiceHelper {
  given ExecutionContext = global

  val requestSaveClaimGiftAid =
    testRequest(
      "POST",
      "/claims",
      SaveClaimRequest(
        claimingGiftAid = true,
        claimingTaxDeducted = false,
        claimingUnderGasds = false,
        claimReferenceNumber = Some("1234567890"),
        claimingDonationsNotFromCommunityBuilding = None,
        claimingDonationsCollectedInCommunityBuildings = None,
        connectedToAnyOtherCharities = None,
        makingAdjustmentToPreviousClaim = None
      )
    )

  val requestSaveClaimGasds =
    testRequest(
      "POST",
      "/claims",
      SaveClaimRequest(
        claimingGiftAid = false,
        claimingTaxDeducted = false,
        claimingUnderGasds = true,
        claimReferenceNumber = None,
        claimingDonationsNotFromCommunityBuilding = Some(true),
        claimingDonationsCollectedInCommunityBuildings = None,
        connectedToAnyOtherCharities = Some(true),
        makingAdjustmentToPreviousClaim = None
      )
    )

  val claimsService = new TestClaimsService(initialClaims = Seq.empty)

  "POST /claims" - {
    "return 200 when claim is saved for a gift aid claim" in new AuthorisedOrganisationFixture {

      val controller = new SaveClaimController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val result = controller.saveClaim()(requestSaveClaimGiftAid)
      status(result) shouldBe Status.OK
      val saveClaimResponse = contentAsJson(result).as[SaveClaimResponse]

      await(claimsService.getClaim(saveClaimResponse.claimId)) shouldEqual Some(
        Claim(
          claimId = saveClaimResponse.claimId,
          userId = organisation1,
          claimSubmitted = false,
          creationTimestamp = saveClaimResponse.creationTimestamp,
          claimData = ClaimData(
            repaymentClaimDetails = RepaymentClaimDetails(
              claimingGiftAid = true,
              claimingTaxDeducted = false,
              claimingUnderGasds = false,
              claimReferenceNumber = Some("1234567890"),
              claimingDonationsNotFromCommunityBuilding = None,
              claimingDonationsCollectedInCommunityBuildings = None,
              connectedToAnyOtherCharities = None,
              makingAdjustmentToPreviousClaim = None
            )
          )
        )
      )
    }

    "return 200 when claim is saved for gasds claim" in new AuthorisedOrganisationFixture {

      val controller = new SaveClaimController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val result = controller.saveClaim()(requestSaveClaimGasds)
      status(result) shouldBe Status.OK
      val saveClaimResponse = contentAsJson(result).as[SaveClaimResponse]

      await(claimsService.getClaim(saveClaimResponse.claimId)) shouldEqual Some(
        Claim(
          claimId = saveClaimResponse.claimId,
          userId = organisation1,
          claimSubmitted = false,
          creationTimestamp = saveClaimResponse.creationTimestamp,
          claimData = ClaimData(
            repaymentClaimDetails = RepaymentClaimDetails(
              claimingGiftAid = false,
              claimingTaxDeducted = false,
              claimingUnderGasds = true,
              claimReferenceNumber = None,
              claimingDonationsNotFromCommunityBuilding = Some(true),
              claimingDonationsCollectedInCommunityBuildings = None,
              connectedToAnyOtherCharities = Some(true),
              makingAdjustmentToPreviousClaim = None
            )
          )
        )
      )
    }

    "return 500 when the claims service returns an error" in new AuthorisedOrganisationFixture {

      val mockClaimsService: ClaimsService = mock[ClaimsService]

      (mockClaimsService
        .putClaim(_: Claim))
        .expects(*)
        .returning(Future.failed(new RuntimeException("Error message")))

      val controller = new SaveClaimController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      val result = controller.saveClaim()(requestSaveClaimGiftAid)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      val errorResponse = contentAsJson(result).as[JsObject]
      errorResponse.value.get("errorMessage") shouldBe Some(JsString("Error message"))
      errorResponse.value.get("errorCode")    shouldBe Some(JsString("CLAIM_SERVICE_ERROR"))
    }

    "return 400 when wrong entity format" in new AuthorisedOrganisationFixture {

      val mockClaimsService: ClaimsService = mock[ClaimsService]

      val malformedRequest = testRequest("POST", "/claims", Json.obj("claimingGiftAid" -> true))

      val controller = new SaveClaimController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      val result = controller.saveClaim()(malformedRequest)
      status(result) shouldBe Status.BAD_REQUEST
      val errorResponse = contentAsJson(result).as[JsObject]
      errorResponse.value.get("errorMessage") shouldBe Some(
        JsString(
          "unmarshalling failed at /claimingUnderGasds because of error.path.missing, at /claimingTaxDeducted because of error.path.missing"
        )
      )
      errorResponse.value.get("errorCode")    shouldBe Some(JsString("INVALID_JSON_FORMAT"))
    }

    "return 400 when malformed JSON request" in new AuthorisedOrganisationFixture {

      val mockClaimsService: ClaimsService = mock[ClaimsService]

      val malformedRequest = testRequest("POST", "/claims", "{\"claimingGiftAid\": true")

      val controller = new SaveClaimController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      val result = controller.saveClaim()(malformedRequest)
      status(result) shouldBe Status.BAD_REQUEST
      val errorResponse = contentAsJson(result).as[JsObject]
      errorResponse.value.get("errorMessage") shouldBe Some(JsString("\"{\\\"claimingGiftAid\\\": true\""))
      errorResponse.value.get("errorCode")    shouldBe Some(JsString("MALFORMED_JSON"))
    }

  }

}
