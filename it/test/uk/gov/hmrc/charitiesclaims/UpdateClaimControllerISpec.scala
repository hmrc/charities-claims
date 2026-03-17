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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, delete, urlEqualTo}
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.bson.BsonDocument
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.helpers.IntegrationTestSupport
import uk.gov.hmrc.charitiesclaims.models.{Claim, ClaimData, RepaymentClaimDetails, SaveClaimRequest, UpdateClaimRequest, UpdateClaimResponse}
import uk.gov.hmrc.charitiesclaims.repositories.ClaimsRepository
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import scala.concurrent.ExecutionContext.Implicits.global

class UpdateClaimControllerISpec
    extends AnyWordSpec
    with IntegrationTestSupport
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with IntegrationPatience {

  val claimsRepository: ClaimsRepository = app.injector.instanceOf[ClaimsRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(claimsRepository.collection.deleteMany(BsonDocument()).toFuture())
  }
  private val authHeader = "Authorization" -> "Bearer test-token"

  private val claimId             = "claim-1"
  private val initialClaim: Claim =
    Claim(
      claimId = claimId,
      userId = "test-user",
      claimSubmitted = false,
      lastUpdatedReference = "ref-1",
      claimData = ClaimData(
        repaymentClaimDetails = RepaymentClaimDetails(true, true, false)
      )
    )

  private val updateRequest =
    UpdateClaimRequest(
      repaymentClaimDetails = RepaymentClaimDetails(true, true, false),
      organisationDetails = None,
      giftAidSmallDonationsSchemeDonationDetails = None,
      understandFalseStatements = None,
      includedAnyAdjustmentsInClaimPrompt = None,
      giftAidScheduleFileUploadReference = None,
      otherIncomeScheduleFileUploadReference = None,
      communityBuildingsScheduleFileUploadReference = None,
      connectedCharitiesScheduleFileUploadReference = None,
      adjustmentForOtherIncomePreviousOverClaimed = None,
      prevOverclaimedGiftAid = None,
      lastUpdatedReference = "ref-1"
    )

  "PUT /claims/:claimId" should {
    "update claim successfully" in {
      authorisedOrganisation()
      await(repository.put(claimId)(ClaimsRepository.claimDataKey, initialClaim))

      wireMockServer.stubFor(
        delete(urlEqualTo(s"/charities-claims-validation/$claimId/upload-results"))
          .willReturn(aResponse().withStatus(200))
      )

      val response =
        httpClient
          .put(url"$baseUrl/claims/$claimId")(using HeaderCarrier())
          .setHeader(authHeader)
          .withBody(Json.toJson(updateRequest))
          .execute[HttpResponse]
          .futureValue

      response.status shouldBe OK
      val resBody = Json.parse(response.body).as[UpdateClaimResponse]
      resBody.success            shouldBe true
      resBody.lastUpdatedReference should not be updateRequest.lastUpdatedReference
    }
  }
}
