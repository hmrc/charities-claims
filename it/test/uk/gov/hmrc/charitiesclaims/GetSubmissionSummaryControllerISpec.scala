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
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.helpers.IntegrationTestSupport
import uk.gov.hmrc.charitiesclaims.models.*
import uk.gov.hmrc.charitiesclaims.repositories.ClaimsRepository
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import scala.concurrent.ExecutionContext.Implicits.global

class GetSubmissionSummaryControllerISpec
    extends AnyWordSpec
    with IntegrationTestSupport
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with IntegrationPatience {

  private val authHeader = "Authorization" -> "Bearer test-token"

  private val claimId = "claim-1"

  private val submittedClaim =
    Claim(
      claimId = claimId,
      userId = "user-123",
      claimSubmitted = true,
      lastUpdatedReference = "",
      claimData = ClaimData(
        repaymentClaimDetails = RepaymentClaimDetails(
          claimingGiftAid = true,
          claimingTaxDeducted = true,
          claimingUnderGiftAidSmallDonationsScheme = false
        ),
        communityBuildingsScheduleFileUploadReference = Some(FileUploadReference("file-1"))
      ),
      submissionDetails = Some(SubmissionDetails("2025-01-01T10:00:00Z", "SUB123"))
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    authorisedOrganisation()
  }

  private def insertClaim(claim: Claim): Unit =
    await(repository.put(claim.claimId)(ClaimsRepository.claimDataKey, claim))

  private def stubOrganisationLookup(): Unit =
    wireMockServer.stubFor(
      get(urlEqualTo("/rds-datacache-proxy/charities/organisations/1234567890"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(Json.obj("organisationName" -> "test charity").toString())
        )
    )

  private def stubCommunityBuildingsValidation(): Unit =
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

  private def getSummary(id: String): HttpResponse =
    httpClient
      .get(url"$baseUrl/submission-summary/$id")(using HeaderCarrier())
      .setHeader(authHeader)
      .execute[HttpResponse]
      .futureValue

  "GET /submission-summary/:claimId" should {

    "return submission summary successfully" in {
      insertClaim(submittedClaim)
      stubOrganisationLookup()
      stubCommunityBuildingsValidation()

      val response = getSummary(claimId)

      response.status shouldBe OK
    }

    "return 404 if claim does not exist" in {
      val response = getSummary("missing-claim")

      response.status shouldBe NOT_FOUND
    }

    "return 400 if claim is not submitted" in {
      val claimNotSubmitted =
        submittedClaim.copy(
          claimSubmitted = false,
          submissionDetails = None
        )
      insertClaim(claimNotSubmitted)

      val response = getSummary(claimId)

      response.status shouldBe BAD_REQUEST
    }
  }
}
