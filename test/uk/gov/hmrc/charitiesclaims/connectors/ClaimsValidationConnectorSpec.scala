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

package uk.gov.hmrc.charitiesclaims.connectors

import com.typesafe.config.ConfigFactory
import org.scalamock.handlers.CallHandler
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.models.{FileUploadReference, GetUploadResultResponse}
import uk.gov.hmrc.charitiesclaims.util.{BaseSpec, HttpV2Support}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.io.Source

class ClaimsValidationConnectorSpec extends BaseSpec with HttpV2Support {

  val config: Configuration = Configuration(
    ConfigFactory.parseString(
      """
        |  microservice {
        |    services {
        |      charities-claims-validation {
        |        protocol = http
        |        host     = foo.bar.com
        |        port     = 1234
        |        retryIntervals = [10ms,50ms]
        |        context-path = "/foo-claims"
        |      }
        |   }
        |}
        |""".stripMargin
    )
  )

  val connector =
    new ClaimsValidationConnectorImpl(
      http = mockHttp,
      servicesConfig = new ServicesConfig(config),
      configuration = config,
      actorSystem = actorSystem
    )

  def givenDeleteReturns(
    expectedUrl: String,
    response: HttpResponse
  ): CallHandler[Future[HttpResponse]] =
    mockHttpDeleteSuccess(expectedUrl)(response)

  def givenDeleteClaimEndpointReturns(response: HttpResponse): CallHandler[Future[HttpResponse]] =
    givenDeleteReturns(
      expectedUrl = "http://foo.bar.com:1234/foo-claims/12345/upload-results",
      response = response
    )

  def givenGetUploadResultEndpointReturns(response: HttpResponse): CallHandler[Future[HttpResponse]] =
    mockHttpGetSuccess(URL("http://foo.bar.com:1234/foo-claims/12345/upload-results/test-ref-123"))(
      response = response
    )

  def readJsonString(path: String): String =
    Source.fromInputStream(this.getClass.getResourceAsStream(path)).getLines().mkString("\n")

  lazy val testGetUploadResultValidatedGiftAid: String =
    readJsonString("/test-get-upload-result-validated-gift-aid.json")

  lazy val testGetUploadResultValidatedOtherIncome: String =
    readJsonString("/test-get-upload-result-validated-other-income.json")

  lazy val testGetUploadResultValidatedCommunityBuildings: String =
    readJsonString("/test-get-upload-result-validated-community-buildings.json")

  lazy val testGetUploadResultValidatedConnectedCharities: String =
    readJsonString("/test-get-upload-result-validated-connected-charities.json")

  given HeaderCarrier = HeaderCarrier()

  "ClaimsConnector" - {
    "deleteClaim" - {
      "have retries defined" in {
        connector.retryIntervals shouldBe Seq(FiniteDuration(10, "ms"), FiniteDuration(50, "ms"))
      }

      "should return a list of unsubmitted claims" in {
        givenDeleteClaimEndpointReturns(HttpResponse(200)).once()

        await(connector.deleteClaim("12345")) shouldBe ()
      }

      "throw an exception if the service returns 404 status" in {
        givenDeleteClaimEndpointReturns(HttpResponse(404, "Bad Request")).once()
        a[Exception] should be thrownBy
          await(connector.deleteClaim("12345"))
      }

      "throw an exception if the service returns 500 status" in {
        givenDeleteClaimEndpointReturns(HttpResponse(500, "")).once()
        a[Exception] should be thrownBy
          await(connector.deleteClaim("12345"))
      }

      "throw exception when 5xx response status in the third attempt" in {
        givenDeleteClaimEndpointReturns(HttpResponse(500, "")).once()
        givenDeleteClaimEndpointReturns(HttpResponse(499, "")).once()
        givenDeleteClaimEndpointReturns(HttpResponse(469, "")).once()

        a[Exception] shouldBe thrownBy {
          await(connector.deleteClaim("12345"))
        }
      }

      "accept valid response in a second attempt" in {
        givenDeleteClaimEndpointReturns(HttpResponse(500, "")).once()
        givenDeleteClaimEndpointReturns(HttpResponse(200)).once()
        await(connector.deleteClaim("12345")) shouldBe ()
      }

      "accept valid response in a third attempt" in {
        givenDeleteClaimEndpointReturns(HttpResponse(499, "")).once()
        givenDeleteClaimEndpointReturns(HttpResponse(500, "")).once()
        givenDeleteClaimEndpointReturns(HttpResponse(200)).once()
        await(connector.deleteClaim("12345")) shouldBe ()
      }
    }

    "getUploadResult" - {
      "should return GetUploadResultValidatedGiftAid if the service returns 200 status and the type is GiftAid" in {
        val expectedResponse = Json.parse(testGetUploadResultValidatedGiftAid).as[GetUploadResultResponse]
        givenGetUploadResultEndpointReturns(HttpResponse(200, body = testGetUploadResultValidatedGiftAid)).once()
        await(connector.getUploadResult("12345", FileUploadReference("test-ref-123"))) shouldBe Some(expectedResponse)
      }

      "should return GetUploadResultValidatedOtherIncome if the service returns 200 status and the type is OtherIncome" in {
        val expectedResponse = Json.parse(testGetUploadResultValidatedOtherIncome).as[GetUploadResultResponse]
        givenGetUploadResultEndpointReturns(HttpResponse(200, body = testGetUploadResultValidatedOtherIncome)).once()
        await(connector.getUploadResult("12345", FileUploadReference("test-ref-123"))) shouldBe Some(expectedResponse)
      }

      "should return GetUploadResultValidatedCommunityBuildings if the service returns 200 status and the type is CommunityBuildings" in {
        val expectedResponse = Json.parse(testGetUploadResultValidatedCommunityBuildings).as[GetUploadResultResponse]
        givenGetUploadResultEndpointReturns(HttpResponse(200, body = testGetUploadResultValidatedCommunityBuildings))
          .once()
        await(connector.getUploadResult("12345", FileUploadReference("test-ref-123"))) shouldBe Some(expectedResponse)
      }

      "should return GetUploadResultValidatedConnectedCharities if the service returns 200 status and the type is ConnectedCharities" in {
        val expectedResponse = Json.parse(testGetUploadResultValidatedConnectedCharities).as[GetUploadResultResponse]
        givenGetUploadResultEndpointReturns(HttpResponse(200, body = testGetUploadResultValidatedConnectedCharities))
          .once()
        await(connector.getUploadResult("12345", FileUploadReference("test-ref-123"))) shouldBe Some(expectedResponse)
      }

      "should return None if the service returns 404 status" in {
        givenGetUploadResultEndpointReturns(HttpResponse(404, "{}")).once()
        await(connector.getUploadResult("12345", FileUploadReference("test-ref-123"))) shouldBe None
      }

      "should return None if the service returns 500 status" in {
        givenGetUploadResultEndpointReturns(HttpResponse(500)).once()
        a[Exception] should be thrownBy
          await(connector.getUploadResult("12345", FileUploadReference("test-ref-123")))
      }

      "throw exception when 5xx response status in the third attempt" in {
        givenGetUploadResultEndpointReturns(HttpResponse(500, "")).once()
        givenGetUploadResultEndpointReturns(HttpResponse(499, "")).once()
        givenGetUploadResultEndpointReturns(HttpResponse(469, "")).once()

        a[Exception] shouldBe thrownBy {
          await(connector.getUploadResult("12345", FileUploadReference("test-ref-123")))
        }
      }

      "accept valid response in a second attempt" in {
        val expectedResponse = Json.parse(testGetUploadResultValidatedConnectedCharities).as[GetUploadResultResponse]
        givenGetUploadResultEndpointReturns(HttpResponse(500, "")).once()
        givenGetUploadResultEndpointReturns(HttpResponse(200, testGetUploadResultValidatedConnectedCharities)).once()
        await(connector.getUploadResult("12345", FileUploadReference("test-ref-123"))) shouldBe Some(expectedResponse)
      }

      "accept valid response in a third attempt" in {
        val expectedResponse = Json.parse(testGetUploadResultValidatedConnectedCharities).as[GetUploadResultResponse]
        givenGetUploadResultEndpointReturns(HttpResponse(499, "")).once()
        givenGetUploadResultEndpointReturns(HttpResponse(500, "")).once()
        givenGetUploadResultEndpointReturns(HttpResponse(200, testGetUploadResultValidatedConnectedCharities)).once()
        await(connector.getUploadResult("12345", FileUploadReference("test-ref-123"))) shouldBe Some(expectedResponse)
      }
    }
  }

}
