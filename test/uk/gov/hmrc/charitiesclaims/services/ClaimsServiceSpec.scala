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

package uk.gov.hmrc.charitiesclaims.services

import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.charitiesclaims.models.Claim

import java.util.UUID
import play.api.libs.json.Json
import uk.gov.hmrc.charitiesclaims.models.GetClaimsResponse

class ClaimsServiceSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite {

  private val claimsService = app.injector.instanceOf[ClaimsService]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .build()

  val claims = Json
    .parse(this.getClass.getResourceAsStream("/get-claims-response.json"))
    .as[GetClaimsResponse]
    .claimsList

  "ClaimsService" should {
    "store, retrieve, list and delete a claim" in {
      val claim     = claims.head
      val claimJson = Json.toJson(claim)(using Claim.format)

      val result = claimsService.putClaim(claim).futureValue
      result.data.value.get("claim")                         shouldBe Some(claimJson)
      result.data.value.get("claim").flatMap(_.asOpt[Claim]) shouldBe Some(claim)

      claimsService.getClaim(claim.claimId).futureValue shouldBe Some(claim)
      claimsService.getClaim(claim.claimId).futureValue shouldBe Some(claim)

      claimsService.listClaims(claim.userId).futureValue shouldBe Seq(claimJson)

      val claim2     = claim.copy(userId = UUID.randomUUID().toString)
      val claimJson2 = Json.toJson(claim2)(using Claim.format)

      val result2 = claimsService.putClaim(claim2).futureValue
      result2.data.value.get("claim")                         shouldBe Some(claimJson2)
      result2.data.value.get("claim").flatMap(_.asOpt[Claim]) shouldBe Some(claim2)

      claimsService.listClaims(claim2.userId).futureValue shouldBe Seq(claimJson2)

      val claim3     = claim.copy(claimId = UUID.randomUUID().toString, userId = claim2.userId)
      val claimJson3 = Json.toJson(claim3)(using Claim.format)
      claimsService.putClaim(claim3).futureValue

      claimsService.listClaims(claim3.userId).futureValue shouldBe Seq(claimJson2, claimJson3)

      claimsService.deleteClaim(claim.claimId).futureValue
      claimsService.getClaim(claim.claimId).futureValue shouldBe None

      claimsService.deleteClaim(claim3.claimId).futureValue
      claimsService.getClaim(claim3.claimId).futureValue shouldBe None
    }
  }
}
