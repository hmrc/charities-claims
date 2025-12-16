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

import uk.gov.hmrc.charitiesclaims.util.ControllerSpec
import play.api.test.Helpers
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.connectors.FormpProxyConnector
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.http.Status
import play.api.libs.json.Json

class UnregulatedDonationsControllerSpec extends ControllerSpec {

  "GET /charities/:charityReference/unregulated-donations" - {
    "return 200 when unregulated donations are found" in new AuthorisedOrganisationFixture {

      val formpProxyConnector = mock[FormpProxyConnector]
      (formpProxyConnector
        .getTotalUnregulatedDonations(_: String)(using _: HeaderCarrier))
        .expects("1234567890", *)
        .anyNumberOfTimes()
        .returns(Future.successful(Some(100.1)))

      val controller =
        new UnregulatedDonationsController(Helpers.stubControllerComponents(), authorisedAction, formpProxyConnector)

      val request = testRequest("GET", "/charities/1234567890/unregulated-donations")

      val result = controller.getTotalUnregulatedDonations("1234567890")(request)
      status(result)        shouldBe Status.OK
      contentAsJson(result) shouldBe Json.obj("unregulatedDonationsTotal" -> 100.1)
    }

    "return 404 when no unregulated donations are found" in new AuthorisedOrganisationFixture {
      val formpProxyConnector = mock[FormpProxyConnector]
      (formpProxyConnector
        .getTotalUnregulatedDonations(_: String)(using _: HeaderCarrier))
        .expects("1234567890", *)
        .anyNumberOfTimes()
        .returns(Future.successful(None))

      val controller =
        new UnregulatedDonationsController(Helpers.stubControllerComponents(), authorisedAction, formpProxyConnector)

      val request = testRequest("GET", "/charities/1234567890/unregulated-donations")

      val result = controller.getTotalUnregulatedDonations("1234567890")(request)
      status(result)        shouldBe Status.NOT_FOUND
      contentAsJson(result) shouldBe Json.obj(
        "errorMessage" -> "No unregulated donations found for the given charity reference 1234567890",
        "errorCode"    -> "NO_UNREGULATED_DONATIONS_FOUND"
      )
    }

    "return 500 when the formp proxy connector returns an error" in new AuthorisedOrganisationFixture {
      val formpProxyConnector = mock[FormpProxyConnector]
      (formpProxyConnector
        .getTotalUnregulatedDonations(_: String)(using _: HeaderCarrier))
        .expects("1234567890", *)
        .anyNumberOfTimes()
        .returns(Future.failed(new Exception("Error message")))

      val controller =
        new UnregulatedDonationsController(Helpers.stubControllerComponents(), authorisedAction, formpProxyConnector)

      val request = testRequest("GET", "/charities/1234567890/unregulated-donations")

      val result = controller.getTotalUnregulatedDonations("1234567890")(request)
      status(result)        shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.obj(
        "errorMessage" -> "Error message",
        "errorCode"    -> "FORMP_PROXY_ERROR"
      )
    }
  }

}
