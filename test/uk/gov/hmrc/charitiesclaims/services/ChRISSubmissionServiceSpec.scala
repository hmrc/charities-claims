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
import uk.gov.hmrc.charitiesclaims.connectors.RdsDatacacheProxyConnector
import scala.concurrent.Future

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
        creationTimestamp = "2025-01-01",
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
        creationTimestamp = "2025-01-01",
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
              nameOfCharityRegulator = "EnglandAndWales",
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
        creationTimestamp = "2025-01-01",
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
              nameOfCharityRegulator = "EnglandAndWales",
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
        creationTimestamp = "2025-01-01",
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
              nameOfCharityRegulator = "EnglandAndWales",
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
        creationTimestamp = "2025-01-01",
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
              nameOfCharityRegulator = "EnglandAndWales",
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
        creationTimestamp = "2025-01-01",
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
              nameOfCharityRegulator = "NorthernIreland",
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
        creationTimestamp = "2025-01-01",
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
              nameOfCharityRegulator = "Scottish",
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
        creationTimestamp = "2025-01-01",
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
              nameOfCharityRegulator = "None",
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
        creationTimestamp = "2025-01-01",
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
              nameOfCharityRegulator = "None",
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

  }
}
