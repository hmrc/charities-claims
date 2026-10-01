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
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.charitiesclaims.connectors.ClaimsValidationConnector
import uk.gov.hmrc.charitiesclaims.models.{Claim, ClaimInfo}
import uk.gov.hmrc.charitiesclaims.util.TestClaimsService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import java.util.UUID
import scala.concurrent.Future

class ClaimsServiceSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with MockFactory {

  val mockClaimsValidationConnector: ClaimsValidationConnector = mock[ClaimsValidationConnector]

  (mockClaimsValidationConnector
    .deleteClaim(_: String)(using _: HeaderCarrier))
    .expects(*, *)
    .anyNumberOfTimes()
    .returning(Future.successful(()))

  (mockClaimsValidationConnector
    .touchTtl(_: String)(using _: HeaderCarrier))
    .expects(*, *)
    .atLeastOnce()
    .returning(Future.unit)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides {
        bind[ClaimsValidationConnector].toInstance(mockClaimsValidationConnector)
      }
      .build()

  private val realMongoDBClaimsService = app.injector.instanceOf[ClaimsService]

  val getClaimsResponse: JsValue = Json
    .parse(this.getClass.getResourceAsStream("/get-claims-response.json"))

  val claims: Seq[Claim] = getClaimsResponse.as[JsObject].value("claimsList").as[Seq[Claim]]

  Seq(
    (realMongoDBClaimsService, "DefaultClaimsService"),
    (new TestClaimsService(Seq.empty), "TestClaimsService")
  )
    .foreach { (claimsService, description) =>
      "ClaimsService" should {
        s"store, retrieve, list and delete claims when using $description" in {
          given HeaderCarrier = HeaderCarrier()

          info("create and store a submitted claim for the first user")
          val claim = claims.head.copy(claimId = UUID.randomUUID().toString, "UUID.randomUUID().toString")

          claim.claimSubmitted shouldBe true

          claimsService.putClaim(claim)

          info("check the claim can be retrieved and listed")
          claimsService.getClaim(claim.claimId).futureValue.map(_._1) shouldBe Some(claim)

          claimsService.listClaims(claim.userId).futureValue shouldBe Seq.empty

          claimsService
            .hasUnsubmittedClaim(claim.userId, "OR123")
            .futureValue shouldBe false

          info("add a new submitted claim for the second user")
          val claim2 = claim.copy(userId = UUID.randomUUID().toString)

          claimsService.putClaim(claim2)

          info("check the second claim cannot be retrieved since it is submitted")
          claimsService.listClaims(claim2.userId).futureValue shouldBe Seq.empty

          info("add the second submitted claim for the second user")
          val claim3 = claim.copy(
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
          claimsService.putClaim(claim3)

          info("check both claims cannot be retrieved since it is submitted")
          claimsService.listClaims(claim3.userId).futureValue shouldBe Seq.empty

          info("add a new unsubmitted claim for the second user")
          val claim4     = claim3.copy(claimId = UUID.randomUUID().toString, claimSubmitted = false)
          val claimInfo4 = ClaimInfo(
            claimId = claim4.claimId,
            userId = claim4.userId,
            claimSubmitted = claim4.claimSubmitted,
            lastUpdatedReference = claim4.lastUpdatedReference,
            hmrcCharitiesReference = claim4.claimData.repaymentClaimDetails.hmrcCharitiesReference,
            nameOfCharity = claim4.claimData.repaymentClaimDetails.nameOfCharity
          )
          claimsService.putClaim(claim4)

          info("check claims returned are only the submitted or unsubmitted claim")
          claimsService.listClaims(claim4.userId).futureValue shouldBe Seq(claimInfo4)

          claimsService
            .hasUnsubmittedClaim(claim4.userId, "OR123")
            .futureValue shouldBe false

          claimsService
            .hasUnsubmittedClaim(claim4.userId, "XR1234")
            .futureValue shouldBe true

          info("delete the claims")

          claimsService.deleteClaim(claim.claimId)
          claimsService.getClaim(claim.claimId).futureValue shouldBe None

          claimsService.deleteClaim(claim3.claimId)
          claimsService.getClaim(claim3.claimId).futureValue shouldBe None

          claimsService.deleteClaim(claim4.claimId)
          claimsService.getClaim(claim4.claimId).futureValue shouldBe None
        }
      }
    }
}
