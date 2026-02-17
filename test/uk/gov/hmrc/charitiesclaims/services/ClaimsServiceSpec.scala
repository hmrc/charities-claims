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

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.charitiesclaims.connectors.ClaimsValidationConnector
import uk.gov.hmrc.charitiesclaims.models.{Claim, ClaimInfo}
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global

import java.util.UUID
import scala.concurrent.Future
import uk.gov.hmrc.charitiesclaims.util.TestClaimsService

class ClaimsServiceSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with MockFactory {

  val mockClaimsValidationConnector = mock[ClaimsValidationConnector]

  (mockClaimsValidationConnector
    .deleteClaim(_: String)(using _: HeaderCarrier))
    .expects(*, *)
    .anyNumberOfTimes()
    .returning(Future.successful(()))

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides {
        bind[ClaimsValidationConnector].toInstance(mockClaimsValidationConnector)
      }
      .build()

  private val realMongoDBClaimsService = app.injector.instanceOf[ClaimsService]

  val getClaimsResponse = Json
    .parse(this.getClass.getResourceAsStream("/get-claims-response.json"))

  val claims = getClaimsResponse.as[JsObject].value.get("claimsList").get.as[Seq[Claim]]

  Seq(
    (realMongoDBClaimsService, "DefaultClaimsService"),
    (new TestClaimsService(Seq.empty), "TestClaimsService")
  )
    .foreach { (claimsService, description) =>
      "ClaimsService" should {
        s"store, retrieve, list and delete claims when using $description" in {
          given HeaderCarrier = HeaderCarrier()

          info("create and store a submitted claim for the first user")
          val claim     = claims.head.copy(claimId = UUID.randomUUID().toString, UUID.randomUUID().toString)
          val claimInfo = ClaimInfo(
            claim.claimId,
            claim.userId,
            claim.claimSubmitted,
            claim.lastUpdatedReference
          )

          claim.claimSubmitted shouldBe true

          claimsService.putClaim(claim).futureValue
          claimsService.putClaim(claim).futureValue

          info("check the claim can be retrieved and listed")
          claimsService.getClaim(claim.claimId).futureValue.map(_._1) shouldBe Some(claim)
          claimsService.getClaim(claim.claimId).futureValue.map(_._1) shouldBe Some(claim)

          claimsService.listClaims(claim.userId, claimSubmitted = true).futureValue  shouldBe Seq(claimInfo)
          claimsService.listClaims(claim.userId, claimSubmitted = false).futureValue shouldBe Seq.empty

          info("add a new submitted claim for the second user")
          val claim2     = claim.copy(userId = UUID.randomUUID().toString)
          val claimInfo2 = ClaimInfo(
            claim2.claimId,
            claim2.userId,
            claim2.claimSubmitted,
            claim2.lastUpdatedReference
          )

          claimsService.putClaim(claim2).futureValue

          info("check the second claim can be retrieved and listed")
          claimsService.listClaims(claim2.userId, claimSubmitted = true).futureValue  shouldBe Seq(claimInfo2)
          claimsService.listClaims(claim2.userId, claimSubmitted = false).futureValue shouldBe Seq.empty

          info("add the second submitted claim for the second user")
          val claim3     = claim.copy(
            claimId = UUID.randomUUID().toString,
            userId = claim2.userId,
            claimData = claim.claimData.copy(repaymentClaimDetails =
              claim.claimData.repaymentClaimDetails
                .copy(
                  hmrcCharitiesReference = Some("XR1234"),
                  nameOfCharity = Some("Test Charity")
                )
            )
          )
          val claimInfo3 = ClaimInfo(
            claim3.claimId,
            claim3.userId,
            claim3.claimSubmitted,
            claim3.lastUpdatedReference,
            claim3.claimData.repaymentClaimDetails.hmrcCharitiesReference,
            claim3.claimData.repaymentClaimDetails.nameOfCharity
          )
          claimsService.putClaim(claim3).futureValue

          info("check both claims can be retrieved and listed")
          claimsService.listClaims(claim3.userId, claimSubmitted = true).futureValue  shouldBe Seq(
            claimInfo2,
            claimInfo3
          )
          claimsService.listClaims(claim3.userId, claimSubmitted = false).futureValue shouldBe Seq.empty

          info("add a new unsubmitted claim for the second user")
          val claim4     = claim3.copy(claimId = UUID.randomUUID().toString, claimSubmitted = false)
          val claimInfo4 = ClaimInfo(
            claim4.claimId,
            claim4.userId,
            claim4.claimSubmitted,
            claim4.lastUpdatedReference,
            claim4.claimData.repaymentClaimDetails.hmrcCharitiesReference,
            claim4.claimData.repaymentClaimDetails.nameOfCharity
          )
          claimsService.putClaim(claim4).futureValue

          info("check claims returned are only the submitted or unsubmitted claim")
          claimsService.listClaims(claim4.userId, claimSubmitted = true).futureValue  shouldBe Seq(
            claimInfo2,
            claimInfo3
          )
          claimsService.listClaims(claim4.userId, claimSubmitted = false).futureValue shouldBe Seq(claimInfo4)

          info("delete the claims")
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
