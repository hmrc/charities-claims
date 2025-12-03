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
import play.api.libs.json.{JsBoolean, JsObject, JsString}
import play.api.test.Helpers
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.models.Claim
import uk.gov.hmrc.charitiesclaims.services.ClaimsService
import uk.gov.hmrc.charitiesclaims.util.{ControllerSpec, TestClaimsService, TestClaimsServiceHelper}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class DeleteClaimControllerSpec extends ControllerSpec with TestClaimsServiceHelper {

  def requestDeleteClaim(claimId: String) =
    testRequest("DELETE", s"/claims/$claimId")

  val claimsService = new TestClaimsService(initialTestClaimsSet)

  "DELETE /claims/:claimId" - {
    for (claim <- initialTestClaimsSet)
      s"return 200 when claim ${claim.claimId} is deleted" in new AuthorisedOrganisationFixture {

        await(claimsService.getClaim(claim.claimId)).isDefined shouldBe true

        val controller = new DeleteClaimController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

        val result = controller.deleteClaim(claim.claimId)(requestDeleteClaim(claim.claimId))
        status(result) shouldBe Status.OK
        val deleteClaimResponse = contentAsJson(result).as[JsObject]
        deleteClaimResponse.value.get("success") shouldBe Some(JsBoolean(true))

        await(claimsService.getClaim(claim.claimId)) shouldBe None
      }

    "be idempotent and return 200 even when claimId is not present in the cache" in new AuthorisedOrganisationFixture {
      val controller = new DeleteClaimController(Helpers.stubControllerComponents(), authorisedAction, claimsService)
      val result     = controller.deleteClaim("test-claim-not-found")(requestDeleteClaim("test-claim-not-found"))
      status(result) shouldBe Status.OK
      val deleteClaimResponse = contentAsJson(result).as[JsObject]
      deleteClaimResponse.value.get("success") shouldBe Some(JsBoolean(true))
    }

    "return 500 when the claims service returns an error" in new AuthorisedOrganisationFixture {
      val mockClaimsService: ClaimsService = mock[ClaimsService]
      (mockClaimsService
        .deleteClaim(_: String)(using _: HeaderCarrier))
        .expects("claim-id", *)
        .anyNumberOfTimes()
        .returning(Future.failed(new RuntimeException("Error message")))

      val controller =
        new DeleteClaimController(Helpers.stubControllerComponents(), authorisedAction, mockClaimsService)

      val result = controller.deleteClaim("claim-id")(requestDeleteClaim("claim-id"))
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

      val errorResponse = contentAsJson(result).as[JsObject]
      errorResponse.value.get("errorMessage") shouldBe Some(JsString("Error message"))
      errorResponse.value.get("errorCode")    shouldBe Some(JsString("CLAIM_SERVICE_ERROR"))
    }
  }
}
