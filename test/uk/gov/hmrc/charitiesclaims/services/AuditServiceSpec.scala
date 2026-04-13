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

package uk.gov.hmrc.charitiesclaims.services

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalamock.scalatest.MockFactory

import uk.gov.hmrc.charitiesclaims.services.AuditService
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.charitiesclaims.models._
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.http.HeaderCarrier
import org.scalamock.matchers.ArgThat
import java.time.Instant

import play.api.libs.json.JsValue

import scala.concurrent.{ExecutionContext, Future}
import org.scalatest.concurrent.ScalaFutures

class AuditServiceSpec extends AnyWordSpec with Matchers with MockFactory with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val hc: HeaderCarrier    = HeaderCarrier()

  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  val service = new AuditService(mockAuditConnector)

  def testClaim: Claim =
    Claim(
      claimId = "test-claim-id",
      userId = "test-user-id",
      claimSubmitted = false,
      lastUpdatedReference = "ref-123",
      claimData = ClaimData(
        repaymentClaimDetails = RepaymentClaimDetails(
          claimingGiftAid = false,
          claimingTaxDeducted = false,
          claimingUnderGiftAidSmallDonationsScheme = true
        )
      ),
      submissionDetails = None
    )

  "AuditService - sendEvent" should {

    "send correct ExtendedDataEvent and return success" in {

      val claim             = testClaim
      val scheduleData      = ScheduleData()
      val creationTimestamp = Instant.now()

      (mockAuditConnector
        .sendExtendedEvent(_: ExtendedDataEvent)(using _: HeaderCarrier, _: ExecutionContext))
        .expects(
          ArgThat[ExtendedDataEvent](
            { event =>
              val json = event.detail
              event.auditSource == "charities-claims" &&
              event.auditType == "ClaimSubmission" &&
              (json \ "claimId").as[String] == claim.claimId &&
              (json \ "userId").as[String] == claim.userId
            },
            None
          ),
          *,
          *
        )
        .returning(Future.successful(AuditResult.Success))

      val result = service.sendEvent(claim, scheduleData, creationTimestamp)

      whenReady(result) { res =>
        res shouldBe AuditResult.Success
      }
    }

    "propagate failure from auditConnector" in {

      val claim             = testClaim
      val scheduleData      = ScheduleData()
      val creationTimestamp = Instant.now()

      (mockAuditConnector
        .sendExtendedEvent(_: ExtendedDataEvent)(using _: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(new RuntimeException("Audit failed")))

      whenReady(service.sendEvent(claim, scheduleData, creationTimestamp).failed) { ex =>
        ex            shouldBe a[RuntimeException]
        ex.getMessage shouldBe "Audit failed"
      }
    }

    "include submissionDetails when present in claim JSON" in {

      val claim = testClaim.copy(
        submissionDetails = Some(
          SubmissionDetails(
            submissionTimestamp = "2026-01-01T00:00:00Z",
            submissionReference = "ref-456"
          )
        )
      )

      val scheduleData      = ScheduleData()
      val creationTimestamp = Instant.now()

      (mockAuditConnector
        .sendExtendedEvent(_: ExtendedDataEvent)(using _: HeaderCarrier, _: ExecutionContext))
        .expects(
          ArgThat((event: ExtendedDataEvent) => (event.detail \ "submissionDetails").asOpt[JsValue].isDefined, None),
          *,
          *
        )
        .returning(Future.successful(AuditResult.Success))

      val result = service.sendEvent(claim, scheduleData, creationTimestamp)

      whenReady(result) { res =>
        res shouldBe AuditResult.Success
      }
    }
  }
}
