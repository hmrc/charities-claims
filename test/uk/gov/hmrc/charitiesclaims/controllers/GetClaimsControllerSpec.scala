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
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.test.Helpers
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.models.Claim
import uk.gov.hmrc.charitiesclaims.services.ClaimsService
import uk.gov.hmrc.charitiesclaims.util.ControllerSpec
import uk.gov.hmrc.charitiesclaims.util.TestClaimsService
import uk.gov.hmrc.charitiesclaims.util.TestClaimsServiceHelper

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GetClaimsControllerSpec extends ControllerSpec with TestClaimsServiceHelper {
  given ExecutionContext = global

  val claimsService = new TestClaimsService(initialTestClaimsSet)

  "GET /claims" - {
    "return 200 with only claimId when user is an organisation" in new AuthorisedOrganisationFixture {

      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val request = testRequest("GET", "/claims?claimSubmitted=true")

      val result = controller.getClaims(claimSubmitted = true)(request)
      status(result) shouldBe Status.OK

      val json       = contentAsJson(result).as[JsObject]
      val claimsList = (json \ "claimsList").as[JsArray]

      (json \ "claimsCount").as[Int] shouldBe 1
      claimsList.value.size          shouldBe 1

      val firstClaim = claimsList.value.head.as[JsObject]
      (firstClaim \ "claimId").as[String] shouldBe "test-claim-submitted"

      firstClaim.keys shouldBe Set("claimId")
    }

    "return 200 with claimId, hmrcCharitiesReference, and nameOfCharity when user is an agent" in new AuthorisedAgentFixture {

      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val request = testRequest("GET", "/claims?claimSubmitted=true")

      val result = controller.getClaims(claimSubmitted = true)(request)
      status(result) shouldBe Status.OK

      val json       = contentAsJson(result).as[JsObject]
      val claimsList = (json \ "claimsList").as[JsArray]

      (json \ "claimsCount").as[Int] shouldBe 1
      claimsList.value.size          shouldBe 1

      val firstClaim = claimsList.value.head.as[JsObject]
      (firstClaim \ "claimId").as[String] shouldBe "test-claim-submitted-2"

      firstClaim.keys shouldBe Set("claimId", "hmrcCharitiesReference", "nameOfCharity")
    }

    "return 200 with multiple claims for organisation" in new AuthorisedOrganisationFixture {

      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val request = testRequest("GET", "/claims?claimSubmitted=false")

      val result = controller.getClaims(claimSubmitted = false)(request)
      status(result) shouldBe Status.OK

      val json       = contentAsJson(result).as[JsObject]
      val claimsList = (json \ "claimsList").as[JsArray]

      (json \ "claimsCount").as[Int]                        shouldBe 3
      claimsList.value.map(c => (c \ "claimId").as[String]) shouldBe Seq(
        "test-claim-unsubmitted-1",
        "test-claim-unsubmitted-2",
        "test-claim-unsubmitted-3"
      )

      claimsList.value.foreach(c => c.as[JsObject].keys shouldBe Set("claimId"))
    }

    "return 200 with multiple claims for agent" in new AuthorisedAgentFixture {

      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val request = testRequest("GET", "/claims?claimSubmitted=false")

      val result = controller.getClaims(claimSubmitted = false)(request)
      status(result) shouldBe Status.OK

      val json       = contentAsJson(result).as[JsObject]
      val claimsList = (json \ "claimsList").as[JsArray]

      (json \ "claimsCount").as[Int]                        shouldBe 3
      claimsList.value.map(c => (c \ "claimId").as[String]) shouldBe Seq(
        "test-claim-unsubmitted-1-2",
        "test-claim-unsubmitted-2-2",
        "test-claim-unsubmitted-3-2"
      )

      claimsList.value.foreach(c =>
        c.as[JsObject].keys shouldBe Set("claimId", "hmrcCharitiesReference", "nameOfCharity")
      )
    }

    "return 500 when the claims service returns an error" in new AuthorisedOrganisationFixture {

      val mockClaimsService: ClaimsService = mock[ClaimsService]

      (mockClaimsService
        .listClaims(_: String, _: Boolean))
        .expects(*, *)
        .anyNumberOfTimes()
        .returning(Future.failed(new RuntimeException("Error message")))

      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      val request = testRequest("GET", "/claims?claimSubmitted=true")

      val result = controller.getClaims(claimSubmitted = true)(request)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      val errorResponse = contentAsJson(result).as[JsObject]
      errorResponse.value.get("errorMessage") shouldBe Some(JsString("Error message"))
      errorResponse.value.get("errorCode")    shouldBe Some(JsString("CLAIM_SERVICE_ERROR"))
    }
  }

  "GET /claims/:claimId" - {
    "return 200 when the claim exists" in new AuthorisedOrganisationFixture {
      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val request = testRequest("GET", "/claims/test-claim-submitted")

      val result = controller.getClaim(claimId = "test-claim-submitted")(request)
      status(result) shouldBe Status.OK

      val json = contentAsJson(result).as[JsObject]
      (json \ "claimId").as[String]            shouldBe "test-claim-submitted"
      (json \ "userId").as[String]             shouldBe organisation1
      (json \ "claimSubmitted").as[Boolean]    shouldBe true
      (json \ "creationTimestamp").asOpt[String] should not be empty
      val claim = (json - "creationTimestamp").as[Claim]
      claim.claimData.repaymentClaimDetails.claimingGiftAid                          shouldBe true
      claim.claimData.repaymentClaimDetails.claimingTaxDeducted                      shouldBe false
      claim.claimData.repaymentClaimDetails.claimingUnderGiftAidSmallDonationsScheme shouldBe false
      claim.claimData.organisationDetails.isDefined                                  shouldBe true
      claim.claimData.declarationDetails.isDefined                                   shouldBe true
      claim.claimData.giftAidSmallDonationsSchemeDonationDetails.isDefined           shouldBe false
      claim.submissionDetails.isDefined                                              shouldBe true
    }

    "return lastUpdatedReference in response for optimistic locking" in new AuthorisedOrganisationFixture {
      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val request = testRequest("GET", "/claims/test-claim-submitted")

      val result = controller.getClaim(claimId = "test-claim-submitted")(request)
      status(result) shouldBe Status.OK

      val json = contentAsJson(result).as[JsObject]
      (json \ "lastUpdatedReference").as[String] shouldBe "test-last-updated-reference"
    }

    "return 404 when the claim does not exist" in new AuthorisedOrganisationFixture {
      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val request = testRequest("GET", "/claims/test-claim-not-found")

      val result = controller.getClaim(claimId = "test-claim-not-found")(request)
      status(result) shouldBe Status.NOT_FOUND
    }

    "return 500 when the claims service returns an error" in new AuthorisedOrganisationFixture {
      val mockClaimsService: ClaimsService = mock[ClaimsService]

      (mockClaimsService
        .getClaim(_: String))
        .expects(*)
        .anyNumberOfTimes()
        .returning(Future.failed(new RuntimeException("Error message")))

      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      val request = testRequest("GET", "/claims/test-claim-submitted")

      val result = controller.getClaim(claimId = "test-claim-submitted")(request)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      val errorResponse = contentAsJson(result).as[JsObject]
      errorResponse.value.get("errorMessage") shouldBe Some(JsString("Error message"))
      errorResponse.value.get("errorCode")    shouldBe Some(JsString("CLAIM_SERVICE_ERROR"))
    }
  }
}
