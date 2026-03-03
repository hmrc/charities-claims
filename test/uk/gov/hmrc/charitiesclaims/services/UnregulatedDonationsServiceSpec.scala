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

import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.charitiesclaims.connectors.{ClaimsValidationConnector, FormpProxyConnector}
import uk.gov.hmrc.charitiesclaims.models.*
import uk.gov.hmrc.charitiesclaims.util.BaseSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UnregulatedDonationsServiceSpec extends BaseSpec {

  // Test user
  case class TestCurrentUser(
    affinityGroup: AffinityGroup,
    userId: String,
    enrolmentIdentifierValue: String,
    enrolmentIdentifierKey: String
  ) extends CurrentUser

  // Organisation user (uses CHARID enrolment identifier as charity reference)
  val organisationUser: CurrentUser = TestCurrentUser(
    affinityGroup = AffinityGroup.Organisation,
    userId = "test-user-id",
    enrolmentIdentifierValue = "ORG-CHARITY-REF-123",
    enrolmentIdentifierKey = "CHARID"
  )

  // Agent user (uses hmrcCharitiesReference from claim data as charity reference)
  val agentUser: CurrentUser = TestCurrentUser(
    affinityGroup = AffinityGroup.Agent,
    userId = "test-agent-id",
    enrolmentIdentifierValue = "AGENT-REF-456",
    enrolmentIdentifierKey = "AGENTCHARID"
  )

  given HeaderCarrier = HeaderCarrier()

  // test claim with organisation details
  def buildClaim(
    claimId: String = "test-claim-id",
    regulator: NameOfCharityRegulator = NameOfCharityRegulator.None,
    reason: Option[ReasonNotRegisteredWithRegulator] = Some(ReasonNotRegisteredWithRegulator.LowIncome),
    hmrcCharitiesReference: Option[String] = None,
    giftAidRef: Option[FileUploadReference] = None,
    otherIncomeRef: Option[FileUploadReference] = None,
    communityBuildingsRef: Option[FileUploadReference] = None
  ): Claim =
    Claim(
      claimId = claimId,
      userId = "test-user-id",
      claimSubmitted = false,
      lastUpdatedReference = "test-last-updated-ref",
      claimData = ClaimData(
        repaymentClaimDetails = RepaymentClaimDetails(
          claimingGiftAid = true,
          claimingTaxDeducted = false,
          claimingUnderGiftAidSmallDonationsScheme = false,
          hmrcCharitiesReference = hmrcCharitiesReference
        ),
        organisationDetails = Some(
          OrganisationDetails(
            nameOfCharityRegulator = regulator,
            reasonNotRegisteredWithRegulator = reason,
            charityRegistrationNumber = None,
            areYouACorporateTrustee = false
          )
        ),
        giftAidScheduleFileUploadReference = giftAidRef,
        otherIncomeScheduleFileUploadReference = otherIncomeRef,
        communityBuildingsScheduleFileUploadReference = communityBuildingsRef
      )
    )

  // test claim with no organisation details
  def buildClaimWithoutOrgDetails(claimId: String = "test-claim-id"): Claim =
    Claim(
      claimId = claimId,
      userId = "test-user-id",
      claimSubmitted = false,
      lastUpdatedReference = "test-last-updated-ref",
      claimData = ClaimData(
        repaymentClaimDetails = RepaymentClaimDetails(
          claimingGiftAid = true,
          claimingTaxDeducted = false,
          claimingUnderGiftAidSmallDonationsScheme = false
        )
      )
    )

  // function tests:

  "UnregulatedDonationsService companion object" - {

    "isUnregulatedDonation" - {

      "should return true when regulator is None and reason is LowIncome" in {
        val claim = buildClaim(
          regulator = NameOfCharityRegulator.None,
          reason = Some(ReasonNotRegisteredWithRegulator.LowIncome)
        )
        UnregulatedDonationsService.isUnregulatedDonation(claim) shouldBe true
      }

      "should return true when regulator is None and reason is Excepted" in {
        val claim = buildClaim(
          regulator = NameOfCharityRegulator.None,
          reason = Some(ReasonNotRegisteredWithRegulator.Excepted)
        )
        UnregulatedDonationsService.isUnregulatedDonation(claim) shouldBe true
      }

      "should return false when charity has a regulator" in {
        val claim = buildClaim(
          regulator = NameOfCharityRegulator.EnglandAndWales,
          reason = None
        )
        UnregulatedDonationsService.isUnregulatedDonation(claim) shouldBe false
      }

      "should return false when regulator is None but reason is Exempt" in {
        val claim = buildClaim(
          regulator = NameOfCharityRegulator.None,
          reason = Some(ReasonNotRegisteredWithRegulator.Exempt)
        )
        UnregulatedDonationsService.isUnregulatedDonation(claim) shouldBe false
      }

      "should return false when regulator is None but reason is Waiting" in {
        val claim = buildClaim(
          regulator = NameOfCharityRegulator.None,
          reason = Some(ReasonNotRegisteredWithRegulator.Waiting)
        )
        UnregulatedDonationsService.isUnregulatedDonation(claim) shouldBe false
      }

      "should return false when regulator is None but reason is not set" in {
        val claim = buildClaim(
          regulator = NameOfCharityRegulator.None,
          reason = None
        )
        UnregulatedDonationsService.isUnregulatedDonation(claim) shouldBe false
      }

      "should return false when organisationDetails is missing entirely" in {
        val claim = buildClaimWithoutOrgDetails()
        UnregulatedDonationsService.isUnregulatedDonation(claim) shouldBe false
      }
    }

    "calculateDonationsTotal" - {

      "should return the gift aid total when gift aid data is present" in {
        val giftAid = Some(
          GiftAidScheduleData(
            earliestDonationDate = "2025-01-01",
            totalDonations = BigDecimal(1000),
            donations = Seq.empty
          )
        )
        UnregulatedDonationsService.calculateDonationsTotal(giftAid) shouldBe BigDecimal(1000)
      }

      "should return zero when no gift aid data is present" in {
        UnregulatedDonationsService.calculateDonationsTotal(None) shouldBe BigDecimal(0)
      }
    }

    "resolveCharityReference" - {

      "should return enrolment identifier for organisation users" in {
        val claim = buildClaim()
        UnregulatedDonationsService.resolveCharityReference(claim, organisationUser) shouldBe Some(
          "ORG-CHARITY-REF-123"
        )
      }

      "should return hmrcCharitiesReference from claim data for agent users" in {
        val claim = buildClaim(hmrcCharitiesReference = Some("AGENT-CHARITY-REF-789"))
        UnregulatedDonationsService.resolveCharityReference(claim, agentUser) shouldBe Some("AGENT-CHARITY-REF-789")
      }

      "should return None for agent users when hmrcCharitiesReference is not set" in {
        val claim = buildClaim(hmrcCharitiesReference = None)
        UnregulatedDonationsService.resolveCharityReference(claim, agentUser) shouldBe None
      }
    }

    "claimIdToInt" - {

      "should always produce the same result for the same input" in {
        val claimId = "f2137428-c3b5-4b57-b75e-a6aa3b392680"
        val result1 = UnregulatedDonationsService.claimIdToInt(claimId)
        val result2 = UnregulatedDonationsService.claimIdToInt(claimId)
        result1 shouldBe result2
      }

      "should always produce a positive integer" in {
        val claimIds = Seq(
          "f2137428-c3b5-4b57-b75e-a6aa3b392680",
          "00000000-0000-0000-0000-000000000000",
          "ffffffff-ffff-ffff-ffff-ffffffffffff",
          "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
        )

        claimIds.foreach { claimId =>
          UnregulatedDonationsService.claimIdToInt(claimId) should be >= 0
        }
      }

      "should produce different results for different input claimId's" in {
        val result1 = UnregulatedDonationsService.claimIdToInt("claim-id-1")
        val result2 = UnregulatedDonationsService.claimIdToInt("claim-id-2")
        result1 should not be result2
      }
    }
  }

  // Service implementation tests:

  "UnregulatedDonationsServiceImpl" - {

    "recordUnregulatedDonation" - {

      "should be a no-op when the claim does not qualify as an unregulated donation, claim has a regulator" in {
        val formpProxyConnector       = mock[FormpProxyConnector]
        val claimsValidationConnector = mock[ClaimsValidationConnector]
        val service                   = new UnregulatedDonationsServiceImpl(formpProxyConnector, claimsValidationConnector)

        val claim = buildClaim(
          regulator = NameOfCharityRegulator.EnglandAndWales,
          reason = None
        )

        val result = service.recordUnregulatedDonation(claim, organisationUser)
        result.futureValue shouldBe ()
      }

      "should call saveUnregulatedDonation for a qualifying unregulated claim (LowIncome, organisation user)" in {
        val formpProxyConnector       = mock[FormpProxyConnector]
        val claimsValidationConnector = mock[ClaimsValidationConnector]
        val service                   = new UnregulatedDonationsServiceImpl(formpProxyConnector, claimsValidationConnector)

        val giftAidRef = FileUploadReference("gift-aid-ref")
        val claim      = buildClaim(
          claimId = "qualifying-claim-id",
          regulator = NameOfCharityRegulator.None,
          reason = Some(ReasonNotRegisteredWithRegulator.LowIncome),
          giftAidRef = Some(giftAidRef)
        )

        val giftAidScheduleData = GiftAidScheduleData(
          earliestDonationDate = "2025-01-01",
          totalDonations = BigDecimal(5000),
          donations = Seq.empty
        )

        (claimsValidationConnector
          .getUploadResult(_: String, _: FileUploadReference)(using _: HeaderCarrier))
          .expects("qualifying-claim-id", giftAidRef, *)
          .returning(Future.successful(Some(GetUploadResultValidatedGiftAid(giftAidRef, giftAidScheduleData))))

        val expectedClaimIdInt = UnregulatedDonationsService.claimIdToInt("qualifying-claim-id")

        (formpProxyConnector
          .saveUnregulatedDonation(_: String, _: Int, _: BigDecimal)(using _: HeaderCarrier))
          .expects("ORG-CHARITY-REF-123", expectedClaimIdInt, BigDecimal(5000), *)
          .returning(Future.successful(()))

        val result = service.recordUnregulatedDonation(claim, organisationUser)
        result.futureValue shouldBe ()
      }

      "should call saveUnregulatedDonation for a qualifying claim (Excepted, agent user)" in {
        val formpProxyConnector       = mock[FormpProxyConnector]
        val claimsValidationConnector = mock[ClaimsValidationConnector]
        val service                   = new UnregulatedDonationsServiceImpl(formpProxyConnector, claimsValidationConnector)

        val giftAidRef = FileUploadReference("gift-aid-ref")
        val claim      = buildClaim(
          claimId = "agent-claim-id",
          regulator = NameOfCharityRegulator.None,
          reason = Some(ReasonNotRegisteredWithRegulator.Excepted),
          hmrcCharitiesReference = Some("AGENT-CHARITY-REF-789"),
          giftAidRef = Some(giftAidRef)
        )

        val giftAidScheduleData = GiftAidScheduleData(
          earliestDonationDate = "2025-01-01",
          totalDonations = BigDecimal(3000),
          donations = Seq.empty
        )

        (claimsValidationConnector
          .getUploadResult(_: String, _: FileUploadReference)(using _: HeaderCarrier))
          .expects("agent-claim-id", giftAidRef, *)
          .returning(Future.successful(Some(GetUploadResultValidatedGiftAid(giftAidRef, giftAidScheduleData))))

        val expectedClaimIdInt = UnregulatedDonationsService.claimIdToInt("agent-claim-id")

        (formpProxyConnector
          .saveUnregulatedDonation(_: String, _: Int, _: BigDecimal)(using _: HeaderCarrier))
          .expects("AGENT-CHARITY-REF-789", expectedClaimIdInt, BigDecimal(3000), *)
          .returning(Future.successful(()))

        val result = service.recordUnregulatedDonation(claim, agentUser)
        result.futureValue shouldBe ()
      }

      "should return a failed Future when no charity reference is available (agent, no hmrcCharitiesReference)" in {
        val formpProxyConnector       = mock[FormpProxyConnector]
        val claimsValidationConnector = mock[ClaimsValidationConnector]
        val service                   = new UnregulatedDonationsServiceImpl(formpProxyConnector, claimsValidationConnector)

        // qualifying claim but agent has no hmrcCharitiesReference
        val claim = buildClaim(
          regulator = NameOfCharityRegulator.None,
          reason = Some(ReasonNotRegisteredWithRegulator.LowIncome),
          hmrcCharitiesReference = None
        )

        val result = service.recordUnregulatedDonation(claim, agentUser)
        result.failed.futureValue.getMessage shouldBe "Cannot record unregulated donation: no charity reference available"
      }

      "should be FormP error when saveUnregulatedDonation fails" in {
        val formpProxyConnector       = mock[FormpProxyConnector]
        val claimsValidationConnector = mock[ClaimsValidationConnector]
        val service                   = new UnregulatedDonationsServiceImpl(formpProxyConnector, claimsValidationConnector)

        val claim = buildClaim(
          claimId = "failing-claim-id",
          regulator = NameOfCharityRegulator.None,
          reason = Some(ReasonNotRegisteredWithRegulator.LowIncome)
        )

        // have no schedule refs, so no validation connector calls needed
        val expectedClaimIdInt = UnregulatedDonationsService.claimIdToInt("failing-claim-id")

        (formpProxyConnector
          .saveUnregulatedDonation(_: String, _: Int, _: BigDecimal)(using _: HeaderCarrier))
          .expects("ORG-CHARITY-REF-123", expectedClaimIdInt, BigDecimal(0), *)
          .returning(Future.failed(Exception("FormP Proxy error")))

        val result = service.recordUnregulatedDonation(claim, organisationUser)
        result.failed.futureValue.getMessage shouldBe "FormP Proxy error"
      }

      "should handle a qualifying claim when no gift aid schedule uploaded (total is zero)" in {
        val formpProxyConnector       = mock[FormpProxyConnector]
        val claimsValidationConnector = mock[ClaimsValidationConnector]
        val service                   = new UnregulatedDonationsServiceImpl(formpProxyConnector, claimsValidationConnector)

        // qualifying claim but no schedule file upload references
        val claim = buildClaim(
          claimId = "no-schedules-claim",
          regulator = NameOfCharityRegulator.None,
          reason = Some(ReasonNotRegisteredWithRegulator.Excepted)
        )

        val expectedClaimIdInt = UnregulatedDonationsService.claimIdToInt("no-schedules-claim")

        (formpProxyConnector
          .saveUnregulatedDonation(_: String, _: Int, _: BigDecimal)(using _: HeaderCarrier))
          .expects("ORG-CHARITY-REF-123", expectedClaimIdInt, BigDecimal(0), *)
          .returning(Future.successful(()))

        val result = service.recordUnregulatedDonation(claim, organisationUser)
        result.futureValue shouldBe ()
      }
    }
  }
}
