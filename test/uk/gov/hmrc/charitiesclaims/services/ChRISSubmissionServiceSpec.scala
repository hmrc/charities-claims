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
import uk.gov.hmrc.charitiesclaims.models
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.SessionId
import uk.gov.hmrc.charitiesclaims.models.chris.*
import uk.gov.hmrc.charitiesclaims.xml.{XmlAttribute, XmlContent}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.charitiesclaims.connectors.{ClaimsValidationConnector, RdsDatacacheProxyConnector}
import scala.concurrent.Future
import uk.gov.hmrc.charitiesclaims.models.NameOfCharityRegulator
import uk.gov.hmrc.charitiesclaims.models.{CommunityBuilding1, CommunityBuildingsScheduleData, ConnectedCharitiesScheduleData, ConnectedCharity, FileUploadReference, GetUploadResultValidatedCommunityBuildings, GetUploadResultValidatedConnectedCharities}

class ChRISSubmissionServiceSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with MockFactory {

  case class TestCurrentUser(
    affinityGroup: AffinityGroup,
    userId: String,
    enrolmentIdentifierValue: String,
    enrolmentIdentifierKey: String
  ) extends models.CurrentUser

  given HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("test-session-id")))

  "ChRISSubmissionService" should {
    "build a ChRISSubmission correctly for an organisation user claiming gift aid" in {
      val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
      val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
      val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

      val claim = models.Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = true,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = models.ClaimData(
          repaymentClaimDetails = models.RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false,
            claimReferenceNumber = Some("test-claim-reference-number")
          )
        )
      )

      val currentUser = TestCurrentUser(
        affinityGroup = AffinityGroup.Organisation,
        userId = "test-user-id",
        enrolmentIdentifierValue = "test-enrolment-identifier-value",
        enrolmentIdentifierKey = "test-enrolment-identifier-key"
      )

      val submision = service.buildChRISSubmission(claim, currentUser).futureValue

      submision.GovTalkDetails shouldBe GovTalkDetails(
        Keys = List(
          Key(Type = "CredentialID", Value = "test-user-id"),
          Key(Type = "test-enrolment-identifier-key", Value = "test-enrolment-identifier-value"),
          Key(Type = "SessionID", Value = "test-session-id")
        )
      )
    }

    "build a ChRISSubmission correctly for an organisation - Regulator = EnglandAndWales, Corporate Trustee = true and address in UK " in {
      val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
      val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
      val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

      val claim = models.Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = true,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = models.ClaimData(
          repaymentClaimDetails = models.RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false,
            claimReferenceNumber = Some("test-claim-reference-number")
          ),
          organisationDetails = Some(
            models.OrganisationDetails(
              nameOfCharityRegulator = NameOfCharityRegulator.EnglandAndWales,
              reasonNotRegisteredWithRegulator = None,
              charityRegistrationNumber = Some("123456"),
              areYouACorporateTrustee = true,
              doYouHaveCorporateTrusteeUKAddress = Some(true),
              doYouHaveAuthorisedOfficialTrusteeUKAddress = Some(false),
              nameOfCorporateTrustee = Some("Test-Corporate-Trustee"),
              corporateTrusteePostcode = Some("post-code"),
              corporateTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteePostcode = Some("post-code"),
              authorisedOfficialTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteeTitle = Some("Mr"),
              authorisedOfficialTrusteeFirstName = Some("John"),
              authorisedOfficialTrusteeLastName = Some("Jones")
            )
          )
        )
      )

      val currentUser = TestCurrentUser(
        affinityGroup = AffinityGroup.Organisation,
        userId = "test-user-id",
        enrolmentIdentifierValue = "test-enrolment-identifier-value",
        enrolmentIdentifierKey = "test-enrolment-identifier-key"
      )

      val submissionR68       = service.buildR68(claim, currentUser, None, None, None)
      val submissionOffId     = service.buildOffId(claim)
      val submissionOffName   = service.buildOffName(claim)
      val submissionRegulator = service.buildRegulator(claim)

      submissionR68.AuthOfficial shouldBe Some(
        AuthOfficial(
          Trustee = Some("Test-Corporate-Trustee"),
          OffName = None,
          ClaimNo = None,
          OffID = Some(
            OffID(
              Postcode = Some("post-code"),
              Overseas = None
            )
          ),
          Phone = Some("1234567890")
        )
      )

      submissionRegulator shouldBe Some(
        Regulator(
          RegName = Some(RegulatorName.CCEW),
          NoReg = None,
          RegNo = Some("123456")
        )
      )

      submissionOffName shouldBe None

      submissionOffId shouldBe Some(
        OffID(
          Postcode = Some("post-code"),
          Overseas = None
        )
      )

    }
    "build a ChRISSubmission correctly for an organisation - Regulator = EnglandAndWales, Corporate Trustee = true and address NOT in UK " in {
      val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
      val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
      val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

      val claim = models.Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = true,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = models.ClaimData(
          repaymentClaimDetails = models.RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false,
            claimReferenceNumber = Some("test-claim-reference-number")
          ),
          organisationDetails = Some(
            models.OrganisationDetails(
              nameOfCharityRegulator = NameOfCharityRegulator.EnglandAndWales,
              reasonNotRegisteredWithRegulator = None,
              charityRegistrationNumber = Some("123456"),
              areYouACorporateTrustee = true,
              doYouHaveCorporateTrusteeUKAddress = Some(false),
              doYouHaveAuthorisedOfficialTrusteeUKAddress = Some(false),
              nameOfCorporateTrustee = Some("Test-Corporate-Trustee"),
              corporateTrusteePostcode = Some("post-code"),
              corporateTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteePostcode = Some("post-code"),
              authorisedOfficialTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteeTitle = Some("Mr"),
              authorisedOfficialTrusteeFirstName = Some("John"),
              authorisedOfficialTrusteeLastName = Some("Jones")
            )
          )
        )
      )

      val currentUser = TestCurrentUser(
        affinityGroup = AffinityGroup.Organisation,
        userId = "test-user-id",
        enrolmentIdentifierValue = "test-enrolment-identifier-value",
        enrolmentIdentifierKey = "test-enrolment-identifier-key"
      )

      val submissionR68       = service.buildR68(claim, currentUser, None, None, None)
      val submissionRegulator = service.buildRegulator(claim)

      submissionR68.AuthOfficial shouldBe Some(
        AuthOfficial(
          Trustee = Some("Test-Corporate-Trustee"),
          OffName = None,
          ClaimNo = None,
          OffID = Some(
            OffID(
              Postcode = None,
              Overseas = Some(true)
            )
          ),
          Phone = Some("1234567890")
        )
      )

      submissionRegulator shouldBe Some(
        Regulator(
          RegName = Some(RegulatorName.CCEW),
          NoReg = None,
          RegNo = Some("123456")
        )
      )

    }

    "build a ChRISSubmission correctly for an organisation - Regulator = EnglandAndWales, Corporate Trustee = false and address in UK " in {
      val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
      val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
      val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

      val claim = models.Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = true,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = models.ClaimData(
          repaymentClaimDetails = models.RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false,
            claimReferenceNumber = Some("test-claim-reference-number")
          ),
          organisationDetails = Some(
            models.OrganisationDetails(
              nameOfCharityRegulator = NameOfCharityRegulator.EnglandAndWales,
              reasonNotRegisteredWithRegulator = None,
              charityRegistrationNumber = Some("123456"),
              areYouACorporateTrustee = false,
              doYouHaveCorporateTrusteeUKAddress = Some(true),
              doYouHaveAuthorisedOfficialTrusteeUKAddress = Some(true),
              nameOfCorporateTrustee = Some("Test-Corporate-Trustee"),
              corporateTrusteePostcode = Some("post-code"),
              corporateTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteePostcode = Some("post-code"),
              authorisedOfficialTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteeTitle = Some("Mr"),
              authorisedOfficialTrusteeFirstName = Some("John"),
              authorisedOfficialTrusteeLastName = Some("Jones")
            )
          )
        )
      )

      val currentUser = TestCurrentUser(
        affinityGroup = AffinityGroup.Organisation,
        userId = "test-user-id",
        enrolmentIdentifierValue = "test-enrolment-identifier-value",
        enrolmentIdentifierKey = "test-enrolment-identifier-key"
      )

      val submissionR68       = service.buildR68(claim, currentUser, None, None, None)
      val submissionRegulator = service.buildRegulator(claim)

      submissionR68.AuthOfficial shouldBe Some(
        AuthOfficial(
          Trustee = None,
          OffName = Some(
            OffName(
              Ttl = Some("Mr"),
              Fore = Some("John"),
              Sur = Some("Jones")
            )
          ),
          ClaimNo = None,
          OffID = Some(
            OffID(
              Postcode = Some("post-code"),
              Overseas = None
            )
          ),
          Phone = Some("1234567890")
        )
      )

      submissionRegulator shouldBe Some(
        Regulator(
          RegName = Some(RegulatorName.CCEW),
          NoReg = None,
          RegNo = Some("123456")
        )
      )

    }
    "build a ChRISSubmission correctly for an organisation - Regulator = EnglandAndWales, Corporate Trustee = false and address NOT in UK " in {
      val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
      val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
      val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

      val claim = models.Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = true,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = models.ClaimData(
          repaymentClaimDetails = models.RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false,
            claimReferenceNumber = Some("test-claim-reference-number")
          ),
          organisationDetails = Some(
            models.OrganisationDetails(
              nameOfCharityRegulator = NameOfCharityRegulator.EnglandAndWales,
              reasonNotRegisteredWithRegulator = None,
              charityRegistrationNumber = Some("123456"),
              areYouACorporateTrustee = false,
              doYouHaveCorporateTrusteeUKAddress = Some(false),
              doYouHaveAuthorisedOfficialTrusteeUKAddress = Some(false),
              nameOfCorporateTrustee = Some("Test-Corporate-Trustee"),
              corporateTrusteePostcode = Some("post-code"),
              corporateTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteePostcode = Some("post-code"),
              authorisedOfficialTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteeTitle = Some("Mr"),
              authorisedOfficialTrusteeFirstName = Some("John"),
              authorisedOfficialTrusteeLastName = Some("Jones")
            )
          )
        )
      )

      val currentUser = TestCurrentUser(
        affinityGroup = AffinityGroup.Organisation,
        userId = "test-user-id",
        enrolmentIdentifierValue = "test-enrolment-identifier-value",
        enrolmentIdentifierKey = "test-enrolment-identifier-key"
      )

      val submissionR68 = service.buildR68(claim, currentUser, None, None, None)

      submissionR68.AuthOfficial shouldBe Some(
        AuthOfficial(
          Trustee = None,
          OffName = Some(
            OffName(
              Ttl = Some("Mr"),
              Fore = Some("John"),
              Sur = Some("Jones")
            )
          ),
          ClaimNo = None,
          OffID = Some(
            OffID(
              Postcode = None,
              Overseas = Some(true)
            )
          ),
          Phone = Some("1234567890")
        )
      )
    }

    "build a ChRISSubmission correctly for an organisation - Regulator = NorthernIreland, Corporate Trustee = false and address in UK " in {
      val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
      val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
      val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

      val claim = models.Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = true,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = models.ClaimData(
          repaymentClaimDetails = models.RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false,
            claimReferenceNumber = Some("test-claim-reference-number")
          ),
          organisationDetails = Some(
            models.OrganisationDetails(
              nameOfCharityRegulator = NameOfCharityRegulator.NorthernIreland,
              reasonNotRegisteredWithRegulator = None,
              charityRegistrationNumber = Some("123456"),
              areYouACorporateTrustee = false,
              doYouHaveCorporateTrusteeUKAddress = Some(true),
              doYouHaveAuthorisedOfficialTrusteeUKAddress = Some(true),
              nameOfCorporateTrustee = Some("Test-Corporate-Trustee"),
              corporateTrusteePostcode = Some("post-code"),
              corporateTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteePostcode = Some("post-code"),
              authorisedOfficialTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteeTitle = Some("Mr"),
              authorisedOfficialTrusteeFirstName = Some("John"),
              authorisedOfficialTrusteeLastName = Some("Jones")
            )
          )
        )
      )

      val currentUser = TestCurrentUser(
        affinityGroup = AffinityGroup.Organisation,
        userId = "test-user-id",
        enrolmentIdentifierValue = "test-enrolment-identifier-value",
        enrolmentIdentifierKey = "test-enrolment-identifier-key"
      )

      val submissionR68       = service.buildR68(claim, currentUser, None, None, None)
      val submissionRegulator = service.buildRegulator(claim)

      submissionR68.AuthOfficial shouldBe Some(
        AuthOfficial(
          Trustee = None,
          OffName = Some(
            OffName(
              Ttl = Some("Mr"),
              Fore = Some("John"),
              Sur = Some("Jones")
            )
          ),
          ClaimNo = None,
          OffID = Some(
            OffID(
              Postcode = Some("post-code"),
              Overseas = None
            )
          ),
          Phone = Some("1234567890")
        )
      )

      submissionRegulator shouldBe Some(
        Regulator(
          RegName = Some(RegulatorName.CCNI),
          NoReg = None,
          RegNo = Some("123456")
        )
      )
    }

    "build a ChRISSubmission correctly for an organisation - Regulator = Scottish, Corporate Trustee = false and address in UK " in {
      val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
      val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
      val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

      val claim = models.Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = true,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = models.ClaimData(
          repaymentClaimDetails = models.RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false,
            claimReferenceNumber = Some("test-claim-reference-number")
          ),
          organisationDetails = Some(
            models.OrganisationDetails(
              nameOfCharityRegulator = NameOfCharityRegulator.Scottish,
              reasonNotRegisteredWithRegulator = None,
              charityRegistrationNumber = Some("123456"),
              areYouACorporateTrustee = false,
              doYouHaveCorporateTrusteeUKAddress = Some(true),
              doYouHaveAuthorisedOfficialTrusteeUKAddress = Some(true),
              nameOfCorporateTrustee = Some("Test-Corporate-Trustee"),
              corporateTrusteePostcode = Some("post-code"),
              corporateTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteePostcode = Some("post-code"),
              authorisedOfficialTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteeTitle = Some("Mr"),
              authorisedOfficialTrusteeFirstName = Some("John"),
              authorisedOfficialTrusteeLastName = Some("Jones")
            )
          )
        )
      )

      val currentUser = TestCurrentUser(
        affinityGroup = AffinityGroup.Organisation,
        userId = "test-user-id",
        enrolmentIdentifierValue = "test-enrolment-identifier-value",
        enrolmentIdentifierKey = "test-enrolment-identifier-key"
      )

      val submissionR68       = service.buildR68(claim, currentUser, None, None, None)
      val submissionRegulator = service.buildRegulator(claim)

      submissionR68.AuthOfficial shouldBe Some(
        AuthOfficial(
          Trustee = None,
          OffName = Some(
            OffName(
              Ttl = Some("Mr"),
              Fore = Some("John"),
              Sur = Some("Jones")
            )
          ),
          ClaimNo = None,
          OffID = Some(
            OffID(
              Postcode = Some("post-code"),
              Overseas = None
            )
          ),
          Phone = Some("1234567890")
        )
      )

      submissionRegulator shouldBe Some(
        Regulator(
          RegName = Some(RegulatorName.OSCR),
          NoReg = None,
          RegNo = Some("123456")
        )
      )
    }

    "build a ChRISSubmission correctly for an organisation - Regulator = None, Corporate Trustee = false and address in UK " in {
      val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
      val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
      val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

      val claim = models.Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = true,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = models.ClaimData(
          repaymentClaimDetails = models.RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false,
            claimReferenceNumber = Some("test-claim-reference-number")
          ),
          organisationDetails = Some(
            models.OrganisationDetails(
              nameOfCharityRegulator = NameOfCharityRegulator.None,
              reasonNotRegisteredWithRegulator = None,
              charityRegistrationNumber = Some("123456"),
              areYouACorporateTrustee = false,
              doYouHaveCorporateTrusteeUKAddress = Some(true),
              doYouHaveAuthorisedOfficialTrusteeUKAddress = Some(true),
              nameOfCorporateTrustee = Some("Test-Corporate-Trustee"),
              corporateTrusteePostcode = Some("post-code"),
              corporateTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteePostcode = Some("post-code"),
              authorisedOfficialTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteeTitle = Some("Mr"),
              authorisedOfficialTrusteeFirstName = Some("John"),
              authorisedOfficialTrusteeLastName = Some("Jones")
            )
          )
        )
      )

      val currentUser = TestCurrentUser(
        affinityGroup = AffinityGroup.Organisation,
        userId = "test-user-id",
        enrolmentIdentifierValue = "test-enrolment-identifier-value",
        enrolmentIdentifierKey = "test-enrolment-identifier-key"
      )

      val submissionR68       = service.buildR68(claim, currentUser, None, None, None)
      val submissionRegulator = service.buildRegulator(claim)

      submissionR68.AuthOfficial shouldBe Some(
        AuthOfficial(
          Trustee = None,
          OffName = Some(
            OffName(
              Ttl = Some("Mr"),
              Fore = Some("John"),
              Sur = Some("Jones")
            )
          ),
          ClaimNo = None,
          OffID = Some(
            OffID(
              Postcode = Some("post-code"),
              Overseas = None
            )
          ),
          Phone = Some("1234567890")
        )
      )

      submissionRegulator shouldBe Some(
        Regulator(
          RegName = None,
          NoReg = Some(true),
          RegNo = Some("123456")
        )
      )
    }

    "build a ChRISSubmission correctly for an agent - Regulator = None, Corporate Trustee = false and address in UK " in {
      val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
      val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
      val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

      (rdsConnectorMock
        .getAgentName(_: String)(using _: HeaderCarrier))
        .expects("test-enrolment-identifier-value", *)
        .returns(Future.successful(Some("Test-Agent-Name")))

      val claim = models.Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = true,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = models.ClaimData(
          repaymentClaimDetails = models.RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false,
            claimReferenceNumber = Some("test-claim-reference-number")
          ),
          organisationDetails = Some(
            models.OrganisationDetails(
              nameOfCharityRegulator = NameOfCharityRegulator.None,
              reasonNotRegisteredWithRegulator = None,
              charityRegistrationNumber = Some("123456"),
              areYouACorporateTrustee = false,
              doYouHaveCorporateTrusteeUKAddress = Some(true),
              doYouHaveAuthorisedOfficialTrusteeUKAddress = Some(true),
              nameOfCorporateTrustee = Some("Test-Corporate-Trustee"),
              corporateTrusteePostcode = Some("post-code"),
              corporateTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteePostcode = Some("post-code"),
              authorisedOfficialTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              authorisedOfficialTrusteeTitle = Some("Mr"),
              authorisedOfficialTrusteeFirstName = Some("John"),
              authorisedOfficialTrusteeLastName = Some("Jones")
            )
          )
        )
      )

      val currentUser = TestCurrentUser(
        affinityGroup = AffinityGroup.Agent,
        userId = "test-user-id",
        enrolmentIdentifierValue = "test-enrolment-identifier-value",
        enrolmentIdentifierKey = "test-enrolment-identifier-key"
      )

      val submission = service.buildChRISSubmission(claim, currentUser).futureValue

      val submissionR68       = service.buildR68(claim, currentUser, Some("Test-Agent-Name"), None, None)
      val submissionRegulator = service.buildRegulator(claim)

      submission.Body.IRenvelope.R68 shouldBe submissionR68

      submissionR68.AuthOfficial shouldBe None
      submissionR68.AgtOrNom     shouldBe Some(
        AgtOrNom(
          OrgName = "Test-Agent-Name",
          RefNo = "test-enrolment-identifier-value",
          ClaimNo = None,
          PayToAoN = None,
          AoNID = None,
          Phone = "1234567890"
        )
      )

      submissionRegulator shouldBe Some(
        Regulator(
          RegName = None,
          NoReg = Some(true),
          RegNo = Some("123456")
        )
      )
    }

    "buildChRISSubmission fetches upload data from validation service when GASDS upload references are present" in {
      val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
      val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
      val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

      val communityBuildingsRef = FileUploadReference("cb-ref-123")
      val connectedCharitiesRef = FileUploadReference("cc-ref-456")

      val claim = models.Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = false,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = models.ClaimData(
          repaymentClaimDetails = models.RepaymentClaimDetails(
            claimingGiftAid = false,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = true,
            connectedToAnyOtherCharities = Some(true),
            claimingDonationsCollectedInCommunityBuildings = Some(true)
          ),
          giftAidSmallDonationsSchemeDonationDetails = Some(
            models.GiftAidSmallDonationsSchemeDonationDetails(
              adjustmentForGiftAidOverClaimed = BigDecimal("0"),
              claims = Seq(
                models
                  .GiftAidSmallDonationsSchemeClaim(taxYear = 2024, amountOfDonationsReceived = BigDecimal("100.00"))
              )
            )
          ),
          communityBuildingsScheduleFileUploadReference = Some(communityBuildingsRef),
          connectedCharitiesScheduleFileUploadReference = Some(connectedCharitiesRef)
        )
      )

      val communityBuildingsData = CommunityBuildingsScheduleData(
        totalOfAllAmounts = BigDecimal("500.00"),
        communityBuildings = Seq(
          CommunityBuilding1(
            communityBuildingItem = 1,
            buildingName = "Village Hall",
            firstLineOfAddress = "1 High Street",
            postcode = "AB1 2CD",
            taxYear1 = 2024,
            amountYear1 = BigDecimal("500.00")
          )
        )
      )

      val connectedCharitiesData = ConnectedCharitiesScheduleData(
        charities = Seq(
          ConnectedCharity(charityItem = 1, charityName = "Charity One", charityReference = "X95442")
        )
      )

      (claimsValidationConnectorMock
        .getUploadResult(_: String, _: FileUploadReference)(using _: HeaderCarrier))
        .expects("test-claim-id", communityBuildingsRef, *)
        .returns(
          Future.successful(
            Some(GetUploadResultValidatedCommunityBuildings(communityBuildingsRef, communityBuildingsData))
          )
        )

      (claimsValidationConnectorMock
        .getUploadResult(_: String, _: FileUploadReference)(using _: HeaderCarrier))
        .expects("test-claim-id", connectedCharitiesRef, *)
        .returns(
          Future.successful(
            Some(GetUploadResultValidatedConnectedCharities(connectedCharitiesRef, connectedCharitiesData))
          )
        )

      val currentUser = TestCurrentUser(
        affinityGroup = AffinityGroup.Organisation,
        userId = "test-user-id",
        enrolmentIdentifierValue = "test-enrolment-identifier-value",
        enrolmentIdentifierKey = "test-enrolment-identifier-key"
      )

      val result = service.buildChRISSubmission(claim, currentUser).futureValue

      result.Body.IRenvelope.R68.Claim.GiftAidSmallDonationsScheme.get.Charity shouldBe Some(
        List(Charity(Name = "Charity One", HMRCref = "X95442"))
      )

      result.Body.IRenvelope.R68.Claim.GiftAidSmallDonationsScheme.get.Building shouldBe Some(
        List(
          Building(
            BldgName = "Village Hall",
            Address = "1 High Street",
            Postcode = "AB1 2CD",
            BldgClaim = List(BldgClaim(Year = "2024", Amount = BigDecimal("500.00")))
          )
        )
      )
    }

    "buildGiftAidSmallDonationsScheme" should {

      "populate GASDS with upload data from validation service" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = false,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = true,
              connectedToAnyOtherCharities = Some(true),
              claimingDonationsCollectedInCommunityBuildings = Some(true),
              makingAdjustmentToPreviousClaim = Some(true)
            ),
            giftAidSmallDonationsSchemeDonationDetails = Some(
              models.GiftAidSmallDonationsSchemeDonationDetails(
                adjustmentForGiftAidOverClaimed = BigDecimal("56.89"),
                claims = Seq(
                  models
                    .GiftAidSmallDonationsSchemeClaim(taxYear = 2024, amountOfDonationsReceived = BigDecimal("67.09"))
                )
              )
            )
          )
        )

        val connectedCharitiesData = Some(
          ConnectedCharitiesScheduleData(
            charities = Seq(
              ConnectedCharity(charityItem = 1, charityName = "Charity One", charityReference = "X95442")
            )
          )
        )

        val communityBuildingsData = Some(
          CommunityBuildingsScheduleData(
            totalOfAllAmounts = BigDecimal("1757.21"),
            communityBuildings = Seq(
              CommunityBuilding1(
                communityBuildingItem = 1,
                buildingName = "YMCA",
                firstLineOfAddress = "123 New Street",
                postcode = "AB12 3CD",
                taxYear1 = 2024,
                amountYear1 = BigDecimal("1257.21"),
                taxYear2 = Some(2023),
                amountYear2 = Some(BigDecimal("500.00"))
              )
            )
          )
        )

        val currentUser = TestCurrentUser(
          affinityGroup = AffinityGroup.Organisation,
          userId = "test-user-id",
          enrolmentIdentifierValue = "test-enrolment-identifier-value",
          enrolmentIdentifierKey = "test-enrolment-identifier-key"
        )

        val result = service.buildClaim(claim, currentUser, connectedCharitiesData, communityBuildingsData)
        result.GiftAidSmallDonationsScheme shouldBe Some(
          GiftAidSmallDonationsScheme(
            ConnectedCharities = true,
            Charity = Some(List(Charity(Name = "Charity One", HMRCref = "X95442"))),
            GiftAidSmallDonationsSchemeClaim = Some(
              List(
                GiftAidSmallDonationsSchemeClaim(Year = Some("2024"), Amount = Some(BigDecimal("67.09")))
              )
            ),
            CommBldgs = Some(true),
            Building = Some(
              List(
                Building(
                  BldgName = "YMCA",
                  Address = "123 New Street",
                  Postcode = "AB12 3CD",
                  BldgClaim = List(
                    BldgClaim(Year = "2024", Amount = BigDecimal("1257.21")),
                    BldgClaim(Year = "2023", Amount = BigDecimal("500.00"))
                  )
                )
              )
            ),
            Adj = Some("56.89")
          )
        )
      }

      "return None when claimingUnderGiftAidSmallDonationsScheme is false" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = false,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = false,
              connectedToAnyOtherCharities = Some(true)
            ),
            giftAidSmallDonationsSchemeDonationDetails = Some(
              models.GiftAidSmallDonationsSchemeDonationDetails(
                adjustmentForGiftAidOverClaimed = BigDecimal("10.00"),
                claims = Seq(
                  models
                    .GiftAidSmallDonationsSchemeClaim(taxYear = 2024, amountOfDonationsReceived = BigDecimal("50.00"))
                )
              )
            )
          )
        )

        val currentUser = TestCurrentUser(
          affinityGroup = AffinityGroup.Organisation,
          userId = "test-user-id",
          enrolmentIdentifierValue = "test-enrolment-identifier-value",
          enrolmentIdentifierKey = "test-enrolment-identifier-key"
        )

        val result = service.buildClaim(claim, currentUser, None, None)
        result.GiftAidSmallDonationsScheme shouldBe None
      }

      "return None when giftAidSmallDonationsSchemeDonationDetails is None" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = false,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = true
            ),
            giftAidSmallDonationsSchemeDonationDetails = None
          )
        )

        val currentUser = TestCurrentUser(
          affinityGroup = AffinityGroup.Organisation,
          userId = "test-user-id",
          enrolmentIdentifierValue = "test-enrolment-identifier-value",
          enrolmentIdentifierKey = "test-enrolment-identifier-key"
        )

        val result = service.buildClaim(claim, currentUser, None, None)
        result.GiftAidSmallDonationsScheme shouldBe None
      }

      "map multiple connected charities from upload data correctly" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = false,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = true,
              connectedToAnyOtherCharities = Some(true),
              claimingDonationsCollectedInCommunityBuildings = Some(false)
            ),
            giftAidSmallDonationsSchemeDonationDetails = Some(
              models.GiftAidSmallDonationsSchemeDonationDetails(
                adjustmentForGiftAidOverClaimed = BigDecimal("0"),
                claims = Seq.empty
              )
            )
          )
        )

        val connectedCharitiesData = Some(
          ConnectedCharitiesScheduleData(
            charities = Seq(
              ConnectedCharity(charityItem = 1, charityName = "Charity One", charityReference = "X95442"),
              ConnectedCharity(charityItem = 2, charityName = "Charity Two", charityReference = "Y12345")
            )
          )
        )

        val currentUser = TestCurrentUser(
          affinityGroup = AffinityGroup.Organisation,
          userId = "test-user-id",
          enrolmentIdentifierValue = "test-enrolment-identifier-value",
          enrolmentIdentifierKey = "test-enrolment-identifier-key"
        )

        val result = service.buildClaim(claim, currentUser, connectedCharitiesData, None)
        result.GiftAidSmallDonationsScheme.get.Charity shouldBe Some(
          List(
            Charity(Name = "Charity One", HMRCref = "X95442"),
            Charity(Name = "Charity Two", HMRCref = "Y12345")
          )
        )
      }

      "return Charity as None when no upload data for connected charities" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = false,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = true,
              connectedToAnyOtherCharities = Some(false),
              claimingDonationsCollectedInCommunityBuildings = Some(false)
            ),
            giftAidSmallDonationsSchemeDonationDetails = Some(
              models.GiftAidSmallDonationsSchemeDonationDetails(
                adjustmentForGiftAidOverClaimed = BigDecimal("0"),
                claims = Seq.empty
              )
            )
          )
        )

        val currentUser = TestCurrentUser(
          affinityGroup = AffinityGroup.Organisation,
          userId = "test-user-id",
          enrolmentIdentifierValue = "test-enrolment-identifier-value",
          enrolmentIdentifierKey = "test-enrolment-identifier-key"
        )

        val result = service.buildClaim(claim, currentUser, None, None)
        result.GiftAidSmallDonationsScheme.get.Charity shouldBe None
      }

      "map GiftAidSmallDonationsSchemeClaim correctly" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = false,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = true,
              connectedToAnyOtherCharities = Some(false),
              claimingDonationsCollectedInCommunityBuildings = Some(false)
            ),
            giftAidSmallDonationsSchemeDonationDetails = Some(
              models.GiftAidSmallDonationsSchemeDonationDetails(
                adjustmentForGiftAidOverClaimed = BigDecimal("0"),
                claims = Seq(
                  models
                    .GiftAidSmallDonationsSchemeClaim(taxYear = 2024, amountOfDonationsReceived = BigDecimal("67.09")),
                  models
                    .GiftAidSmallDonationsSchemeClaim(taxYear = 2023, amountOfDonationsReceived = BigDecimal("120.50"))
                )
              )
            )
          )
        )

        val currentUser = TestCurrentUser(
          affinityGroup = AffinityGroup.Organisation,
          userId = "test-user-id",
          enrolmentIdentifierValue = "test-enrolment-identifier-value",
          enrolmentIdentifierKey = "test-enrolment-identifier-key"
        )

        val result = service.buildClaim(claim, currentUser, None, None)
        result.GiftAidSmallDonationsScheme.get.GiftAidSmallDonationsSchemeClaim shouldBe Some(
          List(
            GiftAidSmallDonationsSchemeClaim(Year = Some("2024"), Amount = Some(BigDecimal("67.09"))),
            GiftAidSmallDonationsSchemeClaim(Year = Some("2023"), Amount = Some(BigDecimal("120.50")))
          )
        )
      }

      "map CommunityBuilding1 with 2 years from upload data" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = false,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = true,
              connectedToAnyOtherCharities = Some(false),
              claimingDonationsCollectedInCommunityBuildings = Some(true)
            ),
            giftAidSmallDonationsSchemeDonationDetails = Some(
              models.GiftAidSmallDonationsSchemeDonationDetails(
                adjustmentForGiftAidOverClaimed = BigDecimal("0"),
                claims = Seq.empty
              )
            )
          )
        )

        val communityBuildingsData = Some(
          CommunityBuildingsScheduleData(
            totalOfAllAmounts = BigDecimal("1757.21"),
            communityBuildings = Seq(
              CommunityBuilding1(
                communityBuildingItem = 1,
                buildingName = "YMCA",
                firstLineOfAddress = "123 New Street",
                postcode = "AB12 3CD",
                taxYear1 = 2024,
                amountYear1 = BigDecimal("1257.21"),
                taxYear2 = Some(2023),
                amountYear2 = Some(BigDecimal("500.00"))
              )
            )
          )
        )

        val currentUser = TestCurrentUser(
          affinityGroup = AffinityGroup.Organisation,
          userId = "test-user-id",
          enrolmentIdentifierValue = "test-enrolment-identifier-value",
          enrolmentIdentifierKey = "test-enrolment-identifier-key"
        )

        val result = service.buildClaim(claim, currentUser, None, communityBuildingsData)
        result.GiftAidSmallDonationsScheme.get.Building shouldBe Some(
          List(
            Building(
              BldgName = "YMCA",
              Address = "123 New Street",
              Postcode = "AB12 3CD",
              BldgClaim = List(
                BldgClaim(Year = "2024", Amount = BigDecimal("1257.21")),
                BldgClaim(Year = "2023", Amount = BigDecimal("500.00"))
              )
            )
          )
        )
      }

      "map CommunityBuilding1 with 1 year only from upload data" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = false,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = true,
              connectedToAnyOtherCharities = Some(false),
              claimingDonationsCollectedInCommunityBuildings = Some(true)
            ),
            giftAidSmallDonationsSchemeDonationDetails = Some(
              models.GiftAidSmallDonationsSchemeDonationDetails(
                adjustmentForGiftAidOverClaimed = BigDecimal("0"),
                claims = Seq.empty
              )
            )
          )
        )

        val communityBuildingsData = Some(
          CommunityBuildingsScheduleData(
            totalOfAllAmounts = BigDecimal("500.00"),
            communityBuildings = Seq(
              CommunityBuilding1(
                communityBuildingItem = 1,
                buildingName = "Village Hall",
                firstLineOfAddress = "1 High Street",
                postcode = "AB1 2CD",
                taxYear1 = 2024,
                amountYear1 = BigDecimal("500.00")
              )
            )
          )
        )

        val currentUser = TestCurrentUser(
          affinityGroup = AffinityGroup.Organisation,
          userId = "test-user-id",
          enrolmentIdentifierValue = "test-enrolment-identifier-value",
          enrolmentIdentifierKey = "test-enrolment-identifier-key"
        )

        val result = service.buildClaim(claim, currentUser, None, communityBuildingsData)
        result.GiftAidSmallDonationsScheme.get.Building shouldBe Some(
          List(
            Building(
              BldgName = "Village Hall",
              Address = "1 High Street",
              Postcode = "AB1 2CD",
              BldgClaim = List(
                BldgClaim(Year = "2024", Amount = BigDecimal("500.00"))
              )
            )
          )
        )
      }

      "return Building as None when no upload data for community buildings" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = false,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = true,
              connectedToAnyOtherCharities = Some(false),
              claimingDonationsCollectedInCommunityBuildings = Some(false)
            ),
            giftAidSmallDonationsSchemeDonationDetails = Some(
              models.GiftAidSmallDonationsSchemeDonationDetails(
                adjustmentForGiftAidOverClaimed = BigDecimal("0"),
                claims = Seq.empty
              )
            )
          )
        )

        val currentUser = TestCurrentUser(
          affinityGroup = AffinityGroup.Organisation,
          userId = "test-user-id",
          enrolmentIdentifierValue = "test-enrolment-identifier-value",
          enrolmentIdentifierKey = "test-enrolment-identifier-key"
        )

        val result = service.buildClaim(claim, currentUser, None, None)
        result.GiftAidSmallDonationsScheme.get.Building shouldBe None
      }

      "set Adj when adjustment > 0 and None when 0" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val claimWithAdj = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = false,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = true,
              connectedToAnyOtherCharities = Some(false),
              claimingDonationsCollectedInCommunityBuildings = Some(false)
            ),
            giftAidSmallDonationsSchemeDonationDetails = Some(
              models.GiftAidSmallDonationsSchemeDonationDetails(
                adjustmentForGiftAidOverClaimed = BigDecimal("56.89"),
                claims = Seq.empty
              )
            )
          )
        )

        val claimWithZeroAdj = claimWithAdj.copy(
          claimData = claimWithAdj.claimData.copy(
            giftAidSmallDonationsSchemeDonationDetails = Some(
              models.GiftAidSmallDonationsSchemeDonationDetails(
                adjustmentForGiftAidOverClaimed = BigDecimal("0"),
                claims = Seq.empty
              )
            )
          )
        )

        val currentUser = TestCurrentUser(
          affinityGroup = AffinityGroup.Organisation,
          userId = "test-user-id",
          enrolmentIdentifierValue = "test-enrolment-identifier-value",
          enrolmentIdentifierKey = "test-enrolment-identifier-key"
        )

        val resultWithAdj = service.buildClaim(claimWithAdj, currentUser, None, None)
        resultWithAdj.GiftAidSmallDonationsScheme.get.Adj shouldBe Some("56.89")

        val resultWithZeroAdj = service.buildClaim(claimWithZeroAdj, currentUser, None, None)
        resultWithZeroAdj.GiftAidSmallDonationsScheme.get.Adj shouldBe None
      }

      "map ConnectedCharities yes/no from repaymentClaimDetails" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        def buildClaimWithConnected(connected: Option[Boolean]): models.Claim =
          models.Claim(
            claimId = "test-claim-id",
            userId = "test-user-id",
            claimSubmitted = false,
            lastUpdatedReference = UUID.randomUUID().toString,
            claimData = models.ClaimData(
              repaymentClaimDetails = models.RepaymentClaimDetails(
                claimingGiftAid = false,
                claimingTaxDeducted = false,
                claimingUnderGiftAidSmallDonationsScheme = true,
                connectedToAnyOtherCharities = connected,
                claimingDonationsCollectedInCommunityBuildings = Some(false)
              ),
              giftAidSmallDonationsSchemeDonationDetails = Some(
                models.GiftAidSmallDonationsSchemeDonationDetails(
                  adjustmentForGiftAidOverClaimed = BigDecimal("0"),
                  claims = Seq.empty
                )
              )
            )
          )

        val currentUser = TestCurrentUser(
          affinityGroup = AffinityGroup.Organisation,
          userId = "test-user-id",
          enrolmentIdentifierValue = "test-enrolment-identifier-value",
          enrolmentIdentifierKey = "test-enrolment-identifier-key"
        )

        val resultYes = service.buildClaim(buildClaimWithConnected(Some(true)), currentUser, None, None)
        resultYes.GiftAidSmallDonationsScheme.get.ConnectedCharities shouldBe (true: YesNo)

        val resultNo = service.buildClaim(buildClaimWithConnected(Some(false)), currentUser, None, None)
        resultNo.GiftAidSmallDonationsScheme.get.ConnectedCharities shouldBe (false: YesNo)

        val resultNone = service.buildClaim(buildClaimWithConnected(None), currentUser, None, None)
        resultNone.GiftAidSmallDonationsScheme.get.ConnectedCharities shouldBe (false: YesNo)
      }

      "map CommBldgs yes/no from repaymentClaimDetails" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        def buildClaimWithCommBldgs(claiming: Option[Boolean]): models.Claim =
          models.Claim(
            claimId = "test-claim-id",
            userId = "test-user-id",
            claimSubmitted = false,
            lastUpdatedReference = UUID.randomUUID().toString,
            claimData = models.ClaimData(
              repaymentClaimDetails = models.RepaymentClaimDetails(
                claimingGiftAid = false,
                claimingTaxDeducted = false,
                claimingUnderGiftAidSmallDonationsScheme = true,
                connectedToAnyOtherCharities = Some(false),
                claimingDonationsCollectedInCommunityBuildings = claiming
              ),
              giftAidSmallDonationsSchemeDonationDetails = Some(
                models.GiftAidSmallDonationsSchemeDonationDetails(
                  adjustmentForGiftAidOverClaimed = BigDecimal("0"),
                  claims = Seq.empty
                )
              )
            )
          )

        val currentUser = TestCurrentUser(
          affinityGroup = AffinityGroup.Organisation,
          userId = "test-user-id",
          enrolmentIdentifierValue = "test-enrolment-identifier-value",
          enrolmentIdentifierKey = "test-enrolment-identifier-key"
        )

        val resultYes = service.buildClaim(buildClaimWithCommBldgs(Some(true)), currentUser, None, None)
        resultYes.GiftAidSmallDonationsScheme.get.CommBldgs shouldBe Some(true: YesNo)

        val resultNo = service.buildClaim(buildClaimWithCommBldgs(Some(false)), currentUser, None, None)
        resultNo.GiftAidSmallDonationsScheme.get.CommBldgs shouldBe Some(false: YesNo)

        val resultNone = service.buildClaim(buildClaimWithCommBldgs(None), currentUser, None, None)
        resultNone.GiftAidSmallDonationsScheme.get.CommBldgs shouldBe Some(false: YesNo)
      }

    }

  }
}
