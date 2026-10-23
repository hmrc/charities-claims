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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.charitiesclaims.connectors.ClaimsValidationConnector
import uk.gov.hmrc.charitiesclaims.models.{Claim, ClaimInfo}
import uk.gov.hmrc.charitiesclaims.repositories.ClaimsRepository
import uk.gov.hmrc.charitiesclaims.util.TestClaimsService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ClaimsServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with IntegrationPatience with MockitoSugar {

  given ExecutionContext = ExecutionContext.global
  given HeaderCarrier    = HeaderCarrier()

  private val claims: Seq[Claim] =
    Json
      .parse(this.getClass.getResourceAsStream("/get-claims-response.json"))
      .as[JsObject]
      .value("claimsList")
      .as[Seq[Claim]]

  private val testClaim: Claim = claims.head

  private val testClaimInfo: ClaimInfo =
    ClaimInfo(
      claimId = testClaim.claimId,
      userId = testClaim.userId,
      claimSubmitted = testClaim.claimSubmitted,
      lastUpdatedReference = testClaim.lastUpdatedReference,
      hmrcCharitiesReference = testClaim.claimData.repaymentClaimDetails.hmrcCharitiesReference,
      nameOfCharity = testClaim.claimData.repaymentClaimDetails.nameOfCharity
    )

  trait Setup {
    val mockRepository: ClaimsRepository         = mock[ClaimsRepository]
    val mockConnector: ClaimsValidationConnector = mock[ClaimsValidationConnector]
    val service                                  = new ClaimsServiceImpl(mockRepository, mockConnector)
  }

  "ClaimsServiceImpl.putClaim" should {
    "skip saving when the claim is unchanged" in new Setup {
      when(mockRepository.getClaimWithCreatedAt(testClaim.claimId))
        .thenReturn(Future.successful(Some((testClaim, Instant.now()))))

      service.putClaim(testClaim).futureValue

      verify(mockRepository, never()).putClaim(any[Claim]())
      verify(mockConnector, never()).touchTtl(any[String]())(using any[HeaderCarrier]())
    }

    "save and touch the TTL when the claim is new" in new Setup {
      when(mockRepository.getClaimWithCreatedAt(testClaim.claimId))
        .thenReturn(Future.successful(None))
      when(mockRepository.putClaim(testClaim))
        .thenReturn(Future.unit)
      when(mockConnector.touchTtl(any[String]())(using any[HeaderCarrier]())).thenReturn(Future.unit)

      service.putClaim(testClaim).futureValue

      verify(mockRepository).putClaim(testClaim)
      verify(mockConnector).touchTtl(any[String]())(using any[HeaderCarrier]())
    }

    "save when the claim has changed" in new Setup {
      val existing = testClaim.copy(claimSubmitted = !testClaim.claimSubmitted)
      when(mockRepository.getClaimWithCreatedAt(testClaim.claimId))
        .thenReturn(Future.successful(Some((existing, Instant.now()))))
      when(mockRepository.putClaim(testClaim)).thenReturn(Future.unit)
      when(mockConnector.touchTtl(any[String]())(using any[HeaderCarrier]())).thenReturn(Future.unit)

      service.putClaim(testClaim).futureValue

      verify(mockRepository).putClaim(testClaim)
    }

    "still succeed when the TTL update fails" in new Setup {
      when(mockRepository.getClaimWithCreatedAt(testClaim.claimId))
        .thenReturn(Future.successful(None))
      when(mockRepository.putClaim(testClaim)).thenReturn(Future.unit)
      when(mockConnector.touchTtl(any[String]())(using any[HeaderCarrier]()))
        .thenReturn(Future.failed(new RuntimeException("TTL failed")))

      service.putClaim(testClaim).futureValue
    }
  }

  "ClaimsServiceImpl.getClaim" should {
    "return the claim from the repository" in new Setup {
      val result = Some((testClaim, Instant.now()))
      when(mockRepository.getClaimWithCreatedAt(testClaim.claimId))
        .thenReturn(Future.successful(result))

      service.getClaim(testClaim.claimId).futureValue shouldBe result
    }
  }

  "ClaimsServiceImpl.deleteClaim" should {
    "delete via the connector, then from the repository" in new Setup {
      when(mockConnector.deleteClaim(any[String]())(using any[HeaderCarrier]())).thenReturn(Future.unit)
      when(mockRepository.deleteEntity(testClaim.claimId)).thenReturn(Future.unit)

      service.deleteClaim(testClaim.claimId).futureValue

      verify(mockRepository).deleteEntity(testClaim.claimId)
    }

    "not delete from the repository when the connector fails" in new Setup {
      when(mockConnector.deleteClaim(any[String]())(using any[HeaderCarrier]()))
        .thenReturn(Future.failed(new RuntimeException("delete failed")))

      service.deleteClaim(testClaim.claimId).failed.futureValue shouldBe a[RuntimeException]

      verify(mockRepository, never()).deleteEntity(any[String]())
    }
  }

  "ClaimsServiceImpl.lisClaims" should {
    "return the claims from the repository" in new Setup {

      when(mockRepository.listClaims(testClaim.userId)).thenReturn(Future.successful(Seq(testClaimInfo)))

      service.listClaims(testClaim.userId).futureValue shouldBe Seq(testClaimInfo)
    }
  }

  "ClaimsServiceImpl.hasUnsubmittedClaim" should {
    "return the result from the repository" in new Setup {

      when(mockRepository.hasUnsubmittedClaim(testClaim.userId, "XR1234")).thenReturn(Future.successful(true))

      service.hasUnsubmittedClaim(testClaim.userId, "XR1234").futureValue shouldBe true
    }
  }

  "ClaimsServiceImpl.updateLastVisitedAt" should {
    "delegate to the repository" in new Setup {

      when(mockRepository.updateLastVisitedAt(testClaim.claimId)).thenReturn(Future.unit)

      service.updateLastVisitedAt(testClaim.claimId).futureValue

      verify(mockRepository).updateLastVisitedAt(testClaim.claimId)
    }
  }

  "TestClaimsService" should {
    "store, retrieve, list and delete claims" in {
      val claimsService = new TestClaimsService(Seq.empty)

      info("create and store a submitted claim for the first user")
      val claim = claims.head.copy(claimId = UUID.randomUUID().toString, userId = UUID.randomUUID().toString)
      claim.claimSubmitted shouldBe true
      claimsService.putClaim(claim).futureValue

      info("check the claim can be retrieved and listed")
      claimsService.getClaim(claim.claimId).futureValue.map(_._1)          shouldBe Some(claim)
      claimsService.listClaims(claim.userId).futureValue                   shouldBe Seq.empty
      claimsService.hasUnsubmittedClaim(claim.userId, "OR123").futureValue shouldBe false

      info("add a new submitted claim for the second user")
      val claim2 = claim.copy(userId = UUID.randomUUID().toString)
      claimsService.putClaim(claim2).futureValue

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
      claimsService.putClaim(claim3).futureValue

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
      claimsService.putClaim(claim4).futureValue

      info("check claims returned are only the submitted or unsubmitted claim")
      claimsService.listClaims(claim4.userId).futureValue                    shouldBe Seq(claimInfo4)
      claimsService.hasUnsubmittedClaim(claim4.userId, "OR123").futureValue  shouldBe false
      claimsService.hasUnsubmittedClaim(claim4.userId, "XR1234").futureValue shouldBe true

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
