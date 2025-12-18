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
import uk.gov.hmrc.charitiesclaims.models as models
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.SessionId
import uk.gov.hmrc.charitiesclaims.models.chris.*
import uk.gov.hmrc.charitiesclaims.xml.{XmlAttribute, XmlContent}

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
      val service = new ChRISSubmissionServiceImpl()

      val claim = models.Claim(
        claimId = "test-claim-id",
        userId = "test-user-id",
        claimSubmitted = true,
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
              reasonNotRegisteredWithRegulator = Some("Excepted"),
              charityRegistrationNumber = Some("123456"),
              areYouACorporateTrustee = true,
              doYouHaveUKAddress = Some(true),
              nameOfCorporateTrustee = Some("Test-Corporate-Trustee"),
              corporateTrusteePostcode = Some("post-code"),
              corporateTrusteeDaytimeTelephoneNumber = Some("1234567890"),
              corporateTrusteeTitle = None,
              corporateTrusteeFirstName = None,
              corporateTrusteeLastName = None
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

      val submision = service.buildChRISSubmission(claim, currentUser).futureValue

      submision.GovTalkDetails shouldBe GovTalkDetails(
        Keys = List(
          Key(Type = "CredentialID", Value = "test-user-id"),
          Key(Type = "test-enrolment-identifier-key", Value = "test-enrolment-identifier-value"),
          Key(Type = "SessionID", Value = "test-session-id")
        )
      )

      val submissionR68     = service.buildR68(claim, currentUser)
      val submissionOffId   = service.buildOffId(claim)
      val submissionOffName = service.buildOffName(claim)

      submissionR68.AuthOfficial shouldBe Some(
        AuthOfficial(
          Trustee = Some("Test-Corporate-Trustee"),
          OffName = Some(
            OffName(
              Ttl = None,
              Fore = None,
              Sur = None
            )
          ),
          ClaimNo = Some(""),
          OffID = Some(
            OffID(
              Postcode = Some(""),
              Overseas = Some(false)
            )
          ),
          Phone = Some("1234567890")
        )
      )

      submissionOffName shouldBe Some(
        OffName(
          Ttl = None,
          Fore = None,
          Sur = None
        )
      )

      submissionOffId shouldBe Some(
        OffID(
          Postcode = Some(""),
          Overseas = Some(false)
        )
      )

//      submision.Body shouldBe Body(
//        IRenvelope = IRenvelope(
//          IRheader = IRheader(
//            Keys = List(
//              Key(Type = "CHARID", Value = "FOO"),
//              Key(Type = "test-enrolment-identifier-key", Value = "test-enrolment-identifier-value")
//            ),
//            PeriodEnd = "2012-01-01",
//            Sender = "Other"
//          ),
//          R68 = R68(
//            WelshSubmission = Some(false),
//            AuthOfficial = Some(
//              AuthOfficial(
//                Trustee = Some("Test-Corporate-Trustee"),
//                OffName = Some(
//                  OffName(
//                    Ttl = None,
//                    Fore = None,
//                    Sur = None
//                  )
//                ),
//                ClaimNo = Some(""),
//                OffID = Some(
//                  OffID(
//                    Postcode = Some(""),
//                    Overseas = Some(false)
//                  )
//                ),
//                Phone = Some("1234567890")
//              )
//            ),
//            AgtOrNom = None,
//            Declaration = true,
//            Claim = Claim(
//              OrgName = "CASC",
//              HMRCref = "FOO",
//              Regulator = Some(
//                Regulator(
//                  RegName = Some("EnglandAndWales"),
//                  NoReg = Some(false),
//                  RegNo = Some("123456")
//                )
//              ),
//              Repayment = Some(
//                Repayment(
//                  GAD = None,
//                  EarliestGAdate = ???,
//                  OtherInc = ???,
//                  Adjustment = ???
//                )
//              )
//            )
//          )
//        )
//      )
      // TODO Add more assertions for the rest of the submission
    }
  }

}
