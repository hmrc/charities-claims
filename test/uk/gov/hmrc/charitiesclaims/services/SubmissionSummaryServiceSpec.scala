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

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.charitiesclaims.connectors.{ClaimsValidationConnector, RdsDatacacheProxyConnector}
import uk.gov.hmrc.charitiesclaims.models
import uk.gov.hmrc.charitiesclaims.models.*
import uk.gov.hmrc.charitiesclaims.models.summary.*
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SubmissionSummaryServiceSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with OptionValues {

  given HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("test-session-id")))

  private val mockRdsConnector        = mock[RdsDatacacheProxyConnector]
  private val mockValidationConnector = mock[ClaimsValidationConnector]

  private val service =
    new SubmissionSummaryServiceImpl(mockRdsConnector, mockValidationConnector)

  case class TestCurrentUser(
    affinityGroup: AffinityGroup,
    userId: String,
    enrolmentIdentifierValue: String,
    enrolmentIdentifierKey: String
  ) extends models.CurrentUser

  private val organisationUser: models.CurrentUser =
    TestCurrentUser(
      affinityGroup = AffinityGroup.Organisation,
      userId = "test-user-id",
      enrolmentIdentifierValue = "CHAR123",
      enrolmentIdentifierKey = "test-enrolment-identifier-key"
    )

  private val agentUser: models.CurrentUser =
    TestCurrentUser(
      affinityGroup = AffinityGroup.Agent,
      userId = "test-user-id",
      enrolmentIdentifierValue = "AGENT123",
      enrolmentIdentifierKey = "test-enrolment-identifier-key"
    )

  private val baseClaim =
    Claim(
      claimId = "test-claim-id",
      userId = "user-1",
      claimSubmitted = true,
      lastUpdatedReference = "ref",
      claimData = ClaimData(
        repaymentClaimDetails = RepaymentClaimDetails(
          claimingGiftAid = true,
          claimingTaxDeducted = true,
          claimingUnderGiftAidSmallDonationsScheme = false
        ),
        organisationDetails = Some(
          OrganisationDetails(
            nameOfCharityRegulator = NameOfCharityRegulator.EnglandAndWales,
            areYouACorporateTrustee = true,
            nameOfCorporateTrustee = Some("Corp Trustee")
          )
        )
      ),
      submissionDetails = Some(
        SubmissionDetails(
          submissionTimestamp = "2025-01-01T12:00:00Z",
          submissionReference = "SUB123"
        )
      )
    )

  private def mockOrganisationName(
    identifier: String = "CHAR123",
    organisationName: String = "Test Charity"
  ): Unit =
    when(mockRdsConnector.getOrganisationName(eqTo(identifier))(using any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(organisationName)))

  private def mockAgentName(
    identifier: String = "AGENT123",
    agentName: String = "Test Agent"
  ): Unit =
    when(mockRdsConnector.getAgentName(eqTo(identifier))(using any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(agentName)))

  private def mockGiftAidValidation(
    ref: FileUploadReference,
    data: GiftAidScheduleData
  ): Unit =
    when(mockValidationConnector.getUploadResult(eqTo("test-claim-id"), eqTo(ref))(using any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(GetUploadResultValidatedGiftAid(ref, data))))

  private def mockOtherIncomeValidation(
    ref: FileUploadReference,
    data: OtherIncomeScheduleData
  ): Unit =
    when(mockValidationConnector.getUploadResult(eqTo("test-claim-id"), eqTo(ref))(using any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(GetUploadResultValidatedOtherIncome(ref, data))))

  private def mockCommunityBuildingValidation(
    ref: FileUploadReference,
    data: CommunityBuildingsScheduleData
  ): Unit =
    when(mockValidationConnector.getUploadResult(eqTo("test-claim-id"), eqTo(ref))(using any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(GetUploadResultValidatedCommunityBuildings(ref, data))))

  private def mockConnectedCharitiesValidation(
    ref: FileUploadReference,
    data: ConnectedCharitiesScheduleData
  ): Unit =
    when(mockValidationConnector.getUploadResult(eqTo("test-claim-id"), eqTo(ref))(using any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(GetUploadResultValidatedConnectedCharities(ref, data))))

  "SubmissionSummaryServiceImpl" should {

    "return a summary response successfully for the claim submitted by corporate trustee" in {
      mockOrganisationName()

      val result = service.getSummary(baseClaim, organisationUser).futureValue

      result.claimDetails.charityName          shouldBe "Test Charity"
      result.claimDetails.hmrcCharityReference shouldBe "CHAR123"
      result.claimDetails.submittedBy          shouldBe "Corp Trustee"
      result.submissionReferenceNumber         shouldBe "SUB123"
    }

    "populate claim details correctly when submitted by an agent" in {
      val claim =
        baseClaim.copy(
          claimData = baseClaim.claimData.copy(
            repaymentClaimDetails = baseClaim.claimData.repaymentClaimDetails.copy(
              hmrcCharitiesReference = Some("Hmrc-ref-123")
            )
          )
        )

      mockOrganisationName("Hmrc-ref-123", "Test Charity")
      mockAgentName()

      val result = service.getSummary(claim, agentUser).futureValue

      result.claimDetails shouldBe ClaimDetails(
        charityName = "Test Charity",
        hmrcCharityReference = "Hmrc-ref-123",
        submissionTimestamp = "2025-01-01T12:00:00Z",
        submittedBy = "Test Agent"
      )
    }

    "return empty submittedBy when agent name is not found" in {
      val claim =
        baseClaim.copy(
          claimData = baseClaim.claimData.copy(
            repaymentClaimDetails = baseClaim.claimData.repaymentClaimDetails.copy(
              hmrcCharitiesReference = Some("Hmrc-ref-123")
            )
          )
        )

      mockOrganisationName("Hmrc-ref-123", "Test Charity")

      when(mockRdsConnector.getAgentName(eqTo("AGENT123"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      val result = service.getSummary(claim, agentUser).futureValue

      result.claimDetails.submittedBy shouldBe ""
    }

    "populate gift aid details correctly for the claim submitted by an authorised official with no gift aid adjustment" in {
      val giftAidRef = FileUploadReference("ga-ref-123")

      val giftAidSchedule =
        GiftAidScheduleData(
          earliestDonationDate = "2024-01-01",
          totalDonations = BigDecimal(100),
          donations = Seq(
            Donation(donationDate = "2024-01-01", donationAmount = BigDecimal(50)),
            Donation(donationDate = "2024-01-01", donationAmount = BigDecimal(50))
          )
        )

      val claim =
        baseClaim.copy(
          claimData = baseClaim.claimData.copy(
            giftAidScheduleFileUploadReference = Some(giftAidRef),
            organisationDetails = Some(
              OrganisationDetails(
                nameOfCharityRegulator = NameOfCharityRegulator.EnglandAndWales,
                areYouACorporateTrustee = false,
                authorisedOfficialTrusteeTitle = Some("Mr"),
                authorisedOfficialTrusteeFirstName = Some("Test"),
                authorisedOfficialTrusteeLastName = Some("User")
              )
            )
          )
        )

      mockOrganisationName()
      mockGiftAidValidation(giftAidRef, giftAidSchedule)

      val result = service.getSummary(claim, organisationUser).futureValue

      result.claimDetails.submittedBy                        shouldBe "Mr Test User"
      result.giftAidDetails.value.numberGiftAidDonations     shouldBe 2
      result.giftAidDetails.value.totalValueGiftAidDonations shouldBe 100
      result.adjustmentDetails                               shouldBe None
    }

    "populate other income details correctly with no other income adjustment" in {
      val otherIncomeRef      = FileUploadReference("oi-ref-789")
      val otherIncomeSchedule =
        OtherIncomeScheduleData(
          adjustmentForOtherIncomePreviousOverClaimed = BigDecimal(0),
          totalOfGrossPayments = BigDecimal(50),
          totalOfTaxDeducted = BigDecimal(20),
          otherIncomes = Seq(
            OtherIncome(1, "payer", "2024-01-01", BigDecimal(50), BigDecimal(20))
          )
        )
      val claim               =
        baseClaim.copy(
          claimData = baseClaim.claimData.copy(
            otherIncomeScheduleFileUploadReference = Some(otherIncomeRef)
          )
        )
      mockOrganisationName()
      mockOtherIncomeValidation(otherIncomeRef, otherIncomeSchedule)

      val result = service.getSummary(claim, organisationUser).futureValue

      result.otherIncomeDetails.value.numberOtherIncomeItems     shouldBe 1
      result.otherIncomeDetails.value.totalValueOtherIncomeItems shouldBe 20
      result.adjustmentDetails                                   shouldBe None
    }

    "populate gasdsDetails correctly when both connected charities and community buildings are present" in {
      val communityBuildingsRef = FileUploadReference("cb-ref-123")
      val connectedCharitiesRef = FileUploadReference("cc-ref-456")

      val communityBuildingsData =
        CommunityBuildingsScheduleData(
          totalOfAllAmounts = BigDecimal("500.00"),
          communityBuildings = Seq(
            CommunityBuilding(
              communityBuildingItem = 1,
              buildingName = "Village Hall",
              firstLineOfAddress = "1 High Street",
              postcode = "AB1 2CD",
              taxYear1 = 2024,
              amountYear1 = BigDecimal("500.00")
            )
          )
        )

      val connectedCharitiesData =
        ConnectedCharitiesScheduleData(
          charities = Seq(
            ConnectedCharity(1, "Charity One", "X95442"),
            ConnectedCharity(2, "Charity Two", "X95442"),
            ConnectedCharity(3, "Charity Three", "X95442"),
            ConnectedCharity(4, "Charity Four", "X95442")
          )
        )

      val claim =
        baseClaim.copy(
          claimData = baseClaim.claimData.copy(
            communityBuildingsScheduleFileUploadReference = Some(communityBuildingsRef),
            connectedCharitiesScheduleFileUploadReference = Some(connectedCharitiesRef)
          )
        )

      mockOrganisationName()
      mockCommunityBuildingValidation(communityBuildingsRef, communityBuildingsData)
      mockConnectedCharitiesValidation(connectedCharitiesRef, connectedCharitiesData)

      val result = service.getSummary(claim, organisationUser).futureValue

      result.gasdsDetails.value.numberCommunityBuildings           shouldBe Some(1)
      result.gasdsDetails.value.totalValueGasdsInCommunityBuilding shouldBe Some(500)
      result.gasdsDetails.value.numberConnectedCharities           shouldBe Some(4)
    }

    "populate gasdsDetails correctly when only community building is present" in {
      val communityBuildingsRef = FileUploadReference("cb-ref-123")

      val communityBuildingsData =
        CommunityBuildingsScheduleData(
          totalOfAllAmounts = BigDecimal("500.00"),
          communityBuildings = Seq(
            CommunityBuilding(
              communityBuildingItem = 1,
              buildingName = "Village Hall",
              firstLineOfAddress = "1 High Street",
              postcode = "AB1 2CD",
              taxYear1 = 2024,
              amountYear1 = BigDecimal("500.00")
            )
          )
        )

      val claim =
        baseClaim.copy(
          claimData = baseClaim.claimData.copy(
            communityBuildingsScheduleFileUploadReference = Some(communityBuildingsRef)
          )
        )

      mockOrganisationName()
      mockCommunityBuildingValidation(communityBuildingsRef, communityBuildingsData)

      val result = service.getSummary(claim, organisationUser).futureValue

      result.gasdsDetails.value.totalValueGasdsNotInCommunityBuilding shouldBe None
      result.gasdsDetails.value.numberCommunityBuildings              shouldBe Some(1)
      result.gasdsDetails.value.totalValueGasdsInCommunityBuilding    shouldBe Some(500)
      result.gasdsDetails.value.numberConnectedCharities              shouldBe None
    }

    "populate gasdsDetails correctly when only connected charities is present" in {
      val connectedCharitiesRef = FileUploadReference("cc-ref-456")

      val connectedCharitiesData =
        ConnectedCharitiesScheduleData(
          charities = Seq(
            ConnectedCharity(1, "Charity One", "X95442"),
            ConnectedCharity(2, "Charity Two", "X95442"),
            ConnectedCharity(3, "Charity Three", "X95442"),
            ConnectedCharity(4, "Charity Four", "X95442")
          )
        )

      val claim =
        baseClaim.copy(
          claimData = baseClaim.claimData.copy(
            connectedCharitiesScheduleFileUploadReference = Some(connectedCharitiesRef)
          )
        )

      mockOrganisationName()
      mockConnectedCharitiesValidation(connectedCharitiesRef, connectedCharitiesData)

      val result = service.getSummary(claim, organisationUser).futureValue

      result.gasdsDetails.value.totalValueGasdsNotInCommunityBuilding shouldBe None
      result.gasdsDetails.value.numberCommunityBuildings              shouldBe None
      result.gasdsDetails.value.totalValueGasdsInCommunityBuilding    shouldBe None
      result.gasdsDetails.value.numberConnectedCharities              shouldBe Some(4)
    }

    "populate gasdsDetails correctly when small donations scheme is present" in {
      mockOrganisationName()

      val claim =
        baseClaim.copy(
          claimData = baseClaim.claimData.copy(
            giftAidSmallDonationsSchemeDonationDetails = Some(
              GiftAidSmallDonationsSchemeDonationDetails(
                adjustmentForGiftAidOverClaimed = BigDecimal(1234),
                claims = Seq(
                  GiftAidSmallDonationsSchemeClaim(2024, BigDecimal(100)),
                  GiftAidSmallDonationsSchemeClaim(2023, BigDecimal(23)),
                  GiftAidSmallDonationsSchemeClaim(2022, BigDecimal(1))
                )
              )
            )
          )
        )

      val result = service.getSummary(claim, organisationUser).futureValue

      result.gasdsDetails.value.totalValueGasdsNotInCommunityBuilding shouldBe Some(BigDecimal(124))
      result.gasdsDetails.value.numberCommunityBuildings              shouldBe None
      result.gasdsDetails.value.totalValueGasdsInCommunityBuilding    shouldBe None
      result.gasdsDetails.value.numberConnectedCharities              shouldBe None
      result.adjustmentDetails.value.previouslyOverclaimedGasds       shouldBe Some(BigDecimal(1234))
    }

    "calculate adjustment values correctly" in {
      val otherIncomeRef = FileUploadReference("oi-ref-789")
      val giftAidRef     = FileUploadReference("ga-ref-123")

      val claim =
        baseClaim.copy(
          claimData = baseClaim.claimData.copy(
            giftAidScheduleFileUploadReference = Some(giftAidRef),
            otherIncomeScheduleFileUploadReference = Some(otherIncomeRef)
          )
        )

      val giftAidSchedule =
        GiftAidScheduleData(
          earliestDonationDate = "2024-01-01",
          prevOverclaimedGiftAid = Some(BigDecimal(10)),
          totalDonations = BigDecimal(100),
          donations = Seq()
        )

      val otherIncomeSchedule =
        OtherIncomeScheduleData(
          adjustmentForOtherIncomePreviousOverClaimed = BigDecimal(5),
          totalOfGrossPayments = BigDecimal(50),
          totalOfTaxDeducted = BigDecimal(20),
          otherIncomes = Seq()
        )

      mockOrganisationName()
      mockOtherIncomeValidation(otherIncomeRef, otherIncomeSchedule)
      mockGiftAidValidation(giftAidRef, giftAidSchedule)

      val result = service.getSummary(claim, organisationUser).futureValue

      result.adjustmentDetails.value.previouslyOverclaimedGiftAidOtherIncome shouldBe Some(15)
      result.adjustmentDetails.value.previouslyOverclaimedGasds              shouldBe None
    }

    "return empty charity name when organisation name is not found" in {
      when(mockRdsConnector.getOrganisationName(eqTo("CHAR123"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      val result = service.getSummary(baseClaim, organisationUser).futureValue

      result.claimDetails.charityName shouldBe ""
    }

    "return empty charity name when organisation lookup fails" in {
      when(mockRdsConnector.getOrganisationName(eqTo("CHAR123"))(using any[HeaderCarrier]))
        .thenReturn(Future.failed(new Exception("500 error")))

      val result = service.getSummary(baseClaim, organisationUser).futureValue

      result.claimDetails.charityName shouldBe ""
    }
  }
}
