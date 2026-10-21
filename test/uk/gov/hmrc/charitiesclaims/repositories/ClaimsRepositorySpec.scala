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

package uk.gov.hmrc.charitiesclaims.repositories

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.{Application, inject}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.charitiesclaims.connectors.ClaimsValidationConnector
import uk.gov.hmrc.charitiesclaims.models.Claim

import java.util.UUID
import scala.language.postfixOps

class ClaimsRepositorySpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with MockitoSugar {

  // not used by the repository; mocked so the app starts without real HTTP dependencies
  val mockClaimsValidationConnector: ClaimsValidationConnector = mock[ClaimsValidationConnector]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides {
        inject.bind[ClaimsValidationConnector].toInstance(mockClaimsValidationConnector)
      }
      .build()

  private lazy val repository = app.injector.instanceOf[ClaimsRepository]

  val claims: Seq[Claim] =
    Json
      .parse(this.getClass.getResourceAsStream("/get-claims-response.json"))
      .as[JsObject]
      .value("claimsList")
      .as[Seq[Claim]]

  // fetch claim with unique ids so tests never clash
  def newClaim(
    submitted: Boolean,
    userId: String = UUID.randomUUID().toString,
    charitiesRef: String = "XR1234"
  ): Claim = {
    val base = claims.head
    base.copy(
      claimId = UUID.randomUUID().toString,
      userId = userId,
      claimSubmitted = submitted,
      claimData = base.claimData.copy(
        repaymentClaimDetails = base.claimData.repaymentClaimDetails.copy(
          hmrcCharitiesReference = Some(charitiesRef),
          nameOfCharity = Some("Test Charity")
        )
      )
    )
  }

  "hasUnsubmittedClaim" should {
    "return false when the claim is submitted, even with a matching reference" in {
      val claim = newClaim(submitted = true)
      repository.putClaim(claim).futureValue
      repository.hasUnsubmittedClaim(claim.userId, "XR1234").futureValue shouldBe false
    }
  }

  "updateLastVisitedAt" should {
    "complete and keep the claim retrievable" in {
      val claim = newClaim(submitted = false)
      repository.putClaim(claim).futureValue
      repository.updateLastVisitedAt(claim.claimId).futureValue
      repository.getClaimWithCreatedAt(claim.claimId).futureValue.isDefined shouldBe true
    }
  }
}
