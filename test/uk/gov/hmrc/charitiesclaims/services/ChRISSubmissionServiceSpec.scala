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

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.charitiesclaims.connectors.{ClaimsValidationConnector, RdsDatacacheProxyConnector}

import scala.concurrent.Future
import uk.gov.hmrc.charitiesclaims.models.{CommunityBuilding, CommunityBuildingsScheduleData, ConnectedCharitiesScheduleData, ConnectedCharity, Donation, FileUploadReference, GetUploadResultValidatedCommunityBuildings, GetUploadResultValidatedConnectedCharities, GetUploadResultValidatedGiftAid, GetUploadResultValidatedOtherIncome, GiftAidScheduleData, NameOfCharityRegulator, OtherIncome, OtherIncomeScheduleData, ScheduleData}

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

  val organisationUser: models.CurrentUser = TestCurrentUser(
    affinityGroup = AffinityGroup.Organisation,
    userId = "test-user-id",
    enrolmentIdentifierValue = "test-enrolment-identifier-value",
    enrolmentIdentifierKey = "test-enrolment-identifier-key"
  )

  val orgName: Option[String]     = Some("test-session-id")
  val declarationLanguage: String = "cy"

  val agentUser: models.CurrentUser = TestCurrentUser(
    affinityGroup = AffinityGroup.Agent,
    userId = "test-user-id",
    enrolmentIdentifierValue = "test-enrolment-identifier-value",
    enrolmentIdentifierKey = "test-enrolment-identifier-key"
  )

  val CHOrganisationUser: models.CurrentUser = TestCurrentUser(
    affinityGroup = AffinityGroup.Organisation,
    userId = "test-user-id",
    enrolmentIdentifierValue = "CH-test-enrolment-identifier-value",
    enrolmentIdentifierKey = "test-enrolment-identifier-key"
  )

  val CFOrganisationUser: models.CurrentUser = TestCurrentUser(
    affinityGroup = AffinityGroup.Organisation,
    userId = "test-user-id",
    enrolmentIdentifierValue = "CH-test-enrolment-identifier-value",
    enrolmentIdentifierKey = "test-enrolment-identifier-key"
  )

  "ChRISSubmissionService" should {
    "build a ChRISSubmission correctly for an organisation user claiming gift aid" in {
      val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
      val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
      val service                       =
        new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

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

      val currentUser = organisationUser

      (rdsConnectorMock
        .getOrganisationName(_: String)(using _: HeaderCarrier))
        .expects(currentUser.enrolmentIdentifierValue, *)
        .returns(
          Future.successful(orgName)
        )

      val submission = service.buildChRISSubmission(claim, currentUser, declarationLanguage).futureValue

      submission.GovTalkDetails shouldBe GovTalkDetails(
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

      val currentUser = organisationUser

      val submissionR68       = service.buildR68(claim, currentUser, orgName, declarationLanguage, ScheduleData.empty)
      val submissionOffId     = service.buildOffId(claim)
      val submissionOffName   = service.buildOffName(claim)
      val submissionRegulator = service.buildRegulator(claim, organisationUser.enrolmentIdentifierValue)

      submissionR68.AuthOfficial shouldBe Some(
        AuthOfficial(
          Trustee = Some("Test-Corporate-Trustee"),
          OffName = None,
          ClaimNo = Some("test-claim-reference-number"),
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

      val currentUser = organisationUser

      val submissionR68       = service.buildR68(claim, currentUser, orgName, declarationLanguage, ScheduleData.empty)
      val submissionRegulator = service.buildRegulator(claim, organisationUser.enrolmentIdentifierValue)

      submissionR68.AuthOfficial shouldBe Some(
        AuthOfficial(
          Trustee = Some("Test-Corporate-Trustee"),
          OffName = None,
          ClaimNo = Some("test-claim-reference-number"),
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

      val currentUser = organisationUser

      val submissionR68       = service.buildR68(claim, currentUser, orgName, declarationLanguage, ScheduleData.empty)
      val submissionRegulator = service.buildRegulator(claim, organisationUser.enrolmentIdentifierValue)

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
          ClaimNo = Some("test-claim-reference-number"),
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

      val currentUser = organisationUser

      val submissionR68 = service.buildR68(claim, currentUser, orgName, declarationLanguage, ScheduleData.empty)

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
          ClaimNo = Some("test-claim-reference-number"),
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

      val currentUser = organisationUser

      val submissionR68       = service.buildR68(claim, currentUser, orgName, declarationLanguage, ScheduleData.empty)
      val submissionRegulator = service.buildRegulator(claim, organisationUser.enrolmentIdentifierValue)

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
          ClaimNo = Some("test-claim-reference-number"),
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

      val currentUser = organisationUser

      val submissionR68       = service.buildR68(claim, currentUser, orgName, declarationLanguage, ScheduleData.empty)
      val submissionRegulator = service.buildRegulator(claim, organisationUser.enrolmentIdentifierValue)

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
          ClaimNo = Some("test-claim-reference-number"),
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

      // val orgName:Option[String] = Some(rdsConnectorMock.getOrganisationName("test"))

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

      val currentUser         = organisationUser
      val declarationLanguage = "en"

      val submissionR68       = service.buildR68(claim, currentUser, orgName, declarationLanguage, ScheduleData.empty)
      val submissionRegulator = service.buildRegulator(claim, organisationUser.enrolmentIdentifierValue)

      submissionR68.WelshSubmission shouldBe None
      submissionR68.AuthOfficial    shouldBe Some(
        AuthOfficial(
          Trustee = None,
          OffName = Some(
            OffName(
              Ttl = Some("Mr"),
              Fore = Some("John"),
              Sur = Some("Jones")
            )
          ),
          ClaimNo = Some("test-claim-reference-number"),
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

    "build a ChRISSubmission correctly for an organisation which has CF - Regulator = None, Corporate Trustee = false and address in UK " in {
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

      val currentUser         = organisationUser
      val declarationLanguage = "en"

      val submissionR68       = service.buildR68(claim, currentUser, orgName, declarationLanguage, ScheduleData.empty)
      val submissionRegulator = service.buildRegulator(claim, CFOrganisationUser.enrolmentIdentifierValue)

      submissionR68.WelshSubmission shouldBe None
      submissionR68.AuthOfficial    shouldBe Some(
        AuthOfficial(
          Trustee = None,
          OffName = Some(
            OffName(
              Ttl = Some("Mr"),
              Fore = Some("John"),
              Sur = Some("Jones")
            )
          ),
          ClaimNo = Some("test-claim-reference-number"),
          OffID = Some(
            OffID(
              Postcode = Some("post-code"),
              Overseas = None
            )
          ),
          Phone = Some("1234567890")
        )
      )

      submissionRegulator shouldBe None
    }

    "build a ChRISSubmission correctly for an organisation which has CH - Regulator = None, Corporate Trustee = false and address in UK " in {
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

      val currentUser         = organisationUser
      val declarationLanguage = "en"

      val submissionR68       = service.buildR68(claim, currentUser, orgName, declarationLanguage, ScheduleData.empty)
      val submissionRegulator = service.buildRegulator(claim, CHOrganisationUser.enrolmentIdentifierValue)

      submissionR68.WelshSubmission shouldBe None
      submissionR68.AuthOfficial    shouldBe Some(
        AuthOfficial(
          Trustee = None,
          OffName = Some(
            OffName(
              Ttl = Some("Mr"),
              Fore = Some("John"),
              Sur = Some("Jones")
            )
          ),
          ClaimNo = Some("test-claim-reference-number"),
          OffID = Some(
            OffID(
              Postcode = Some("post-code"),
              Overseas = None
            )
          ),
          Phone = Some("1234567890")
        )
      )

      submissionRegulator shouldBe None
    }

    "build a ChRISSubmission correctly for an organisation which has CH - Regulator = Scottish, Corporate Trustee = false and address in UK " in {
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

      val currentUser = organisationUser

      val submissionR68       = service.buildR68(claim, currentUser, orgName, declarationLanguage, ScheduleData.empty)
      val submissionRegulator = service.buildRegulator(claim, CHOrganisationUser.enrolmentIdentifierValue)

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
          ClaimNo = Some("test-claim-reference-number"),
          OffID = Some(
            OffID(
              Postcode = Some("post-code"),
              Overseas = None
            )
          ),
          Phone = Some("1234567890")
        )
      )

      submissionRegulator shouldBe
        Some(
          Regulator(
            RegName = Some(RegulatorName.OSCR),
            NoReg = None,
            RegNo = None
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

      val currentUser = agentUser

      val submission = service.buildChRISSubmission(claim, currentUser, declarationLanguage).futureValue

      val submissionR68       =
        service.buildR68(claim, currentUser, Some("Test-Agent-Name"), declarationLanguage, ScheduleData.empty)
      val submissionRegulator = service.buildRegulator(claim, agentUser.enrolmentIdentifierValue)

      submission.Body.IRenvelope.R68 shouldBe submissionR68
      submissionR68.WelshSubmission  shouldBe Some(true)
      submissionR68.AuthOfficial     shouldBe None
      submissionR68.AgtOrNom         shouldBe Some(
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
      val otherIncomeRef        = FileUploadReference("oi-ref-789")
      val giftAidRef            = FileUploadReference("ga-ref-789")

      val claim = models.Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = false,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = models.ClaimData(
          repaymentClaimDetails = models.RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = true,
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
          connectedCharitiesScheduleFileUploadReference = Some(connectedCharitiesRef),
          otherIncomeScheduleFileUploadReference = Some(otherIncomeRef),
          giftAidScheduleFileUploadReference = Some(giftAidRef)
        )
      )

      val giftAidData            =
        GiftAidScheduleData(
          earliestDonationDate = "2024-01-15",
          prevOverclaimedGiftAid = Some(BigDecimal("123.45")),
          totalDonations = BigDecimal("200.00"),
          donations = Seq(
            Donation(
              donationDate = "2024-02-01",
              donationAmount = BigDecimal("200.00"),
              donorTitle = Some("Mr"),
              donorFirstName = Some("Test"),
              donorLastName = Some("User"),
              donorPostcode = Some("ZZ1 1ZZ")
            )
          )
        )
      val communityBuildingsData = CommunityBuildingsScheduleData(
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

      val connectedCharitiesData = ConnectedCharitiesScheduleData(
        charities = Seq(
          ConnectedCharity(charityItem = 1, charityName = "Charity One", charityReference = "X95442")
        )
      )

      val otherIncomeData =
        OtherIncomeScheduleData(
          totalOfGrossPayments = BigDecimal("240.01"),
          totalOfTaxDeducted = BigDecimal("240.02"),
          adjustmentForOtherIncomePreviousOverClaimed = BigDecimal("240.03"),
          otherIncomes = Seq(
            OtherIncome(
              otherIncomeItem = 1,
              payerName = "John Smith",
              paymentDate = "2024-03-01",
              grossPayment = BigDecimal("240.00"),
              taxDeducted = BigDecimal("15.00")
            )
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

      (claimsValidationConnectorMock
        .getUploadResult(_: String, _: FileUploadReference)(using _: HeaderCarrier))
        .expects("test-claim-id", otherIncomeRef, *)
        .returns(
          Future.successful(
            Some(GetUploadResultValidatedOtherIncome(otherIncomeRef, otherIncomeData))
          )
        )

      (claimsValidationConnectorMock
        .getUploadResult(_: String, _: FileUploadReference)(using _: HeaderCarrier))
        .expects("test-claim-id", giftAidRef, *)
        .returns(
          Future.successful(
            Some(GetUploadResultValidatedGiftAid(giftAidRef, giftAidData))
          )
        )

      val currentUser = organisationUser

      (rdsConnectorMock
        .getOrganisationName(_: String)(using _: HeaderCarrier))
        .expects(currentUser.enrolmentIdentifierValue, *)
        .returns(
          Future.successful(orgName)
        )

      val result = service.buildChRISSubmission(claim, currentUser, declarationLanguage).futureValue

      result.Body.IRenvelope.R68.Claim.GASDS.get.Charity shouldBe Some(
        List(Charity(Name = "Charity One", HMRCref = "X95442"))
      )

      result.Body.IRenvelope.R68.Claim.GASDS.get.Building shouldBe Some(
        List(
          Building(
            BldgName = "Village Hall",
            Address = "1 High Street",
            Postcode = "AB1 2CD",
            BldgClaim = List(BldgClaim(Year = "2024", Amount = BigDecimal("500.00")))
          )
        )
      )

      result.Body.IRenvelope.R68.Claim.Repayment.get.OtherInc shouldBe
        Some(
          List(
            OtherInc(
              Payer = "John Smith",
              OIDate = "2024-03-01",
              Gross = BigDecimal("240.00"),
              Tax = BigDecimal("15.00")
            )
          )
        )

    }

    "buildChRISSubmission fetches gift aid upload data from validation service when gift aid upload reference is present" in {
      val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
      val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
      val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

      val giftAidRef = FileUploadReference("ga-ref-123")

      val claim = models.Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = false,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = models.ClaimData(
          repaymentClaimDetails = models.RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = false,
            claimingUnderGiftAidSmallDonationsScheme = false
          ),
          giftAidScheduleFileUploadReference = Some(giftAidRef)
        )
      )

      val giftAidScheduleData = GiftAidScheduleData(
        earliestDonationDate = "2024-01-15",
        prevOverclaimedGiftAid = Some(BigDecimal("50.00")),
        totalDonations = BigDecimal("240.00"),
        donations = Seq(
          Donation(
            donationDate = "2024-03-01",
            donationAmount = BigDecimal("240.00"),
            donorTitle = Some("Mr"),
            donorFirstName = Some("John"),
            donorLastName = Some("Smith"),
            donorHouse = Some("10"),
            donorPostcode = Some("AB1 2CD")
          )
        )
      )

      (claimsValidationConnectorMock
        .getUploadResult(_: String, _: FileUploadReference)(using _: HeaderCarrier))
        .expects("test-claim-id", giftAidRef, *)
        .returns(
          Future.successful(
            Some(GetUploadResultValidatedGiftAid(giftAidRef, giftAidScheduleData))
          )
        )

      val currentUser = organisationUser

      (rdsConnectorMock
        .getOrganisationName(_: String)(using _: HeaderCarrier))
        .expects(currentUser.enrolmentIdentifierValue, *)
        .returns(
          Future.successful(orgName)
        )

      val result = service.buildChRISSubmission(claim, currentUser, declarationLanguage).futureValue

      val repayment = result.Body.IRenvelope.R68.Claim.Repayment
      repayment                    shouldBe defined
      repayment.get.EarliestGAdate shouldBe Some("2024-01-15")
      repayment.get.Adjustment     shouldBe Some(BigDecimal("50.00"))
      repayment.get.GAD            shouldBe Some(
        List(
          GAD(
            AggDonation = None,
            Donor = Some(
              Donor(
                Ttl = Some("Mr"),
                Fore = Some("John"),
                Sur = Some("Smith"),
                House = Some("10"),
                Overseas = None,
                Postcode = Some("AB1 2CD")
              )
            ),
            Sponsored = None,
            Date = "2024-03-01",
            Total = "240.00"
          )
        )
      )
    }

    "buildChRISSubmission fetches gift aid upload data from validation service when gift aid upload & otherIncome reference are present" in {
      val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
      val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
      val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

      val giftAidRef = FileUploadReference("ga-ref-123")

      val claim = models.Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = false,
        lastUpdatedReference = UUID.randomUUID().toString,
        claimData = models.ClaimData(
          repaymentClaimDetails = models.RepaymentClaimDetails(
            claimingGiftAid = true,
            claimingTaxDeducted = true,
            claimingUnderGiftAidSmallDonationsScheme = false
          ),
          giftAidScheduleFileUploadReference = Some(giftAidRef)
        )
      )

      val giftAidScheduleData = GiftAidScheduleData(
        earliestDonationDate = "2024-01-15",
        prevOverclaimedGiftAid = Some(BigDecimal("50.00")),
        totalDonations = BigDecimal("240.00"),
        donations = Seq(
          Donation(
            donationDate = "2024-03-01",
            donationAmount = BigDecimal("240.00"),
            donorTitle = Some("Mr"),
            donorFirstName = Some("John"),
            donorLastName = Some("Smith"),
            donorHouse = Some("10"),
            donorPostcode = Some("AB1 2CD")
          )
        )
      )

      (claimsValidationConnectorMock
        .getUploadResult(_: String, _: FileUploadReference)(using _: HeaderCarrier))
        .expects("test-claim-id", giftAidRef, *)
        .returns(
          Future.successful(
            Some(GetUploadResultValidatedGiftAid(giftAidRef, giftAidScheduleData))
          )
        )

      val currentUser = organisationUser

      (rdsConnectorMock
        .getOrganisationName(_: String)(using _: HeaderCarrier))
        .expects(currentUser.enrolmentIdentifierValue, *)
        .returns(
          Future.successful(orgName)
        )

      val result = service.buildChRISSubmission(claim, currentUser, declarationLanguage).futureValue

      val repayment = result.Body.IRenvelope.R68.Claim.Repayment
      repayment                    shouldBe defined
      repayment.get.EarliestGAdate shouldBe Some("2024-01-15")
      repayment.get.Adjustment     shouldBe Some(BigDecimal("50.00"))
      repayment.get.GAD            shouldBe Some(
        List(
          GAD(
            AggDonation = None,
            Donor = Some(
              Donor(
                Ttl = Some("Mr"),
                Fore = Some("John"),
                Sur = Some("Smith"),
                House = Some("10"),
                Overseas = None,
                Postcode = Some("AB1 2CD")
              )
            ),
            Sponsored = None,
            Date = "2024-03-01",
            Total = "240.00"
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
              CommunityBuilding(
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

        val currentUser = organisationUser

        val result = service.buildClaim(
          orgName,
          claim,
          currentUser,
          ScheduleData(connectedCharities = connectedCharitiesData, communityBuildings = communityBuildingsData)
        )
        result.GASDS shouldBe Some(
          GASDS(
            ConnectedCharities = true,
            Charity = Some(List(Charity(Name = "Charity One", HMRCref = "X95442"))),
            GASDSClaim = Some(
              List(
                GASDSClaim(Year = Some("2024"), Amount = Some(BigDecimal("67.09")))
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

        val currentUser = organisationUser

        val result = service.buildClaim(orgName, claim, currentUser, ScheduleData.empty)
        result.GASDS shouldBe None
      }

      "return GASDSClaim as None when giftAidSmallDonationsSchemeDonationDetails is None" in {
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

        val currentUser = organisationUser

        val result = service.buildClaim(orgName, claim, currentUser, ScheduleData.empty)
        result.GASDS.flatMap(_.GASDSClaim) shouldBe None
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

        val currentUser = organisationUser

        val result =
          service.buildClaim(orgName, claim, currentUser, ScheduleData(connectedCharities = connectedCharitiesData))
        result.GASDS.get.Charity shouldBe Some(
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

        val currentUser = organisationUser

        val result = service.buildClaim(orgName, claim, currentUser, ScheduleData.empty)
        result.GASDS.get.Charity shouldBe None
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

        val currentUser = organisationUser

        val result = service.buildClaim(orgName, claim, currentUser, ScheduleData.empty)
        result.GASDS.get.GASDSClaim shouldBe Some(
          List(
            GASDSClaim(Year = Some("2024"), Amount = Some(BigDecimal("67.09"))),
            GASDSClaim(Year = Some("2023"), Amount = Some(BigDecimal("120.50")))
          )
        )
      }

      "map CommunityBuilding with 2 years from upload data" in {
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
              CommunityBuilding(
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

        val currentUser = organisationUser

        val result =
          service.buildClaim(orgName, claim, currentUser, ScheduleData(communityBuildings = communityBuildingsData))
        result.GASDS.get.Building shouldBe Some(
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

      "map CommunityBuilding with 1 year only from upload data" in {
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
        )

        val currentUser = organisationUser

        val result =
          service.buildClaim(orgName, claim, currentUser, ScheduleData(communityBuildings = communityBuildingsData))
        result.GASDS.get.Building shouldBe Some(
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

        val currentUser = organisationUser

        val result = service.buildClaim(orgName, claim, currentUser, ScheduleData.empty)
        result.GASDS.get.Building shouldBe None
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

        val currentUser = organisationUser

        val resultWithAdj = service.buildClaim(orgName, claimWithAdj, currentUser, ScheduleData.empty)
        resultWithAdj.GASDS.get.Adj shouldBe Some("56.89")

        val resultWithZeroAdj = service.buildClaim(orgName, claimWithZeroAdj, currentUser, ScheduleData.empty)
        resultWithZeroAdj.GASDS.get.Adj shouldBe None
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

        val currentUser = organisationUser

        val resultYes =
          service.buildClaim(orgName, buildClaimWithConnected(Some(true)), currentUser, ScheduleData.empty)
        resultYes.GASDS.get.ConnectedCharities shouldBe (true: YesNo)

        val resultNo =
          service.buildClaim(orgName, buildClaimWithConnected(Some(false)), currentUser, ScheduleData.empty)
        resultNo.GASDS.get.ConnectedCharities shouldBe (false: YesNo)

        val resultNone = service.buildClaim(orgName, buildClaimWithConnected(None), currentUser, ScheduleData.empty)
        resultNone.GASDS.get.ConnectedCharities shouldBe (false: YesNo)
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

        val currentUser = organisationUser

        val resultYes =
          service.buildClaim(orgName, buildClaimWithCommBldgs(Some(true)), currentUser, ScheduleData.empty)
        resultYes.GASDS.get.CommBldgs shouldBe Some(true: YesNo)

        val resultNo =
          service.buildClaim(orgName, buildClaimWithCommBldgs(Some(false)), currentUser, ScheduleData.empty)
        resultNo.GASDS.get.CommBldgs shouldBe Some(false: YesNo)

        val resultNone = service.buildClaim(orgName, buildClaimWithCommBldgs(None), currentUser, ScheduleData.empty)
        resultNone.GASDS.get.CommBldgs shouldBe Some(false: YesNo)
      }

    }

    "buildRepayment" should {

      "return None when claimingGiftAid is false" in {
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
              claimingUnderGiftAidSmallDonationsScheme = false
            )
          )
        )

        val currentUser = organisationUser

        val result = service.buildClaim(orgName, claim, currentUser, ScheduleData.empty)
        result.Repayment shouldBe None
      }

      "return None when claimingGiftAid is true but no gift aid data" in {
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
              claimingGiftAid = true,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = false
            )
          )
        )

        val currentUser = organisationUser

        val result = service.buildClaim(orgName, claim, currentUser, ScheduleData.empty)
        result.Repayment shouldBe None
      }

      "map individual donor donations to GAD with Donor element" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val giftAidData = Some(
          GiftAidScheduleData(
            earliestDonationDate = "2024-01-15",
            totalDonations = BigDecimal("240.00"),
            donations = Seq(
              Donation(
                donationDate = "2024-03-01",
                donationAmount = BigDecimal("240.00"),
                donorTitle = Some("Mr"),
                donorFirstName = Some("John"),
                donorLastName = Some("Smith"),
                donorHouse = Some("10"),
                donorPostcode = Some("AB1 2CD")
              )
            )
          )
        )

        val otherIncomeData = Some(
          OtherIncomeScheduleData(
            totalOfGrossPayments = BigDecimal("240.01"),
            totalOfTaxDeducted = BigDecimal("240.02"),
            adjustmentForOtherIncomePreviousOverClaimed = BigDecimal("240.03"),
            otherIncomes = Seq(
              OtherIncome(
                otherIncomeItem = 1,
                payerName = "John Smith",
                paymentDate = "2024-03-01",
                grossPayment = BigDecimal("240.00"),
                taxDeducted = BigDecimal("15.00")
              )
            )
          )
        )

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = true,
              claimingTaxDeducted = true,
              claimingUnderGiftAidSmallDonationsScheme = false
            )
          )
        )

        val result = service.buildRepayment(claim, giftAidData, otherIncomeData)

        result shouldBe Some(
          Repayment(
            GAD = Some(
              List(
                GAD(
                  AggDonation = None,
                  Donor = Some(
                    Donor(
                      Ttl = Some("Mr"),
                      Fore = Some("John"),
                      Sur = Some("Smith"),
                      House = Some("10"),
                      Overseas = None,
                      Postcode = Some("AB1 2CD")
                    )
                  ),
                  Sponsored = None,
                  Date = "2024-03-01",
                  Total = "240.00"
                )
              )
            ),
            EarliestGAdate = Some("2024-01-15"),
            OtherInc = Some(
              List(
                OtherInc(
                  Payer = "John Smith",
                  OIDate = "2024-03-01",
                  Gross = BigDecimal("240.00"),
                  Tax = BigDecimal("15.00")
                )
              )
            ),
            Adjustment = Some(BigDecimal("240.03"))
          )
        )
      }

      "map aggregated donations to GAD with AggDonation element" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val giftAidData     = Some(
          GiftAidScheduleData(
            earliestDonationDate = "2024-01-15",
            totalDonations = BigDecimal("500.00"),
            donations = Seq(
              Donation(
                donationDate = "2024-06-01",
                donationAmount = BigDecimal("500.00"),
                aggregatedDonations = Some("One off donations")
              )
            )
          )
        )
        val otherIncomeData = Some(
          OtherIncomeScheduleData(
            totalOfGrossPayments = BigDecimal("240.01"),
            totalOfTaxDeducted = BigDecimal("240.02"),
            adjustmentForOtherIncomePreviousOverClaimed = BigDecimal("240.03"),
            otherIncomes = Seq(
              OtherIncome(
                otherIncomeItem = 1,
                payerName = "John Smith",
                paymentDate = "2024-03-01",
                grossPayment = BigDecimal("240.00"),
                taxDeducted = BigDecimal("15.00")
              )
            )
          )
        )

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = true,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = false
            )
          )
        )

        val result = service.buildRepayment(claim, giftAidData, otherIncomeData)

        result.get.GAD.get.head.AggDonation shouldBe Some("One off donations")
        result.get.GAD.get.head.Donor       shouldBe None
      }

      "map overseas donor (postcode X) to GAD with Overseas flag" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val giftAidData     = Some(
          GiftAidScheduleData(
            earliestDonationDate = "2024-01-15",
            totalDonations = BigDecimal("100.00"),
            donations = Seq(
              Donation(
                donationDate = "2024-04-01",
                donationAmount = BigDecimal("100.00"),
                donorTitle = Some("Mrs"),
                donorFirstName = Some("Jane"),
                donorLastName = Some("Doe"),
                donorHouse = Some("5"),
                donorPostcode = Some("X")
              )
            )
          )
        )
        val otherIncomeData = Some(
          OtherIncomeScheduleData(
            totalOfGrossPayments = BigDecimal("240.01"),
            totalOfTaxDeducted = BigDecimal("240.02"),
            adjustmentForOtherIncomePreviousOverClaimed = BigDecimal("240.03"),
            otherIncomes = Seq(
              OtherIncome(
                otherIncomeItem = 1,
                payerName = "John Smith",
                paymentDate = "2024-03-01",
                grossPayment = BigDecimal("240.00"),
                taxDeducted = BigDecimal("15.00")
              )
            )
          )
        )

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = true,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = false
            )
          )
        )

        val result = service.buildRepayment(claim, giftAidData, otherIncomeData)

        val donor = result.get.GAD.get.head.Donor.get
        donor.Overseas shouldBe Some(true)
        donor.Postcode shouldBe None
      }

      "map sponsored donation to GAD with Sponsored flag" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val giftAidData = Some(
          GiftAidScheduleData(
            earliestDonationDate = "2024-01-15",
            totalDonations = BigDecimal("50.00"),
            donations = Seq(
              Donation(
                donationDate = "2024-05-01",
                donationAmount = BigDecimal("50.00"),
                donorTitle = Some("Dr"),
                donorFirstName = Some("Bob"),
                donorLastName = Some("Brown"),
                donorHouse = Some("7"),
                donorPostcode = Some("EF3 4GH"),
                sponsoredEvent = Some(true)
              )
            )
          )
        )

        val otherIncomeData = Some(
          OtherIncomeScheduleData(
            totalOfGrossPayments = BigDecimal("240.01"),
            totalOfTaxDeducted = BigDecimal("240.02"),
            adjustmentForOtherIncomePreviousOverClaimed = BigDecimal("240.03"),
            otherIncomes = Seq(
              OtherIncome(
                otherIncomeItem = 1,
                payerName = "John Smith",
                paymentDate = "2024-03-01",
                grossPayment = BigDecimal("240.00"),
                taxDeducted = BigDecimal("15.00")
              )
            )
          )
        )

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = true,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = false
            )
          )
        )

        val result = service.buildRepayment(claim, giftAidData, otherIncomeData)

        result.get.GAD.get.head.Sponsored shouldBe Some(true)
      }

      "populate EarliestGAdate from schedule data" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val giftAidData = Some(
          GiftAidScheduleData(
            earliestDonationDate = "2023-12-25",
            totalDonations = BigDecimal("100.00"),
            donations = Seq(
              Donation(
                donationDate = "2024-01-01",
                donationAmount = BigDecimal("100.00"),
                donorTitle = Some("Mr"),
                donorFirstName = Some("Test"),
                donorLastName = Some("User"),
                donorPostcode = Some("ZZ1 1ZZ")
              )
            )
          )
        )

        val otherIncomeData = Some(
          OtherIncomeScheduleData(
            totalOfGrossPayments = BigDecimal("240.01"),
            totalOfTaxDeducted = BigDecimal("240.02"),
            adjustmentForOtherIncomePreviousOverClaimed = BigDecimal("240.03"),
            otherIncomes = Seq(
              OtherIncome(
                otherIncomeItem = 1,
                payerName = "John Smith",
                paymentDate = "2024-03-01",
                grossPayment = BigDecimal("240.00"),
                taxDeducted = BigDecimal("15.00")
              )
            )
          )
        )

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = true,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = false
            )
          )
        )

        val result = service.buildRepayment(claim, giftAidData, otherIncomeData)

        result.get.EarliestGAdate shouldBe Some("2023-12-25")
      }

      "populate Adjustment from prevOverclaimedGiftAid when overpayment and OtherIncome overpayment are both present" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val giftAidData = Some(
          GiftAidScheduleData(
            earliestDonationDate = "2024-01-15",
            prevOverclaimedGiftAid = Some(BigDecimal("123.45")),
            totalDonations = BigDecimal("200.00"),
            donations = Seq(
              Donation(
                donationDate = "2024-02-01",
                donationAmount = BigDecimal("200.00"),
                donorTitle = Some("Mr"),
                donorFirstName = Some("Test"),
                donorLastName = Some("User"),
                donorPostcode = Some("ZZ1 1ZZ")
              )
            )
          )
        )

        val otherIncomeData = Some(
          OtherIncomeScheduleData(
            totalOfGrossPayments = BigDecimal("240.01"),
            totalOfTaxDeducted = BigDecimal("240.02"),
            adjustmentForOtherIncomePreviousOverClaimed = BigDecimal("240.03"),
            otherIncomes = Seq(
              OtherIncome(
                otherIncomeItem = 1,
                payerName = "John Smith",
                paymentDate = "2024-03-01",
                grossPayment = BigDecimal("240.00"),
                taxDeducted = BigDecimal("15.00")
              )
            )
          )
        )

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = true,
              claimingTaxDeducted = true,
              claimingUnderGiftAidSmallDonationsScheme = false
            )
          )
        )

        val result = service.buildRepayment(claim, giftAidData, otherIncomeData)

        result.get.Adjustment shouldBe Some(BigDecimal("363.48"))
      }

      "populate Adjustment from prevOverclaimedGiftAid when present and OtherIncome is not defined" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val giftAidData = Some(
          GiftAidScheduleData(
            earliestDonationDate = "2024-01-15",
            prevOverclaimedGiftAid = Some(BigDecimal("123.45")),
            totalDonations = BigDecimal("200.00"),
            donations = Seq(
              Donation(
                donationDate = "2024-02-01",
                donationAmount = BigDecimal("200.00"),
                donorTitle = Some("Mr"),
                donorFirstName = Some("Test"),
                donorLastName = Some("User"),
                donorPostcode = Some("ZZ1 1ZZ")
              )
            )
          )
        )

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = true,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = false
            )
          )
        )

        val result = service.buildRepayment(claim, giftAidData, None)

        result.get.Adjustment shouldBe Some(BigDecimal("123.45"))
      }

      "populate Adjustment when prevOverclaimedGiftAid is None &  OtherIncome overpayment is present" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val giftAidData = Some(
          GiftAidScheduleData(
            earliestDonationDate = "2024-01-15",
            totalDonations = BigDecimal("200.00"),
            donations = Seq(
              Donation(
                donationDate = "2024-02-01",
                donationAmount = BigDecimal("200.00"),
                donorTitle = Some("Mr"),
                donorFirstName = Some("Test"),
                donorLastName = Some("User"),
                donorPostcode = Some("ZZ1 1ZZ")
              )
            )
          )
        )

        val otherIncomeData = Some(
          OtherIncomeScheduleData(
            totalOfGrossPayments = BigDecimal("240.01"),
            totalOfTaxDeducted = BigDecimal("240.02"),
            adjustmentForOtherIncomePreviousOverClaimed = BigDecimal("240.03"),
            otherIncomes = Seq(
              OtherIncome(
                otherIncomeItem = 1,
                payerName = "John Smith",
                paymentDate = "2024-03-01",
                grossPayment = BigDecimal("240.00"),
                taxDeducted = BigDecimal("15.00")
              )
            )
          )
        )

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = false,
              claimingTaxDeducted = true,
              claimingUnderGiftAidSmallDonationsScheme = false
            )
          )
        )

        val result = service.buildRepayment(claim, giftAidData, otherIncomeData)

        result.get.Adjustment shouldBe Some(BigDecimal("240.03"))
      }

      "omit Adjustment when prevOverclaimedGiftAid is None &  there no OtherIncome" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val giftAidData = Some(
          GiftAidScheduleData(
            earliestDonationDate = "2024-01-15",
            totalDonations = BigDecimal("200.00"),
            donations = Seq(
              Donation(
                donationDate = "2024-02-01",
                donationAmount = BigDecimal("200.00"),
                donorTitle = Some("Mr"),
                donorFirstName = Some("Test"),
                donorLastName = Some("User"),
                donorPostcode = Some("ZZ1 1ZZ")
              )
            )
          )
        )

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = true,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = false
            )
          )
        )

        val result = service.buildRepayment(claim, giftAidData, None)

        result.get.Adjustment shouldBe None
      }

      "map multiple donations correctly" in {
        val rdsConnectorMock              = mock[RdsDatacacheProxyConnector]
        val claimsValidationConnectorMock = mock[ClaimsValidationConnector]
        val service                       = new ChRISSubmissionServiceImpl(rdsConnectorMock, claimsValidationConnectorMock)

        val giftAidData = Some(
          GiftAidScheduleData(
            earliestDonationDate = "2024-01-01",
            totalDonations = BigDecimal("790.00"),
            donations = Seq(
              Donation(
                donationDate = "2024-03-01",
                donationAmount = BigDecimal("240.00"),
                donorTitle = Some("Mr"),
                donorFirstName = Some("John"),
                donorLastName = Some("Smith"),
                donorHouse = Some("10"),
                donorPostcode = Some("AB1 2CD")
              ),
              Donation(
                donationDate = "2024-06-01",
                donationAmount = BigDecimal("500.00"),
                aggregatedDonations = Some("One off donations")
              ),
              Donation(
                donationDate = "2024-07-01",
                donationAmount = BigDecimal("50.00"),
                donorTitle = Some("Dr"),
                donorFirstName = Some("Bob"),
                donorLastName = Some("Brown"),
                donorHouse = Some("7"),
                donorPostcode = Some("EF3 4GH"),
                sponsoredEvent = Some(true)
              )
            )
          )
        )

        val otherIncomeData = Some(
          OtherIncomeScheduleData(
            totalOfGrossPayments = BigDecimal("240.01"),
            totalOfTaxDeducted = BigDecimal("240.02"),
            adjustmentForOtherIncomePreviousOverClaimed = BigDecimal("240.03"),
            otherIncomes = Seq(
              OtherIncome(
                otherIncomeItem = 1,
                payerName = "John Smith",
                paymentDate = "2024-03-01",
                grossPayment = BigDecimal("240.00"),
                taxDeducted = BigDecimal("15.00")
              )
            )
          )
        )

        val claim = models.Claim(
          claimId = "test-claim-id",
          userId = "test-user-id",
          claimSubmitted = false,
          lastUpdatedReference = UUID.randomUUID().toString,
          claimData = models.ClaimData(
            repaymentClaimDetails = models.RepaymentClaimDetails(
              claimingGiftAid = true,
              claimingTaxDeducted = false,
              claimingUnderGiftAidSmallDonationsScheme = false
            )
          )
        )

        val result = service.buildRepayment(claim, giftAidData, otherIncomeData)

        val gads = result.get.GAD.get
        gads should have size 3

        gads(0).Donor       shouldBe Some(
          Donor(
            Ttl = Some("Mr"),
            Fore = Some("John"),
            Sur = Some("Smith"),
            House = Some("10"),
            Overseas = None,
            Postcode = Some("AB1 2CD")
          )
        )
        gads(0).AggDonation shouldBe None
        gads(0).Total       shouldBe "240.00"

        gads(1).AggDonation shouldBe Some("One off donations")
        gads(1).Donor       shouldBe None
        gads(1).Total       shouldBe "500.00"

        gads(2).Sponsored shouldBe Some(true)
        gads(2).Total     shouldBe "50.00"
      }

    }

  }
}
