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

class FormpProxyConnectorSpec extends BaseSpec with HttpV2Support {

  val config: Configuration = Configuration(
    ConfigFactory.parseString(
      """
        |  microservice {
        |    services {
        |      formp-proxy {
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
    new FormpProxyConnectorImpl(
      http = mockHttp,
      servicesConfig = new ServicesConfig(config),
      configuration = config,
      actorSystem = actorSystem
    )

  def givenGetTotalUnregulatedDonationsReturns(response: HttpResponse): CallHandler[Future[HttpResponse]] =
    mockHttpGetSuccess(URL("http://foo.bar.com:1234/foo-proxy/charities/abc-123/unregulated-donations"))(response)

  def givenSaveUnregulatedDonationReturns(response: HttpResponse): CallHandler[Future[HttpResponse]] =
    mockHttpPostSuccess(
      "http://foo.bar.com:1234/foo-proxy/charities/abc-123/unregulated-donations",
      Json.obj("claimId" -> 12345, "amount" -> 123.45)
    )(response)

  given HeaderCarrier = HeaderCarrier()

  "FormpProxyConnector" - {

    "have retries defined" in {
      connector.retryIntervals shouldBe Seq(FiniteDuration(10, "ms"), FiniteDuration(50, "ms"))
    }

    "getTotalUnregulatedDonations" - {

      "should return None if the service returns 404 status" in {
        givenGetTotalUnregulatedDonationsReturns(HttpResponse(404))
        await(connector.getTotalUnregulatedDonations("abc-123")) shouldBe None
      }

      "should return the total of all the unregulated donations for the given charity reference" in {
        givenGetTotalUnregulatedDonationsReturns(
          HttpResponse(200, body = Json.obj("unregulatedDonationsTotal" -> 6543).toString())
        )
        await(connector.getTotalUnregulatedDonations("abc-123")) shouldBe Some(6543)
      }

      "throw an exception if the service returns 500 status" in {
        givenGetTotalUnregulatedDonationsReturns(HttpResponse(500))
        a[Exception] should be thrownBy
          await(connector.getTotalUnregulatedDonations("abc-123"))
      }

      "throw exception when 5xx response status in the third attempt" in {
        givenGetTotalUnregulatedDonationsReturns(HttpResponse(500))
        givenGetTotalUnregulatedDonationsReturns(HttpResponse(499))
        givenGetTotalUnregulatedDonationsReturns(HttpResponse(469))

        a[Exception] shouldBe thrownBy {
          await(connector.getTotalUnregulatedDonations("abc-123"))
        }
      }

      "accept valid response in a second attempt" in {
        givenGetTotalUnregulatedDonationsReturns(HttpResponse(500))
        givenGetTotalUnregulatedDonationsReturns(
          HttpResponse(200, body = Json.obj("unregulatedDonationsTotal" -> 123.45).toString())
        )
        await(connector.getTotalUnregulatedDonations("abc-123")) shouldBe Some(123.45)
      }

      "accept valid response in a third attempt" in {
        givenGetTotalUnregulatedDonationsReturns(HttpResponse(499))
        givenGetTotalUnregulatedDonationsReturns(HttpResponse(500))
        givenGetTotalUnregulatedDonationsReturns(
          HttpResponse(200, body = Json.obj("unregulatedDonationsTotal" -> 0.05).toString())
        )
        await(connector.getTotalUnregulatedDonations("abc-123")) shouldBe Some(0.05)
      }
    }

    "saveUnregulatedDonation" - {
      "should save the unregulated donation for the given charity reference" in {
        givenSaveUnregulatedDonationReturns(HttpResponse(200))
        await(connector.saveUnregulatedDonation("abc-123", 12345, 123.45)) shouldBe ()
      }

      "throw an exception if the service returns 500 status" in {
        givenSaveUnregulatedDonationReturns(HttpResponse(500))
        a[Exception] should be thrownBy
          await(connector.saveUnregulatedDonation("abc-123", 12345, 123.45))
      }

      "throw an exception if the service returns 404 status" in {
        givenSaveUnregulatedDonationReturns(HttpResponse(404))
        a[Exception] should be thrownBy
          await(connector.saveUnregulatedDonation("abc-123", 12345, 123.45))
      }

      "accept valid response in a second attempt" in {
        givenSaveUnregulatedDonationReturns(HttpResponse(500))
        givenSaveUnregulatedDonationReturns(HttpResponse(200))
        await(connector.saveUnregulatedDonation("abc-123", 12345, 123.45)) shouldBe ()
      }

      "accept valid response in a third attempt" in {
        givenSaveUnregulatedDonationReturns(HttpResponse(499))
        givenSaveUnregulatedDonationReturns(HttpResponse(500))
        givenSaveUnregulatedDonationReturns(HttpResponse(200))
        await(connector.saveUnregulatedDonation("abc-123", 12345, 123.45)) shouldBe ()
      }

      "throw exception when 5xx response status in the third attempt" in {
        givenSaveUnregulatedDonationReturns(HttpResponse(500))
        givenSaveUnregulatedDonationReturns(HttpResponse(499))
        givenSaveUnregulatedDonationReturns(HttpResponse(469))

        a[Exception] shouldBe thrownBy {
          await(connector.saveUnregulatedDonation("abc-123", 12345, 123.45))
        }
      }
    }
  }

}
