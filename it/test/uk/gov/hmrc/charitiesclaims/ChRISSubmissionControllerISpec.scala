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

import com.github.tomakehurst.wiremock.client.WireMock.*
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
import uk.gov.hmrc.charitiesclaims.models.*
import uk.gov.hmrc.charitiesclaims.repositories.ClaimsRepository
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import scala.concurrent.ExecutionContext.Implicits.global

class ChRISSubmissionControllerISpec
    extends AnyWordSpec
    with IntegrationTestSupport
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with IntegrationPatience {

  private val claimsRepository: ClaimsRepository = app.injector.instanceOf[ClaimsRepository]
  private val authHeader                         = "Authorization" -> "Bearer test-token"

  private val claimId = "claim-1"

  private val baseClaim =
    Claim(
      claimId = claimId,
      userId = "user-123",
      claimSubmitted = false,
      lastUpdatedReference = "ref-1",
      claimData = ClaimData(
        repaymentClaimDetails = RepaymentClaimDetails(
          claimingGiftAid = true,
          claimingTaxDeducted = true,
          claimingUnderGiftAidSmallDonationsScheme = false
        ),
        communityBuildingsScheduleFileUploadReference = Some(FileUploadReference("file-1"))
      )
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(claimsRepository.collection.deleteMany(BsonDocument()).toFuture())
  }

  private def stubOrganisationLookup(): Unit =
    wireMockServer.stubFor(
      get(urlEqualTo("/rds-datacache-proxy/charities/organisations/1234567890"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(Json.obj("organisationName" -> "test charity").toString())
        )
    )

  private def stubCommunityBuildingValidation(): Unit =
    wireMockServer.stubFor(
      get(urlEqualTo(s"/charities-claims-validation/$claimId/upload-results/file-1"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              Json
                .obj(
                  "reference"              -> "file-1",
                  "fileStatus"             -> "VALIDATED",
                  "validationType"         -> "CommunityBuildings",
                  "communityBuildingsData" -> Json.obj(
                    "communityBuildings" -> Json.arr(),
                    "totalOfAllAmounts"  -> 100
                  )
                )
                .toString
            )
        )
    )

  private def stubChrisResponse(status: Int): Unit =
    wireMockServer.stubFor(
      post(urlEqualTo("/submission/ChRIS/Charities/Filing/sync/HMRC-CHAR-CLM"))
        .willReturn(aResponse().withStatus(status))
    )

  private def requestBody(id: String, ref: String) =
    Json.obj(
      "claimId"              -> id,
      "lastUpdatedReference" -> ref,
      "declarationLanguage" -> "EN"
    )

  private def postSubmission(body: play.api.libs.json.JsValue) =
    httpClient
      .post(url"$baseUrl/chris")(using HeaderCarrier())
      .withBody(body)
      .setHeader(authHeader)
      .execute[HttpResponse]
      .futureValue

  "POST /chris" should {

    "return 200 when claim submitted successfully" in {

      authorisedOrganisation()
      await(repository.put(claimId)(ClaimsRepository.claimDataKey, baseClaim))

      stubOrganisationLookup()
      stubCommunityBuildingValidation()
      stubChrisResponse(200)

      val response = postSubmission(requestBody(claimId, "ref-1"))

      response.status shouldBe OK

      wireMockServer.verify(
        postRequestedFor(urlEqualTo("/submission/ChRIS/Charities/Filing/sync/HMRC-CHAR-CLM"))
      )
    }

    "return 404 when claim does not exist" in {
      authorisedOrganisation()

      val response = postSubmission(requestBody("missing-claim", "ref-1"))
      response.status shouldBe NOT_FOUND
    }

    "return 400 when claim already submitted" in {
      authorisedOrganisation()

      val submittedClaim =
        baseClaim.copy(
          claimSubmitted = true,
          submissionDetails = Some(
            SubmissionDetails(
              submissionTimestamp = "2025-03-01T10:00:00Z",
              submissionReference = "ref-1"
            )
          )
        )

      await(repository.put(claimId)(ClaimsRepository.claimDataKey, submittedClaim))

      val response = postSubmission(requestBody(claimId, "ref-2"))
      response.status shouldBe BAD_REQUEST
    }

    "return 400 when claim updated by another user" in {
      authorisedOrganisation()

      await(repository.put(claimId)(ClaimsRepository.claimDataKey, baseClaim))

      val response = postSubmission(requestBody(claimId, "different-ref"))
      response.status shouldBe BAD_REQUEST
    }

    "return 500 when ChRIS connector fails" in {
      authorisedOrganisation()
      await(repository.put(claimId)(ClaimsRepository.claimDataKey, baseClaim))

      stubOrganisationLookup()
      stubCommunityBuildingValidation()
      stubChrisResponse(500)

      val response = postSubmission(requestBody(claimId, "ref-1"))
      response.status shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
