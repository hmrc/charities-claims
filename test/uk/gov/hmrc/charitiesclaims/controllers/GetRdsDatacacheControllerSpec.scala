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
import uk.gov.hmrc.charitiesclaims.connectors.RdsDatacacheProxyConnector
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.http.Status
import play.api.libs.json.Json

class GetRdsDatacacheControllerSpec extends ControllerSpec {

  "GET /charities/agents/:agentReference" - {
    "return 200 when agent name is found" in new AuthorisedOrganisationFixture {

      val rdsDatacacheProxyConnector = mock[RdsDatacacheProxyConnector]
      (rdsDatacacheProxyConnector
        .getAgentName(_: String)(using _: HeaderCarrier))
        .expects("123", *)
        .anyNumberOfTimes()
        .returns(Future.successful(Some("123")))

      val controller =
        new GetRdsDatacacheController(Helpers.stubControllerComponents(), authorisedAction, rdsDatacacheProxyConnector)

      val request = testRequest("GET", "/charities/agents/123")

      val result = controller.getAgentName("123")(request)
      status(result)        shouldBe Status.OK
      contentAsJson(result) shouldBe Json.obj("agentName" -> "123")
    }

    "return 404 when no agent name found for agent ref" in new AuthorisedOrganisationFixture {
      val rdsDatacacheProxyConnector = mock[RdsDatacacheProxyConnector]
      (rdsDatacacheProxyConnector
        .getAgentName(_: String)(using _: HeaderCarrier))
        .expects("123", *)
        .anyNumberOfTimes()
        .returns(Future.successful(None))

      val controller =
        new GetRdsDatacacheController(Helpers.stubControllerComponents(), authorisedAction, rdsDatacacheProxyConnector)

      val request = testRequest("GET", "/charities/agents/123")

      val result = controller.getAgentName("123")(request)
      status(result)        shouldBe Status.NOT_FOUND
      contentAsJson(result) shouldBe Json.obj(
        "errorMessage" -> "No agent name found for the given agent reference 123",
        "errorCode"    -> "NO_AGENT_NAME_FOUND"
      )
    }

    "return 500 when the rds cache proxy connector returns an error" in new AuthorisedOrganisationFixture {
      val rdsDatacacheProxyConnector = mock[RdsDatacacheProxyConnector]
      (rdsDatacacheProxyConnector
        .getAgentName(_: String)(using _: HeaderCarrier))
        .expects("1234567890", *)
        .anyNumberOfTimes()
        .returns(Future.failed(new Exception("Error message")))

      val controller =
        new GetRdsDatacacheController(Helpers.stubControllerComponents(), authorisedAction, rdsDatacacheProxyConnector)

      val request = testRequest("GET", "/charities/agents/1234567890")

      val result = controller.getAgentName("1234567890")(request)
      status(result)        shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.obj(
        "errorMessage" -> "Error message",
        "errorCode"    -> "RDSDATACACHE_PROXY_ERROR"
      )
    }
  }
  "GET /charities/organisations/:charityReference" - {
    "return 200 when Organisation name is found for a charity reference" in new AuthorisedOrganisationFixture {

      val rdsDatacacheProxyConnector = mock[RdsDatacacheProxyConnector]
      (rdsDatacacheProxyConnector
        .getOrganisationName(_: String)(using _: HeaderCarrier))
        .expects("123", *)
        .anyNumberOfTimes()
        .returns(Future.successful(Some("123")))

      val controller =
        new GetRdsDatacacheController(Helpers.stubControllerComponents(), authorisedAction, rdsDatacacheProxyConnector)

      val request = testRequest("GET", "/charities/organisations/123")

      val result = controller.getOrganisationName("123")(request)
      status(result)        shouldBe Status.OK
      contentAsJson(result) shouldBe Json.obj("organisationName" -> "123")
    }

    "return 404 when no organisation name found for charity ref" in new AuthorisedOrganisationFixture {
      val rdsDatacacheProxyConnector = mock[RdsDatacacheProxyConnector]
      (rdsDatacacheProxyConnector
        .getOrganisationName(_: String)(using _: HeaderCarrier))
        .expects("123", *)
        .anyNumberOfTimes()
        .returns(Future.successful(None))

      val controller =
        new GetRdsDatacacheController(Helpers.stubControllerComponents(), authorisedAction, rdsDatacacheProxyConnector)

      val request = testRequest("GET", "/charities/organisations/123")

      val result = controller.getOrganisationName("123")(request)
      status(result)        shouldBe Status.NOT_FOUND
      contentAsJson(result) shouldBe Json.obj(
        "errorMessage" -> "No organisation name found for the given charity reference 123",
        "errorCode"    -> "NO_ORGANISATION_NAME_FOUND"
      )
    }

    "return 500 when the rds cache proxy connector returns an error" in new AuthorisedOrganisationFixture {
      val rdsDatacacheProxyConnector = mock[RdsDatacacheProxyConnector]
      (rdsDatacacheProxyConnector
        .getOrganisationName(_: String)(using _: HeaderCarrier))
        .expects("1234567890", *)
        .anyNumberOfTimes()
        .returns(Future.failed(new Exception("Error message")))

      val controller =
        new GetRdsDatacacheController(Helpers.stubControllerComponents(), authorisedAction, rdsDatacacheProxyConnector)

      val request = testRequest("GET", "/charities/organisations/1234567890")

      val result = controller.getOrganisationName("1234567890")(request)
      status(result)        shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.obj(
        "errorMessage" -> "Error message",
        "errorCode"    -> "RDSDATACACHE_PROXY_ERROR"
      )
    }
  }

}
