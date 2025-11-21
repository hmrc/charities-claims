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
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.util.ControllerSpec
import uk.gov.hmrc.charitiesclaims.models.GetClaimsRequest
import play.api.libs.json.Json
import uk.gov.hmrc.charitiesclaims.util.TestClaimsServiceHelper
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.charitiesclaims.util.TestClaimsService
import uk.gov.hmrc.charitiesclaims.models.GetClaimsResponse

class GetClaimsControllerSpec extends ControllerSpec with TestClaimsServiceHelper {
  given ExecutionContext = global

  val requestGetClaimsSubmitted = FakeRequest("POST", "/get-claims")
    .withJsonBody(Json.toJson(GetClaimsRequest(claimSubmitted = true)))

  val requestGetClaimsUnsubmitted = FakeRequest("POST", "/get-claims")
    .withJsonBody(Json.toJson(GetClaimsRequest(claimSubmitted = false)))

  val claimsService = new TestClaimsService(initialTestClaimsSet)

  "POST /get-claims" - {
    "return 200 when requested submitted claims and user is an organisation" in new AuthorisedOrganisationFixture {

      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val result = controller.getClaims()(requestGetClaimsSubmitted)
      status(result) shouldBe Status.OK
      val getClaimsResponse = contentAsJson(result).as[GetClaimsResponse]
      getClaimsResponse.claimsCount                    shouldBe 1
      getClaimsResponse.claimsList.head.userId         shouldBe organisation1
      getClaimsResponse.claimsList.head.claimSubmitted shouldBe true
      getClaimsResponse.claimsList.head.claimId        shouldBe "test-claim-submitted"
    }

    "return 200 when requested submitted claims and user is an agent" in new AuthorisedAgentFixture {

      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val result = controller.getClaims()(requestGetClaimsSubmitted)
      status(result) shouldBe Status.OK
      val getClaimsResponse = contentAsJson(result).as[GetClaimsResponse]
      getClaimsResponse.claimsCount                    shouldBe 1
      getClaimsResponse.claimsList.head.userId         shouldBe agent1
      getClaimsResponse.claimsList.head.claimSubmitted shouldBe true
      getClaimsResponse.claimsList.head.claimId        shouldBe "test-claim-submitted-2"
    }

    "return 200 when requested unsubmitted claims and user is an organisation" in new AuthorisedOrganisationFixture {

      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val result = controller.getClaims()(requestGetClaimsUnsubmitted)
      status(result) shouldBe Status.OK
      val getClaimsResponse = contentAsJson(result).as[GetClaimsResponse]
      getClaimsResponse.claimsCount                                        shouldBe 3
      getClaimsResponse.claimsList
        .map(claim => (claim.claimId, claim.userId, claim.claimSubmitted)) shouldBe
        Seq(
          ("test-claim-unsubmitted-1", organisation1, false),
          ("test-claim-unsubmitted-2", organisation1, false),
          ("test-claim-unsubmitted-3", organisation1, false)
        )
    }

    "return 200 when requested unsubmitted claims and user is an agent" in new AuthorisedAgentFixture {

      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction, claimsService)

      val result = controller.getClaims()(requestGetClaimsUnsubmitted)
      status(result) shouldBe Status.OK
      val getClaimsResponse = contentAsJson(result).as[GetClaimsResponse]
      getClaimsResponse.claimsCount                                        shouldBe 3
      getClaimsResponse.claimsList
        .map(claim => (claim.claimId, claim.userId, claim.claimSubmitted)) shouldBe
        Seq(
          ("test-claim-unsubmitted-1-2", agent1, false),
          ("test-claim-unsubmitted-2-2", agent1, false),
          ("test-claim-unsubmitted-3-2", agent1, false)
        )
    }
  }

}
