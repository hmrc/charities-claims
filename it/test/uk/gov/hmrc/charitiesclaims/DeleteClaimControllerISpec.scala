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
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaims.helpers.IntegrationTestSupport
import uk.gov.hmrc.charitiesclaims.models.{Claim, ClaimData, RepaymentClaimDetails}
import uk.gov.hmrc.charitiesclaims.repositories.ClaimsRepository
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import scala.concurrent.ExecutionContext.Implicits.global

class DeleteClaimControllerISpec
    extends AnyWordSpec
    with IntegrationTestSupport
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with IntegrationPatience {

  val claimId = "claim-1"

  private val authHeader =
    "Authorization" -> "Bearer test-token"

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.collection.deleteMany(BsonDocument()).toFuture())
  }

  private def createClaim(id: String) =
    Claim(
      claimId = id,
      userId = "test-user",
      claimSubmitted = false,
      lastUpdatedReference = "",
      claimData = ClaimData(
        repaymentClaimDetails = RepaymentClaimDetails(true, true, false)
      )
    )

  private def insertClaim(id: String): Unit =
    await(
      repository.put(id)(
        ClaimsRepository.claimDataKey,
        createClaim(id)
      )
    )

  private def stubDeleteValidation(id: String): Unit =
    wireMockServer.stubFor(
      delete(urlEqualTo(s"/charities-claims-validation/$id/upload-results"))
        .willReturn(aResponse().withStatus(200))
    )

  private def deleteClaim(id: String): HttpResponse =
    httpClient
      .delete(url"$baseUrl/claims/$id")(using HeaderCarrier())
      .setHeader(authHeader)
      .execute[HttpResponse]
      .futureValue

  private def assertSuccess(response: HttpResponse): Unit = {
    response.status                                     shouldBe OK
    (Json.parse(response.body) \ "success").as[Boolean] shouldBe true
  }

  "DELETE /claims/:claimId" should {

    "delete the claim successfully" in {

      insertClaim(claimId)
      authorisedOrganisation()
      stubDeleteValidation(claimId)

      val response = deleteClaim(claimId)

      assertSuccess(response)

      val remaining =
        await(repository.collection.countDocuments().toFuture())
      remaining shouldBe 0

      wireMockServer.verify(
        deleteRequestedFor(
          urlEqualTo(s"/charities-claims-validation/$claimId/upload-results")
        )
      )
    }

    "return success even if claim does not exist" in {

      val missingClaimId = "missing-claim"

      authorisedOrganisation()
      stubDeleteValidation(missingClaimId)

      val response = deleteClaim(missingClaimId)

      assertSuccess(response)
    }
  }
}
