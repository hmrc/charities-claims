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

import java.io.IOException
import java.net.{ConnectException, SocketTimeoutException, URL, UnknownHostException}
import javax.net.ssl.SSLHandshakeException
import scala.concurrent.ExecutionContext.Implicits.global

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
        |  http-verbs.retries.intervals = [10ms,50ms]
        |""".stripMargin
    )
  )

  val connector =
    new ChRISConnectorImpl(
      http = mockHttp,
      servicesConfig = new ServicesConfig(config),
      config = config,
      actorSystem = actorSystem
    )

  def givenChRISReturns(response: HttpResponse, requestBody: String) =
    mockHttpPost(URL("http://foo.bar.com:1234/foo/chris/receiver")).once()
    mockRequestBuilderWithString(requestBody).once()
    mockRequestBuilderExecuteWithoutException(response).once()

  given HeaderCarrier = HeaderCarrier()

  "ChRISConnector" - {

    "submitClaim" - {

      "should return unit if the service returns 200 status" in {
        givenChRISReturns(HttpResponse(200), ChRISTestData.exampleSubmissionXML)
        await(connector.submitClaim(ChRISTestData.exampleMessage)) shouldBe ()
      }

      "fail without retrying when ChRIS returns a 5xx status" in {
        givenChRISReturns(HttpResponse(500), ChRISTestData.exampleSubmissionXML)
        a[ChRISSubmissionRejected] should be thrownBy
          await(connector.submitClaim(ChRISTestData.exampleMessage))
      }

      "fail without retrying when ChRIS returns a 4xx status" in {
        givenChRISReturns(HttpResponse(499), ChRISTestData.exampleSubmissionXML)
        a[ChRISSubmissionRejected] should be thrownBy
          await(connector.submitClaim(ChRISTestData.exampleMessage))
      }
    }

    "neverReachedChRIS" - {

      "allow a retry only when the request never left us" in {
        connector.neverReachedChRIS(ConnectException("refused"))                  shouldBe true
        connector.neverReachedChRIS(UnknownHostException("chris.ws.hmrc.gov.uk")) shouldBe true
        connector.neverReachedChRIS(SSLHandshakeException("handshake"))           shouldBe true
      }

      "refuse a retry for other failures, where ChRIS may already have filed the claim" in {
        connector.neverReachedChRIS(SocketTimeoutException("read timed out")) shouldBe false
        connector.neverReachedChRIS(IOException("connection reset"))          shouldBe false
        connector.neverReachedChRIS(ChRISSubmissionRejected(500, "boom"))     shouldBe false
      }

      "fail closed for unrecognised exceptions" in {
        connector.neverReachedChRIS(RuntimeException("something new")) shouldBe false
        connector.neverReachedChRIS(null)                              shouldBe false
      }

      "unwrap causes, since the HTTP client wraps transport failures" in {
        connector.neverReachedChRIS(
          RuntimeException("wrapper", ConnectException("refused"))
        ) shouldBe true
      }

      "terminate on a cyclic cause chain" in {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        connector.neverReachedChRIS(a) shouldBe false
      }
    }

  }

}
