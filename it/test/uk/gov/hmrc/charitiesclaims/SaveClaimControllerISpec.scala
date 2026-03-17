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
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.helpers.IntegrationTestSupport
import uk.gov.hmrc.charitiesclaims.models.SaveClaimRequest
import uk.gov.hmrc.charitiesclaims.repositories.ClaimsRepository
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import scala.concurrent.ExecutionContext.Implicits.global

class SaveClaimControllerISpec
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
  private val authHeader          = "Authorization" -> "Bearer test-token"
  private val requestBody         =
    Json.toJson(
      SaveClaimRequest(
        claimingGiftAid = true,
        claimingTaxDeducted = true,
        claimingUnderGiftAidSmallDonationsScheme = false,
        claimReferenceNumber = Some("ClaimRef123"),
        claimingDonationsNotFromCommunityBuilding = Some(false),
        claimingDonationsCollectedInCommunityBuildings = Some(false),
        connectedToAnyOtherCharities = Some(false),
        makingAdjustmentToPreviousClaim = Some(false)
      )
    )

  private def postClaim(): HttpResponse =
    httpClient
      .post(url"$baseUrl/claims")(using HeaderCarrier())
      .setHeader(authHeader)
      .withBody(requestBody)
      .execute[HttpResponse]
      .futureValue

  "POST /claims" should {

    "create a claim for an organisation" in {

      authorisedOrganisation()
      val response = postClaim()

      response.status.shouldBe(OK)

      val json = Json.parse(response.body)
      (json \ "claimId").as[String]              should not be empty
      (json \ "lastUpdatedReference").as[String] should not be empty

      val claims = await(repository.collection.countDocuments().toFuture())

      claims shouldBe 1
    }
    "return BAD_REQUEST if organisation already has an unsubmitted claim" in {

      authorisedOrganisation()
      postClaim()

      val response = postClaim()
      response.status shouldBe BAD_REQUEST
    }

    "allow agent to create claim when under limit" in {
      authorisedAgent()

      val response = postClaim()
      response.status shouldBe OK
    }

    "return BAD_REQUEST when agent exceeds claim limit" in {
      authorisedAgent()
      val limit = app.injector.instanceOf[uk.gov.hmrc.charitiesclaims.config.AppConfig].agentUnsubmittedClaimLimit
      (1 to limit).foreach(_ => postClaim())

      val response = postClaim()
      response.status shouldBe BAD_REQUEST
    }
  }
}
