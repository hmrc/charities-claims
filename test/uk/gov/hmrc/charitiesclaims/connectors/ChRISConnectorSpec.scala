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
import play.api.Configuration
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.util.{BaseSpec, ChRISTestData, HttpV2Support}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

class ChRISConnectorSpec extends BaseSpec with HttpV2Support {

  val config: Configuration = Configuration(
    ConfigFactory.parseString(
      """
        |  microservice {
        |    services {
        |      chris {
        |        baseUrl = "http://foo.bar.com:1234/foo"
        |        path = "/chris/receiver"
        |        retryIntervals = [10ms,50ms]
        |      }
        |    }
        |  }
        |""".stripMargin
    )
  )

  val connector =
    new ChRISConnectorImpl(
      http = mockHttp,
      servicesConfig = new ServicesConfig(config),
      configuration = config,
      actorSystem = actorSystem
    )

  def givenChRISReturns(response: HttpResponse, requestBody: String) =
    mockHttpPost(URL("http://foo.bar.com:1234/foo/chris/receiver")).once()
    mockRequestBuilderWithString(requestBody).once()
    mockRequestBuilderExecuteWithoutException(response).once()

  given HeaderCarrier = HeaderCarrier()

  "ChRISConnector" - {

    "have retries defined" in {
      connector.retryIntervals shouldBe Seq(FiniteDuration(10, "ms"), FiniteDuration(50, "ms"))
    }

    "submitClaim" - {

      "should return unit if the service returns 200 status" in {
        givenChRISReturns(HttpResponse(200), ChRISTestData.exampleSubmissionXML)
        await(connector.submitClaim(ChRISTestData.exampleMessage)) shouldBe ()
      }

      "throw an exception if the service returns 500 status" in {
        givenChRISReturns(HttpResponse(500), ChRISTestData.exampleSubmissionXML)
        givenChRISReturns(HttpResponse(500), ChRISTestData.exampleSubmissionXML)
        givenChRISReturns(HttpResponse(500), ChRISTestData.exampleSubmissionXML)
        a[Exception] should be thrownBy
          await(connector.submitClaim(ChRISTestData.exampleMessage))
      }

      "throw exception when 5xx response status in the third attempt" in {
        givenChRISReturns(HttpResponse(500), ChRISTestData.exampleSubmissionXML)
        givenChRISReturns(HttpResponse(499), ChRISTestData.exampleSubmissionXML)
        givenChRISReturns(HttpResponse(469), ChRISTestData.exampleSubmissionXML)

        a[Exception] shouldBe thrownBy {
          await(connector.submitClaim(ChRISTestData.exampleMessage))
        }
      }

      "accept valid response in a second attempt" in {
        givenChRISReturns(HttpResponse(500), ChRISTestData.exampleSubmissionXML)
        givenChRISReturns(HttpResponse(200), ChRISTestData.exampleSubmissionXML)
        await(connector.submitClaim(ChRISTestData.exampleMessage)) shouldBe ()
      }

      "accept valid response in a third attempt" in {
        givenChRISReturns(HttpResponse(499), ChRISTestData.exampleSubmissionXML)
        givenChRISReturns(HttpResponse(500), ChRISTestData.exampleSubmissionXML)
        givenChRISReturns(HttpResponse(200), ChRISTestData.exampleSubmissionXML)
        await(connector.submitClaim(ChRISTestData.exampleMessage)) shouldBe ()
      }
    }

  }

}
