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
import uk.gov.hmrc.charitiesclaims.connectors.RdsDatacacheProxyConnector
import scala.concurrent.Future
import uk.gov.hmrc.charitiesclaims.models.NameOfCharityRegulator

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
      val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
      val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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

    // test for regulator (EnglandAndWales, NorthernIreland,Scottish), Corporate Trustee =Y, then test Corporate Trustee Address (in UK , and overseas)
    // test for regulator (EnglandAndWales, NorthernIreland,Scottish), Corporate Trustee =N, then test Authorised Official Trustee Address (in UK , and overseas)
    // test for regulator (None), reason charity not registered not (LowIncome,Excepted, Exempt, Waiting), Corporate Trustee =Y, then test Corporate Trustee Address (in UK , and overseas)
    // test for regulator (None), reason charity not registered not (LowIncome, Excepted, Exempt,Waiting), Corporate Trustee =N, then test Authorised Official Trustee Address (in UK , and overseas)

    "build a ChRISSubmission correctly for an organisation - Regulator = EnglandAndWales, Corporate Trustee = true and address in UK " in {
      val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
      val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
          // Organisation details
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

      val submissionR68       = service.buildR68(claim, currentUser, None)
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
      val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
      val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
          // Organisation details
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

      val submissionR68       = service.buildR68(claim, currentUser, None)
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
      val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
      val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
          // Organisation details
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

      val submissionR68       = service.buildR68(claim, currentUser, None)
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
      val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
      val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
          // Organisation details
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

      val submissionR68 = service.buildR68(claim, currentUser, None)

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
      val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
      val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
          // Organisation details
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

      val submissionR68       = service.buildR68(claim, currentUser, None)
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
      val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
      val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
          // Organisation details
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

      val submissionR68       = service.buildR68(claim, currentUser, None)
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
      val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
      val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
          // Organisation details
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

      val submissionR68       = service.buildR68(claim, currentUser, None)
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

    // TODO Add more assertions for the rest of the submission

    "build a ChRISSubmission correctly for an agent - Regulator = None, Corporate Trustee = false and address in UK " in {
      val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
      val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
          // Organisation details
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

      val submissionR68       = service.buildR68(claim, currentUser, Some("Test-Agent-Name"))
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

    // TODO Add more assertions for the rest of the submission

    "buildGiftAidSmallDonationsScheme" should {

      "populate GASDS when claimingUnderGiftAidSmallDonationsScheme is true with all sections present" in {
        val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
        val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
                ),
                connectedCharitiesScheduleData = Seq(
                  models.ConnectedCharity(charityItem = 1, charityName = "Charity One", charityReference = "X95442")
                ),
                communityBuildingsScheduleData = Seq(
                  models.CommunityBuilding(
                    buildingItem = 1,
                    buildingName = "YMCA",
                    firstLineOfAddress = "123 New Street",
                    postcode = "AB12 3CD",
                    taxYearOneEnd = 2024,
                    taxYearOneAmount = BigDecimal("1257.21"),
                    taxYearTwoEnd = 2023,
                    taxYearTwoAmount = BigDecimal("500.00"),
                    taxYearThreeEnd = 2022,
                    taxYearThreeAmount = BigDecimal("0")
                  )
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

        val result = service.buildClaim(claim, currentUser)
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
        val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
        val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
                ),
                connectedCharitiesScheduleData = Seq.empty,
                communityBuildingsScheduleData = Seq.empty
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

        val result = service.buildClaim(claim, currentUser)
        result.GiftAidSmallDonationsScheme shouldBe None
      }

      "return None when giftAidSmallDonationsSchemeDonationDetails is None" in {
        val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
        val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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

        val result = service.buildClaim(claim, currentUser)
        result.GiftAidSmallDonationsScheme shouldBe None
      }

      "map multiple connected charities correctly" in {
        val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
        val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
                claims = Seq.empty,
                connectedCharitiesScheduleData = Seq(
                  models.ConnectedCharity(charityItem = 1, charityName = "Charity One", charityReference = "X95442"),
                  models.ConnectedCharity(charityItem = 2, charityName = "Charity Two", charityReference = "Y12345")
                ),
                communityBuildingsScheduleData = Seq.empty
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

        val result = service.buildClaim(claim, currentUser)
        result.GiftAidSmallDonationsScheme.get.Charity shouldBe Some(
          List(
            Charity(Name = "Charity One", HMRCref = "X95442"),
            Charity(Name = "Charity Two", HMRCref = "Y12345")
          )
        )
      }

      "return Charity as None when no connected charities exist" in {
        val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
        val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
                claims = Seq.empty,
                connectedCharitiesScheduleData = Seq.empty,
                communityBuildingsScheduleData = Seq.empty
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

        val result = service.buildClaim(claim, currentUser)
        result.GiftAidSmallDonationsScheme.get.Charity shouldBe None
      }

      "map GiftAidSmallDonationsSchemeClaim correctly" in {
        val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
        val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
                ),
                connectedCharitiesScheduleData = Seq.empty,
                communityBuildingsScheduleData = Seq.empty
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

        val result = service.buildClaim(claim, currentUser)
        result.GiftAidSmallDonationsScheme.get.GiftAidSmallDonationsSchemeClaim shouldBe Some(
          List(
            GiftAidSmallDonationsSchemeClaim(Year = Some("2024"), Amount = Some(BigDecimal("67.09"))),
            GiftAidSmallDonationsSchemeClaim(Year = Some("2023"), Amount = Some(BigDecimal("120.50")))
          )
        )
      }

      "map Building with multiple BldgClaims filtering out zero amounts" in {
        val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
        val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
                claims = Seq.empty,
                connectedCharitiesScheduleData = Seq.empty,
                communityBuildingsScheduleData = Seq(
                  models.CommunityBuilding(
                    buildingItem = 1,
                    buildingName = "YMCA",
                    firstLineOfAddress = "123 New Street",
                    postcode = "AB12 3CD",
                    taxYearOneEnd = 2024,
                    taxYearOneAmount = BigDecimal("1257.21"),
                    taxYearTwoEnd = 2023,
                    taxYearTwoAmount = BigDecimal("500.00"),
                    taxYearThreeEnd = 2022,
                    taxYearThreeAmount = BigDecimal("0")
                  )
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

        val result = service.buildClaim(claim, currentUser)
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

      "return Building as None when no community buildings exist" in {
        val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
        val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
                claims = Seq.empty,
                connectedCharitiesScheduleData = Seq.empty,
                communityBuildingsScheduleData = Seq.empty
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

        val result = service.buildClaim(claim, currentUser)
        result.GiftAidSmallDonationsScheme.get.Building shouldBe None
      }

      "set Adj when adjustment > 0 and None when 0" in {
        val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
        val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
                claims = Seq.empty,
                connectedCharitiesScheduleData = Seq.empty,
                communityBuildingsScheduleData = Seq.empty
              )
            )
          )
        )

        val claimWithZeroAdj = claimWithAdj.copy(
          claimData = claimWithAdj.claimData.copy(
            giftAidSmallDonationsSchemeDonationDetails = Some(
              models.GiftAidSmallDonationsSchemeDonationDetails(
                adjustmentForGiftAidOverClaimed = BigDecimal("0"),
                claims = Seq.empty,
                connectedCharitiesScheduleData = Seq.empty,
                communityBuildingsScheduleData = Seq.empty
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

        val resultWithAdj = service.buildClaim(claimWithAdj, currentUser)
        resultWithAdj.GiftAidSmallDonationsScheme.get.Adj shouldBe Some("56.89")

        val resultWithZeroAdj = service.buildClaim(claimWithZeroAdj, currentUser)
        resultWithZeroAdj.GiftAidSmallDonationsScheme.get.Adj shouldBe None
      }

      "map ConnectedCharities yes/no from repaymentClaimDetails" in {
        val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
        val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
                  claims = Seq.empty,
                  connectedCharitiesScheduleData = Seq.empty,
                  communityBuildingsScheduleData = Seq.empty
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

        val resultYes = service.buildClaim(buildClaimWithConnected(Some(true)), currentUser)
        resultYes.GiftAidSmallDonationsScheme.get.ConnectedCharities shouldBe (true: YesNo)

        val resultNo = service.buildClaim(buildClaimWithConnected(Some(false)), currentUser)
        resultNo.GiftAidSmallDonationsScheme.get.ConnectedCharities shouldBe (false: YesNo)

        val resultNone = service.buildClaim(buildClaimWithConnected(None), currentUser)
        resultNone.GiftAidSmallDonationsScheme.get.ConnectedCharities shouldBe (false: YesNo)
      }

      "map CommBldgs yes/no from repaymentClaimDetails" in {
        val rdsConnectorMock = mock[RdsDatacacheProxyConnector]
        val service          = new ChRISSubmissionServiceImpl(rdsConnectorMock)

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
                  claims = Seq.empty,
                  connectedCharitiesScheduleData = Seq.empty,
                  communityBuildingsScheduleData = Seq.empty
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

        val resultYes = service.buildClaim(buildClaimWithCommBldgs(Some(true)), currentUser)
        resultYes.GiftAidSmallDonationsScheme.get.CommBldgs shouldBe Some(true: YesNo)

        val resultNo = service.buildClaim(buildClaimWithCommBldgs(Some(false)), currentUser)
        resultNo.GiftAidSmallDonationsScheme.get.CommBldgs shouldBe Some(false: YesNo)

        val resultNone = service.buildClaim(buildClaimWithCommBldgs(None), currentUser)
        resultNone.GiftAidSmallDonationsScheme.get.CommBldgs shouldBe Some(false: YesNo)
      }

    }

  }
}
