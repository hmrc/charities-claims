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
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.util.{BaseSpec, HttpV2Support}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import play.api.libs.json.Json
import java.net.URL

class RdsDatacacheProxyConnectorSpec extends BaseSpec with HttpV2Support {

  val config: Configuration = Configuration(
    ConfigFactory.parseString(
      """
        |  microservice {
        |    services {
        |      rds-datacache-proxy {
        |        protocol = http
        |        host     = foo.bar.com
        |        port     = 1234
        |        retryIntervals = [10ms,50ms]
        |        context-path = "/foo-proxy"
        |      }
        |   }
        |}
        |""".stripMargin
    )
  )

  val connector =
    new RdsDatacacheProxyConnectorImpl(
      http = mockHttp,
      servicesConfig = new ServicesConfig(config),
      configuration = config,
      actorSystem = actorSystem
    )

  def givenGetAgentNameByAgentReferenceReturns(response: HttpResponse): CallHandler[Future[HttpResponse]] =
    mockHttpGetSuccess(URL("http://foo.bar.com:1234/foo-proxy/charities/agents/abc-123"))(response)

  def givenGetOrganisationNameByCharityReferenceReturns(response: HttpResponse): CallHandler[Future[HttpResponse]] =
    mockHttpGetSuccess(URL("http://foo.bar.com:1234/foo-proxy/charities/organisations/abc-123"))(response)

  given HeaderCarrier = HeaderCarrier()

  "RdsDatacacheProxyConnector" - {

    "have retries defined" in {
      connector.retryIntervals shouldBe Seq(FiniteDuration(10, "ms"), FiniteDuration(50, "ms"))
    }

    "getAgentName" - {

      "should return None if the service returns 404 status" in {
        givenGetAgentNameByAgentReferenceReturns(HttpResponse(404))
        await(connector.getAgentName("abc-123")) shouldBe None
      }

      "should return Agent name for the given agent reference" in {
        givenGetAgentNameByAgentReferenceReturns(
          HttpResponse(200, body = Json.obj("agentName" -> "AgentABC").toString())
        )
        await(connector.getAgentName("abc-123")) shouldBe Some("AgentABC")
      }

      "throw an exception if the service returns 500 status" in {
        givenGetAgentNameByAgentReferenceReturns(HttpResponse(500))
        a[Exception] should be thrownBy
          await(connector.getAgentName("abc-123"))
      }

      "throw exception when 5xx response status in the third attempt" in {
        givenGetAgentNameByAgentReferenceReturns(HttpResponse(500))
        givenGetAgentNameByAgentReferenceReturns(HttpResponse(499))
        givenGetAgentNameByAgentReferenceReturns(HttpResponse(469))

        a[Exception] shouldBe thrownBy {
          await(connector.getAgentName("abc-123"))
        }
      }

      "accept valid response in a second attempt" in {
        givenGetAgentNameByAgentReferenceReturns(HttpResponse(500))
        givenGetAgentNameByAgentReferenceReturns(
          HttpResponse(200, body = Json.obj("agentName" -> "AgentABC").toString())
        )
        await(connector.getAgentName("abc-123")) shouldBe Some("AgentABC")
      }

      "accept valid response in a third attempt" in {
        givenGetAgentNameByAgentReferenceReturns(HttpResponse(499))
        givenGetAgentNameByAgentReferenceReturns(HttpResponse(500))
        givenGetAgentNameByAgentReferenceReturns(
          HttpResponse(200, body = Json.obj("agentName" -> "AgentABCD").toString())
        )
        await(connector.getAgentName("abc-123")) shouldBe Some("AgentABCD")
      }
    }
    "getOrganisationName" - {

      "should return None if the service returns 404 status" in {
        givenGetOrganisationNameByCharityReferenceReturns(HttpResponse(404))
        await(connector.getOrganisationName("abc-123")) shouldBe None
      }

      "should return the organisation na,e for the given charity reference" in {
        givenGetOrganisationNameByCharityReferenceReturns(
          HttpResponse(200, body = Json.obj("organisationName" -> "OrganisationNameABC").toString())
        )
        await(connector.getOrganisationName("abc-123")) shouldBe Some("OrganisationNameABC")
      }

      "throw an exception if the service returns 500 status" in {
        givenGetOrganisationNameByCharityReferenceReturns(HttpResponse(500))
        a[Exception] should be thrownBy
          await(connector.getOrganisationName("abc-123"))
      }

      "throw exception when 5xx response status in the third attempt" in {
        givenGetOrganisationNameByCharityReferenceReturns(HttpResponse(500))
        givenGetOrganisationNameByCharityReferenceReturns(HttpResponse(499))
        givenGetOrganisationNameByCharityReferenceReturns(HttpResponse(469))

        a[Exception] shouldBe thrownBy {
          await(connector.getOrganisationName("abc-123"))
        }
      }

      "accept valid response in a second attempt" in {
        givenGetOrganisationNameByCharityReferenceReturns(HttpResponse(500))
        givenGetOrganisationNameByCharityReferenceReturns(
          HttpResponse(200, body = Json.obj("organisationName" -> "OrganisationNameABC").toString())
        )
        await(connector.getOrganisationName("abc-123")) shouldBe Some("OrganisationNameABC")
      }

      "accept valid response in a third attempt" in {
        givenGetOrganisationNameByCharityReferenceReturns(HttpResponse(499))
        givenGetOrganisationNameByCharityReferenceReturns(HttpResponse(500))
        givenGetOrganisationNameByCharityReferenceReturns(
          HttpResponse(200, body = Json.obj("organisationName" -> "OrganisationABCD").toString())
        )
        await(connector.getOrganisationName("abc-123")) shouldBe Some("OrganisationABCD")
      }
    }
  }
}
