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
import uk.gov.hmrc.charitiesclaims.util.TestClaimsService
import scala.concurrent.ExecutionContext.Implicits.global

class ClaimsServiceSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite {

  private val realMongoDBClaimsService = app.injector.instanceOf[ClaimsService]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .build()

  val claims = Json
    .parse(this.getClass.getResourceAsStream("/get-claims-response.json"))
    .as[GetClaimsResponse]
    .claimsList

  Seq(
    (realMongoDBClaimsService, "DefaultClaimsService"),
    (new TestClaimsService(Seq.empty), "TestClaimsService")
  )
    .foreach { (claimsService, description) =>
      "ClaimsService" should {
        s"store, retrieve, list and delete claims when using $description" in {
          // create and store a submitted claim for the first user
          val claim = claims.head.copy(claimId = UUID.randomUUID().toString)

          claim.claimSubmitted shouldBe true

          val claimJson = Json.toJson(claim)(using Claim.format)

          claimsService.putClaim(claim).futureValue

          // check the claim can be retrieved and listed
          claimsService.getClaim(claim.claimId).futureValue shouldBe Some(claim)
          claimsService.getClaim(claim.claimId).futureValue shouldBe Some(claim)

          claimsService.listClaims(claim.userId, claimSubmitted = true).futureValue  shouldBe Seq(claimJson)
          claimsService.listClaims(claim.userId, claimSubmitted = false).futureValue shouldBe Seq.empty

          // add a new submitted claim for the second user
          val claim2     = claim.copy(userId = UUID.randomUUID().toString)
          val claimJson2 = Json.toJson(claim2)(using Claim.format)

          claimsService.putClaim(claim2).futureValue

          // check the second claim can be retrieved and listed
          claimsService.listClaims(claim2.userId, claimSubmitted = true).futureValue  shouldBe Seq(claimJson2)
          claimsService.listClaims(claim2.userId, claimSubmitted = false).futureValue shouldBe Seq.empty

          // add the second submitted claim for the second user
          val claim3     = claim.copy(claimId = UUID.randomUUID().toString, userId = claim2.userId)
          val claimJson3 = Json.toJson(claim3)(using Claim.format)
          claimsService.putClaim(claim3).futureValue

          // check both claims can be retrieved and listed
          claimsService.listClaims(claim3.userId, claimSubmitted = true).futureValue  shouldBe Seq(
            claimJson2,
            claimJson3
          )
          claimsService.listClaims(claim3.userId, claimSubmitted = false).futureValue shouldBe Seq.empty

          // add a new unsubmitted claim for the second user
          val claim4     = claim3.copy(claimId = UUID.randomUUID().toString, claimSubmitted = false)
          val claimJson4 = Json.toJson(claim4)(using Claim.format)
          claimsService.putClaim(claim4).futureValue

          // check claims returned are only the submitted or unsubmitted claim
          claimsService.listClaims(claim4.userId, claimSubmitted = true).futureValue  shouldBe Seq(
            claimJson2,
            claimJson3
          )
          claimsService.listClaims(claim4.userId, claimSubmitted = false).futureValue shouldBe Seq(claimJson4)

          // delete the claims
          claimsService.deleteClaim(claim.claimId).futureValue
          claimsService.getClaim(claim.claimId).futureValue shouldBe None

          claimsService.deleteClaim(claim3.claimId).futureValue
          claimsService.getClaim(claim3.claimId).futureValue shouldBe None

          claimsService.deleteClaim(claim4.claimId).futureValue
          claimsService.getClaim(claim4.claimId).futureValue shouldBe None
        }
      }
    }
}
