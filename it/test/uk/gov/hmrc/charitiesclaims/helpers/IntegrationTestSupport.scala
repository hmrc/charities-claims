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

package uk.gov.hmrc.charitiesclaims.helpers

import org.scalatest.{BeforeAndAfterAll, TestSuite}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.charitiesclaims.repositories.ClaimsRepository
import uk.gov.hmrc.charitiesclaims.services.ChRISSubmissionService
import uk.gov.hmrc.charitiesclaims.stubs.{AuthStub, StubChRISSubmissionService}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport

trait IntegrationTestSupport
    extends GuiceOneServerPerSuite
    with WireMockSupport
    with MongoSupport
    with BeforeAndAfterAll
    with AuthStub { self: TestSuite =>

  val repository: ClaimsRepository     = app.injector.instanceOf[ClaimsRepository]
  val httpClient: HttpClientV2         = app.injector.instanceOf[HttpClientV2]

  val baseUrl: String = s"http://localhost:$port/charities-claims"

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> mongoUri,

        "microservice.services.auth.host" -> wireMockHost,
        "microservice.services.auth.port" -> wireMockPort,

        "microservice.services.charities-claims-validation.host" -> wireMockHost,
        "microservice.services.charities-claims-validation.port" -> wireMockPort,

        "microservice.services.formp-proxy.host" -> wireMockHost,
        "microservice.services.formp-proxy.port" -> wireMockPort,

        "microservice.services.rds-datacache-proxy.host" -> wireMockHost,
        "microservice.services.rds-datacache-proxy.port" -> wireMockPort,

        "microservice.services.chris.baseUrl" -> s"http://$wireMockHost:$wireMockPort",
        "microservice.services.chris.path" -> "/submission/ChRIS/Charities/Filing/sync/HMRC-CHAR-CLM",
        "agentUnsubmittedClaimLimit" -> 2
      )
      .overrides(
        bind[MongoComponent].toInstance(mongoComponent),
        bind[ChRISSubmissionService].to[StubChRISSubmissionService]
      )
      .build()

  override def beforeAll(): Unit = {
    super.beforeAll()
    dropDatabase()
  }

  override def afterAll(): Unit = {
    dropDatabase()
    super.afterAll()
  }
}
