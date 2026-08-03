/*
 * Copyright 2026 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.charitiesclaims.models.*
import uk.gov.hmrc.charitiesclaims.models.summary.*
import uk.gov.hmrc.charitiesclaims.services.{ClaimsService, SubmissionSummaryService}
import uk.gov.hmrc.charitiesclaims.util.ControllerSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class GetSubmissionSummaryControllerSpec extends ControllerSpec with MockitoSugar {
  private val mockClaimsService  = mock[ClaimsService]
  private val mockSummaryService = mock[SubmissionSummaryService]

  private val fakeRequest = FakeRequest("GET", "/summary")
  private val testClaimId = "claim-1"
  private val claim       =
    Claim(
      claimId = testClaimId,
      userId = "user-1",
      claimSubmitted = true,
      lastUpdatedReference = "ref",
      claimData = ClaimData(
        repaymentClaimDetails = RepaymentClaimDetails(true, true, false)
      ),
      submissionDetails = Some(
        SubmissionDetails(
          submissionTimestamp = "2025-01-01T10:00:00Z",
          submissionReference = "SUB123"
        )
      )
    )

  private val summaryResponse =
    SubmissionSummaryResponse(
      claimDetails = ClaimDetails(
        charityName = "Test Charity",
        hmrcCharityReference = "CHAR123",
        submissionTimestamp = "2025-01-01T10:00:00Z",
        submittedBy = "Corp Trustee"
      ),
      giftAidDetails = None,
      otherIncomeDetails = None,
      gasdsDetails = None,
      adjustmentDetails = None,
      submissionReferenceNumber = "SUB123"
    )

  "GET /submission-summary/:claimId" - {
    "return 404 when claim is not found" in new AuthorisedOrganisationFixture {
      val controller = new GetSubmissionSummaryController(
        cc = Helpers.stubControllerComponents(),
        authorisedAction = authorisedAction,
        claimsService = mockClaimsService,
        submissionSummaryService = mockSummaryService
      )
      when(mockClaimsService.getClaim("test-claim-not-found")).thenReturn(Future.successful(None))

      private val result = controller.getSummary(claimId = "test-claim-not-found")(fakeRequest)
      status(result)                                   shouldBe Status.NOT_FOUND
      (contentAsJson(result) \ "errorCode").as[String] shouldBe "CLAIM_NOT_FOUND_ERROR"
    }
  }

  "return 400 when claim is not submitted" in new AuthorisedOrganisationFixture {
    val controller                = new GetSubmissionSummaryController(
      cc = Helpers.stubControllerComponents(),
      authorisedAction = authorisedAction,
      claimsService = mockClaimsService,
      submissionSummaryService = mockSummaryService
    )
    private val notSubmittedClaim = claim.copy(
      claimSubmitted = false,
      submissionDetails = None
    )
    when(mockClaimsService.getClaim("test-claim-not-found"))
      .thenReturn(Future.successful(Some(notSubmittedClaim, None)))

    private val result = controller.getSummary(claimId = "test-claim-not-found")(fakeRequest)

    status(result)                                   shouldBe BAD_REQUEST
    (contentAsJson(result) \ "errorCode").as[String] shouldBe "CLAIM_NOT_SUBMITTED_ERROR"
  }

  "return 200 with submission summary when claim exists and submitted" in new AuthorisedOrganisationFixture {
    val controller = new GetSubmissionSummaryController(
      cc = Helpers.stubControllerComponents(),
      authorisedAction = authorisedAction,
      claimsService = mockClaimsService,
      submissionSummaryService = mockSummaryService
    )
    when(mockClaimsService.getClaim(testClaimId))
      .thenReturn(Future.successful(Some(claim, None)))

    when(mockSummaryService.getSummary(any(), any())(using any[HeaderCarrier]))
      .thenReturn(Future.successful(summaryResponse))

    private val result = controller.getSummary(testClaimId)(fakeRequest)

    status(result)        shouldBe OK
    contentAsJson(result) shouldBe Json.toJson(summaryResponse)
  }

  "return 500 when an unexpected exception occurs" in new AuthorisedOrganisationFixture {
    val controller = new GetSubmissionSummaryController(
      cc = Helpers.stubControllerComponents(),
      authorisedAction = authorisedAction,
      claimsService = mockClaimsService,
      submissionSummaryService = mockSummaryService
    )
    when(mockClaimsService.getClaim(testClaimId))
      .thenReturn(Future.failed(new RuntimeException("exception")))

    private val result = controller.getSummary(testClaimId)(fakeRequest)

    status(result)                                   shouldBe INTERNAL_SERVER_ERROR
    (contentAsJson(result) \ "errorCode").as[String] shouldBe "INTERNAL_SERVER_ERROR"
  }
}
