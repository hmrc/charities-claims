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

package uk.gov.hmrc.charitiesclaims

import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.bson.BsonDocument
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, Json}
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.helpers.IntegrationTestSupport
import uk.gov.hmrc.charitiesclaims.models.{Claim, ClaimData, RepaymentClaimDetails}
import uk.gov.hmrc.charitiesclaims.repositories.ClaimsRepository
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import scala.concurrent.ExecutionContext.Implicits.global

class GetClaimsControllerISpec
    extends AnyWordSpec
    with IntegrationTestSupport
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with IntegrationPatience {

  private val claimsRepository: ClaimsRepository = app.injector.instanceOf[ClaimsRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(claimsRepository.collection.deleteMany(BsonDocument()).toFuture())
  }

  val userId             = "test-user"
  private val authHeader = "Authorization" -> "Bearer test-token"

  def insertClaim(claimId: String, submitted: Boolean): Unit = {

    val claim =
      Claim(
        claimId = claimId,
        userId = userId,
        claimSubmitted = submitted,
        lastUpdatedReference = "",
        claimData = ClaimData(
          repaymentClaimDetails = RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = true,
            claimingUnderGiftAidSmallDonationsScheme = false
          )
        )
      )

    await(
      claimsRepository.put(claim.claimId)(
        ClaimsRepository.claimDataKey,
        claim
      )
    )
  }

  "GET /claims" should {

    "return claims for the authenticated user" in {

      insertClaim("claim-1", false)
      authorisedOrganisation()

      val response =
        httpClient
          .get(url"$baseUrl/claims")(using HeaderCarrier())
          .setHeader(authHeader)
          .execute[HttpResponse]
          .futureValue

      response.status.shouldBe(OK)

      val json = Json.parse(response.body)
      (json \ "claimsCount").as[Int].shouldBe(1)
      val claims = (json \ "claimsList").as[JsArray]
      (claims(0) \ "claimId").as[String].shouldBe("claim-1")
    }

    "return empty list when user has no claims" in {

      authorisedOrganisation()

      val response =
        httpClient
          .get(url"$baseUrl/claims")(using HeaderCarrier())
          .setHeader(authHeader)
          .execute[HttpResponse]
          .futureValue

      response.status shouldBe OK
      val json = Json.parse(response.body)
      (json \ "claimsCount").as[Int] shouldBe 0
    }
  }

  "GET /claims/:id" should {
    "return claim for the claim Id" in {
      insertClaim("claim-1", false)
      authorisedOrganisation()

      val response =
        httpClient
          .get(url"$baseUrl/claims/claim-1")(using HeaderCarrier())
          .setHeader(authHeader)
          .execute[HttpResponse]
          .futureValue

      response.status shouldBe OK
    }
  }
}
