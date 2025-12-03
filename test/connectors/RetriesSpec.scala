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
import uk.gov.hmrc.charitiesclaims.util.BaseSpec
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class RetriesSpec extends BaseSpec {

  val retries = new Retries {
    val actorSystem = RetriesSpec.this.actorSystem
  }

  val config: Configuration = Configuration(
    ConfigFactory.parseString(
      """
        |  microservice {
        |    services {
        |     foo {
        |        retryIntervals = [10ms,200ms,1s]
        |      }
        |   }
        |}
        |""".stripMargin
    )
  )

  "Retries" - {
    "retry" - {
      "should return response first time" in {
        await(
          retries.retry(10.microseconds)(retries.shouldRetry, retries.retryReason)(
            Future.successful(HttpResponse(200, ""))
          )
        ).status shouldBe 200
      }
      "should return response at second attempt" in {
        val responses = Iterator(
          HttpResponse(500, ""),
          HttpResponse(200, "")
        )
        await(
          retries.retry(10.microseconds)(retries.shouldRetry, retries.retryReason)(
            Future.successful(responses.next())
          )
        ).status shouldBe 200
      }
      "should return response at second attempt if future fails" in {
        val responses = Iterator(
          Future.failed(Exception("test")),
          Future.successful(HttpResponse(200, ""))
        )
        await(
          retries.retry(10.microseconds)(retries.shouldRetry, retries.retryReason)(responses.next())
        ).status shouldBe 200
      }
      "should return response at third attempt" in {
        val responses = Iterator(
          HttpResponse(499, ""),
          HttpResponse(500, ""),
          HttpResponse(200, "")
        )
        await(
          retries.retry(10.milliseconds, 200.milliseconds)(retries.shouldRetry, retries.retryReason)(
            Future.successful(responses.next())
          )
        ).status shouldBe 200
      }
      "should return response at fourth attempt" in {
        val responses = Iterator(
          HttpResponse(499, ""),
          HttpResponse(500, ""),
          HttpResponse(469, ""),
          HttpResponse(200, "")
        )
        await(
          retries.retry(10.milliseconds, 200.milliseconds, 15.milliseconds)(retries.shouldRetry, retries.retryReason)(
            Future.successful(responses.next())
          )
        ).status shouldBe 200
      }
      "should return last response if the limit of retries is reached" in {
        val responses = Iterator(
          HttpResponse(499, ""),
          HttpResponse(500, ""),
          HttpResponse(469, ""),
          HttpResponse(500, "")
        )
        await(
          retries.retry(10.milliseconds, 200.milliseconds, 15.milliseconds)(retries.shouldRetry, retries.retryReason)(
            Future.successful(responses.next())
          )
        ).status shouldBe 500
      }
      "should return last exception if the limit of retries is reached" in {
        val responses = Iterator(
          Future.failed(Exception("test 1")),
          Future.failed(Exception("test 2")),
          Future.failed(Exception("test 3")),
          Future.failed(Exception("test 4"))
        )
        a[Exception] should be thrownBy {
          await(
            retries.retry(10.milliseconds, 200.milliseconds, 15.milliseconds)(retries.shouldRetry, retries.retryReason)(
              responses.next()
            )
          )
        }
      }
    }
    "should return an empty sequence if the retry intervals are not defined" in {
      Retries.getConfIntervals("bar", config) shouldBe Seq.empty
    }
    "should return the retry intervals" in {
      Retries.getConfIntervals("foo", config) shouldBe Seq(10.milliseconds, 200.milliseconds, 1.second)
    }
    "retry reason" - {
      "should return the retry reason" in {
        retries.retryReason(HttpResponse(500, "")) shouldBe "received HttpResponse status=500"
      }
    }
  }

  "Retries.getConfIntervals" - {
    "should return the retry intervals" in {
      Retries.getConfIntervals("foo", config) shouldBe Seq(10.milliseconds, 200.milliseconds, 1.second)
    }
    "should return an empty sequence if the retry intervals are not defined" in {
      Retries.getConfIntervals("bar", config) shouldBe Seq.empty
    }
  }
}
