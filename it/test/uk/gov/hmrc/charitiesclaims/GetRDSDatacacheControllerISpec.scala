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

package uk.gov.hmrc.charitiesclaims

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.helpers.IntegrationTestSupport
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import scala.concurrent.ExecutionContext.Implicits.global

class GetRDSDatacacheControllerISpec
    extends AnyWordSpec
    with IntegrationTestSupport
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with IntegrationPatience {

  private val authHeader = "Authorization" -> "Bearer test-token"

  private def stubFailure(path: String) =
    wireMockServer.stubFor(
      get(urlEqualTo(path))
        .willReturn(aResponse().withStatus(500))
    )

  "GET /charities/organisations/:charityRef" should {
    val charityRef = "char123"
    def stubOrganisationSuccess(ref: String) =
      wireMockServer.stubFor(
        get(urlEqualTo(s"/rds-datacache-proxy/charities/organisations/$ref"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(Json.obj("organisationName" -> "test charity").toString())
          )
      )

    "return organisation name when found" in {

      authorisedOrganisation()
      stubOrganisationSuccess(charityRef)

      val response = httpClient
        .get(url"$baseUrl/charities/organisations/$charityRef")(using HeaderCarrier())
        .setHeader(authHeader)
        .execute[HttpResponse]
        .futureValue

      response.status                                             shouldBe OK
      (Json.parse(response.body) \ "organisationName").as[String] shouldBe "test charity"
    }

    "return 500 when rds-datacache-proxy fails" in {

      authorisedOrganisation()
      stubFailure(s"/rds-datacache-proxy/charities/organisations/$charityRef")

      val response = httpClient
        .get(url"$baseUrl/charities/organisations/$charityRef")(using HeaderCarrier())
        .setHeader(authHeader)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "GET /charities/agents/:charityRef" should {
    val agentRef   = "charAgent123"
    def stubAgentSuccess(ref: String) =
      wireMockServer.stubFor(
        get(urlEqualTo(s"/rds-datacache-proxy/charities/agents/$ref"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(Json.obj("agentName" -> "test agent charity").toString())
          )
      )
    "return agent name when found" in {

      authorisedOrganisation()
      stubAgentSuccess(agentRef)

      val response = httpClient
        .get(url"$baseUrl/charities/agents/$agentRef")(using HeaderCarrier())
        .setHeader(authHeader)
        .execute[HttpResponse]
        .futureValue

      response.status                                      shouldBe OK
      (Json.parse(response.body) \ "agentName").as[String] shouldBe "test agent charity"
    }

    "return 500 when rds-datacache-proxy fails" in {

      authorisedOrganisation()
      stubFailure(s"/rds-datacache-proxy/charities/agents/$agentRef")

      val response = httpClient
        .get(url"$baseUrl/charities/agents/$agentRef")(using HeaderCarrier())
        .setHeader(authHeader)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
