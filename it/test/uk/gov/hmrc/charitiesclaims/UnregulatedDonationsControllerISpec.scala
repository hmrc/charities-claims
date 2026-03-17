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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlEqualTo}
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

class UnregulatedDonationsControllerISpec
    extends AnyWordSpec
    with IntegrationTestSupport
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with IntegrationPatience {

  private val authHeader = "Authorization" -> "Bearer test-token"

  "GET /charities/:charityReference/unregulated-donations" should {

    "return total unregulated donations when found" in {

      authorisedOrganisation()
      wireMockServer.stubFor(
        get(urlEqualTo(s"/formp-proxy/charities/char123/unregulated-donations"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                Json.obj(
                  "unregulatedDonationsTotal" -> 1000
                ).toString()
              )
          )
      )
      val response =
        httpClient
          .get(url"$baseUrl/charities/char123/unregulated-donations")(using HeaderCarrier())
          .setHeader(authHeader)
          .execute[HttpResponse]
          .futureValue

      response.status.shouldBe(OK)
      val json = Json.parse(response.body)

      (json \ "unregulatedDonationsTotal").as[BigDecimal] shouldBe 1000
    }

    "return 500 when formp-proxy fails" in {

      authorisedOrganisation()

      wireMockServer.stubFor(
        get(urlEqualTo(s"/formp-proxy/charities/char123/unregulated-donations"))
          .willReturn(aResponse().withStatus(500))
      )

      val response =
        httpClient
          .get(url"$baseUrl/charities/char123/unregulated-donations")(using HeaderCarrier())
          .setHeader(authHeader)
          .execute[HttpResponse]
          .futureValue

      response.status shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
