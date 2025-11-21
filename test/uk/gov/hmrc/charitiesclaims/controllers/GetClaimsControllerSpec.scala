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

class GetClaimsControllerSpec extends ControllerSpec {

  val fakeRequest = FakeRequest("POST", "/get-claims")
    .withJsonBody(Json.toJson(GetClaimsRequest(claimSubmitted = true)))

  "POST /get-claims" - {
    "return 200 when user is an organisation" in new AuthorisedOrganisationFixture {

      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction)

      val result = controller.getClaims()(fakeRequest)
      status(result) mustBe Status.OK
    }

    "return 200 when user is an agent" in new AuthorisedAgentFixture {

      val controller = new GetClaimsController(Helpers.stubControllerComponents(), authorisedAction)

      val result = controller.getClaims()(fakeRequest)
      status(result) mustBe Status.OK
    }
  }

}
