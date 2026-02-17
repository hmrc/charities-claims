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

import play.api.libs.json.{JsObject, JsString}
import play.api.test.Helpers
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.connectors.ChRISConnector
import uk.gov.hmrc.charitiesclaims.models.*
import uk.gov.hmrc.charitiesclaims.models.chris.GovTalkMessage
import uk.gov.hmrc.charitiesclaims.services.{ChRISSubmissionService, ClaimsService}
import uk.gov.hmrc.charitiesclaims.util.{ChRISTestData, ControllerSpec, TestClaimsService, TestClaimsServiceHelper}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant
import java.util.UUID

class ChRISSubmissionControllerSpec extends ControllerSpec with TestClaimsServiceHelper {

  "POST /chris" - {
    "return 200 when claim is submitted to ChRIS" in new AuthorisedOrganisationFixture {

      val claim = Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = false,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = ClaimData(
          repaymentClaimDetails = RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false,
            claimReferenceNumber = Some("test-claim-reference-number")
          )
        )
      )

      val claimsService              = new TestClaimsService(initialClaims = Seq(claim))
      val chrisSubmissionServiceMock = mock[ChRISSubmissionService]
      val chrisConnectorMock         = mock[ChRISConnector]

      val controller = new ChRISSubmissionController(
        Helpers.stubControllerComponents(),
        authorisedAction,
        claimsService,
        chrisSubmissionServiceMock,
        chrisConnectorMock
      )

      (chrisSubmissionServiceMock
        .buildChRISSubmission(_: Claim, _: CurrentUser)(using _: HeaderCarrier))
        .expects(*, *, *)
        .returning(Future.successful(ChRISTestData.exampleMessage))

      (chrisConnectorMock
        .submitClaim(_: GovTalkMessage)(using _: HeaderCarrier))
        .expects(ChRISTestData.exampleMessage, *)
        .returning(Future.successful(()))

      val request = testRequest(
        "POST",
        "/chris",
        ChRISSubmissionRequest(
          claimId = "test-claim-id",
          lastUpdatedReference = claim.lastUpdatedReference
        )
      )

      val result   = controller.submitClaim()(request)
      val json     = contentAsJson(result)
      val response = json.as[ChRISSubmissionResponse]
      status(result)               shouldBe OK
      response.success             shouldBe true
      response.submissionReference shouldBe claim.lastUpdatedReference

      val (updatedClaim, _) = claimsService.getClaim("test-claim-id").futureValue.get
      updatedClaim.claimSubmitted                               shouldBe true
      updatedClaim.submissionDetails.map(_.submissionTimestamp) shouldBe Some(response.submissionTimestamp)
      updatedClaim.submissionDetails.map(_.submissionReference) shouldBe Some(claim.lastUpdatedReference)
    }

    "return 404 when claim does not exist" in new AuthorisedOrganisationFixture {

      val claimsService              = new TestClaimsService(initialClaims = Seq.empty)
      val chrisSubmissionServiceMock = mock[ChRISSubmissionService]
      val chrisConnectorMock         = mock[ChRISConnector]

      val controller = new ChRISSubmissionController(
        Helpers.stubControllerComponents(),
        authorisedAction,
        claimsService,
        chrisSubmissionServiceMock,
        chrisConnectorMock
      )

      val request = testRequest(
        "POST",
        "/chris",
        ChRISSubmissionRequest(
          claimId = "test-claim-id",
          lastUpdatedReference = "b4ae2a97-d97b-42d9-bf80-fe3db41968b4"
        )
      )

      val result = controller.submitClaim()(request)
      val json   = contentAsJson(result)
      status(result) shouldBe NOT_FOUND

      json.as[JsObject].value.get("errorMessage") shouldBe Some(JsString("Claim with claimId test-claim-id not found"))
      json.as[JsObject].value.get("errorCode")    shouldBe Some(JsString("CLAIM_NOT_FOUND_ERROR"))
    }

    "return 400 when claim is already submitted" in new AuthorisedOrganisationFixture {

      val claim = Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = true,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = ClaimData(
          repaymentClaimDetails = RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false,
            claimReferenceNumber = Some("test-claim-reference-number")
          )
        )
      )

      val claimsService              = new TestClaimsService(initialClaims = Seq(claim))
      val chrisSubmissionServiceMock = mock[ChRISSubmissionService]
      val chrisConnectorMock         = mock[ChRISConnector]

      val controller = new ChRISSubmissionController(
        Helpers.stubControllerComponents(),
        authorisedAction,
        claimsService,
        chrisSubmissionServiceMock,
        chrisConnectorMock
      )

      val request = testRequest(
        "POST",
        "/chris",
        ChRISSubmissionRequest(
          claimId = "test-claim-id",
          lastUpdatedReference = claim.lastUpdatedReference
        )
      )

      val result = controller.submitClaim()(request)
      val json   = contentAsJson(result)
      status(result) shouldBe BAD_REQUEST

      json.as[JsObject].value.get("errorMessage") shouldBe Some(
        JsString("Claim with claimId test-claim-id has already been submitted to ChRIS")
      )
      json.as[JsObject].value.get("errorCode")    shouldBe Some(JsString("CLAIM_ALREADY_SUBMITTED_ERROR"))
    }

    "return 400 when claim has already been updated by another user" in new AuthorisedOrganisationFixture {

      val claim = Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = false,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = ClaimData(
          repaymentClaimDetails = RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false,
            claimReferenceNumber = Some("test-claim-reference-number")
          )
        )
      )

      val claimsService              = new TestClaimsService(initialClaims = Seq(claim))
      val chrisSubmissionServiceMock = mock[ChRISSubmissionService]
      val chrisConnectorMock         = mock[ChRISConnector]

      val controller = new ChRISSubmissionController(
        Helpers.stubControllerComponents(),
        authorisedAction,
        claimsService,
        chrisSubmissionServiceMock,
        chrisConnectorMock
      )

      val request = testRequest(
        "POST",
        "/chris",
        ChRISSubmissionRequest(
          claimId = "test-claim-id",
          lastUpdatedReference = "b4ae2a97-d97b-42d9-bf80-fe3db41968b4"
        )
      )

      val result = controller.submitClaim()(request)
      val json   = contentAsJson(result)
      status(result) shouldBe BAD_REQUEST

      json.as[JsObject].value.get("errorMessage") shouldBe Some(
        JsString("Claim with claimId test-claim-id has already been updated by another user")
      )
      json.as[JsObject].value.get("errorCode")    shouldBe Some(JsString("UPDATED_BY_ANOTHER_USER"))
    }

    "return 500 when getting claim from claim service returns an error" in new AuthorisedOrganisationFixture {

      val claimsService              = mock[ClaimsService]
      val chrisSubmissionServiceMock = mock[ChRISSubmissionService]
      val chrisConnectorMock         = mock[ChRISConnector]

      val controller = new ChRISSubmissionController(
        Helpers.stubControllerComponents(),
        authorisedAction,
        claimsService,
        chrisSubmissionServiceMock,
        chrisConnectorMock
      )

      (claimsService
        .getClaim(_: String))
        .expects("test-claim-id")
        .returning(Future.failed(new RuntimeException("Error message")))

      val request = testRequest(
        "POST",
        "/chris",
        ChRISSubmissionRequest(
          claimId = "test-claim-id",
          lastUpdatedReference = "b4ae2a97-d97b-42d9-bf80-fe3db41968b4"
        )
      )

      val result = controller.submitClaim()(request)
      val json   = contentAsJson(result)
      status(result) shouldBe INTERNAL_SERVER_ERROR

      json.as[JsObject].value.get("errorMessage") shouldBe Some(
        JsString("Cannot get claim with claimId test-claim-id because of java.lang.RuntimeException: Error message")
      )
      json.as[JsObject].value.get("errorCode")    shouldBe Some(
        JsString("CLAIM_SERVICE_ERROR")
      )
    }

    "return 500 when updating claim in the claim service returns an error" in new AuthorisedOrganisationFixture {

      val claim = Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = false,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = ClaimData(
          repaymentClaimDetails = RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false,
            claimReferenceNumber = Some("test-claim-reference-number")
          )
        )
      )

      val claimsService              = mock[ClaimsService]
      val chrisSubmissionServiceMock = mock[ChRISSubmissionService]
      val chrisConnectorMock         = mock[ChRISConnector]

      val controller = new ChRISSubmissionController(
        Helpers.stubControllerComponents(),
        authorisedAction,
        claimsService,
        chrisSubmissionServiceMock,
        chrisConnectorMock
      )

      (claimsService
        .getClaim(_: String))
        .expects("test-claim-id")
        .returning(Future.successful(Some((claim, Instant.now()))))

      (claimsService
        .putClaim(_: Claim))
        .expects(*)
        .returning(Future.failed(new RuntimeException("Error message")))

      (chrisSubmissionServiceMock
        .buildChRISSubmission(_: Claim, _: CurrentUser)(using _: HeaderCarrier))
        .expects(*, *, *)
        .returning(Future.successful(ChRISTestData.exampleMessage))

      (chrisConnectorMock
        .submitClaim(_: GovTalkMessage)(using _: HeaderCarrier))
        .expects(ChRISTestData.exampleMessage, *)
        .returning(Future.successful(()))

      val request = testRequest(
        "POST",
        "/chris",
        ChRISSubmissionRequest(
          claimId = "test-claim-id",
          lastUpdatedReference = claim.lastUpdatedReference
        )
      )

      val result = controller.submitClaim()(request)
      val json   = contentAsJson(result)
      status(result) shouldBe INTERNAL_SERVER_ERROR

      json.as[JsObject].value.get("errorMessage") shouldBe Some(
        JsString(
          "ChRIS submission was successful but cannot update claim with claimId test-claim-id because of java.lang.RuntimeException: Error message"
        )
      )
      json.as[JsObject].value.get("errorCode")    shouldBe Some(
        JsString("CLAIM_SERVICE_ERROR")
      )
    }

    "return 500 when ChRIS submission fails" in new AuthorisedOrganisationFixture {

      val claim = Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = false,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = ClaimData(
          repaymentClaimDetails = RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false,
            claimReferenceNumber = Some("test-claim-reference-number")
          )
        )
      )

      val claimsService              = new TestClaimsService(initialClaims = Seq(claim))
      val chrisSubmissionServiceMock = mock[ChRISSubmissionService]
      val chrisConnectorMock         = mock[ChRISConnector]

      val controller = new ChRISSubmissionController(
        Helpers.stubControllerComponents(),
        authorisedAction,
        claimsService,
        chrisSubmissionServiceMock,
        chrisConnectorMock
      )

      (chrisSubmissionServiceMock
        .buildChRISSubmission(_: Claim, _: CurrentUser)(using _: HeaderCarrier))
        .expects(*, *, *)
        .returning(Future.successful(ChRISTestData.exampleMessage))

      (chrisConnectorMock
        .submitClaim(_: GovTalkMessage)(using _: HeaderCarrier))
        .expects(ChRISTestData.exampleMessage, *)
        .returning(Future.failed(Exception("Request to POST failed")))

      val request = testRequest(
        "POST",
        "/chris",
        ChRISSubmissionRequest(
          claimId = "test-claim-id",
          lastUpdatedReference = claim.lastUpdatedReference
        )
      )

      val result = controller.submitClaim()(request)
      val json   = contentAsJson(result)
      status(result) shouldBe INTERNAL_SERVER_ERROR

      json.as[JsObject].value.get("errorMessage") shouldBe Some(
        JsString("Request to POST failed")
      )
      json.as[JsObject].value.get("errorCode")    shouldBe Some(
        JsString("CHRIS_SUBMISSION_ERROR")
      )
    }
  }
}
