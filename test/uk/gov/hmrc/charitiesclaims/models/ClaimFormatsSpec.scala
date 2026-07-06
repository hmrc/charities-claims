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

package uk.gov.hmrc.charitiesclaims.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, SymmetricCryptoFactory}

class ClaimFormatsSpec extends AnyWordSpec with Matchers {

  private given crypto: Encrypter & Decrypter =
    SymmetricCryptoFactory.aesGcmCrypto("ci+wy7C6jftPw6tMdjnV60T+bJOgwDXEHmYk4XWKbsM=")

  private val format = ClaimFormats.encryptedClaimFormat

  private val trusteeTitle      = "Ms"
  private val trusteeFirstName  = "Alice"
  private val trusteeLastName   = "Wonderland"
  private val trusteePostcode   = "AB1 2CD"
  private val trusteePhone      = "01234 567890"
  private val corporatePostcode = "ZZ9 9ZZ"

  private val claim = Claim(
    claimId = "claim-1",
    userId = "user-1",
    claimSubmitted = false,
    lastUpdatedReference = "ref-1",
    claimData = ClaimData(
      repaymentClaimDetails = RepaymentClaimDetails(
        claimingGiftAid = true,
        claimingTaxDeducted = false,
        claimingUnderGiftAidSmallDonationsScheme = false
      ),
      organisationDetails = Some(
        OrganisationDetails(
          nameOfCharityRegulator = NameOfCharityRegulator.EnglandAndWales,
          areYouACorporateTrustee = false,
          corporateTrusteePostcode = Some(corporatePostcode),
          authorisedOfficialTrusteePostcode = Some(trusteePostcode),
          authorisedOfficialTrusteeDaytimeTelephoneNumber = Some(trusteePhone),
          authorisedOfficialTrusteeTitle = Some(trusteeTitle),
          authorisedOfficialTrusteeFirstName = Some(trusteeFirstName),
          authorisedOfficialTrusteeLastName = Some(trusteeLastName)
        )
      ),
      agentUserOrganisationDetails = Some(
        AgentUserOrganisationDetails(
          whoShouldHmrcSendPaymentTo = "charity",
          daytimeTelephoneNumber = trusteePhone,
          doYouHaveAgentUKAddress = true,
          postcode = Some(trusteePostcode),
          nameOfCharityRegulator = NameOfCharityRegulator.Scottish
        )
      )
    )
  )

  "ClaimFormats.encryptedClaimFormat" should {

    "encrypt only the named PII fields, leaving the other fields in plaintext" in {
      val json = format.writes(claim)

      val org = json \ "claimData" \ "organisationDetails"
      (org \ "authorisedOfficialTrusteeTitle").as[String]                  should not be trusteeTitle
      (org \ "authorisedOfficialTrusteeFirstName").as[String]              should not be trusteeFirstName
      (org \ "authorisedOfficialTrusteeLastName").as[String]               should not be trusteeLastName
      (org \ "authorisedOfficialTrusteePostcode").as[String]               should not be trusteePostcode
      (org \ "authorisedOfficialTrusteeDaytimeTelephoneNumber").as[String] should not be trusteePhone
      (org \ "corporateTrusteePostcode").as[String]                      shouldBe corporatePostcode

      val agent = json \ "claimData" \ "agentUserOrganisationDetails"
      (agent \ "daytimeTelephoneNumber").as[String]       should not be trusteePhone
      (agent \ "postcode").as[String]                     should not be trusteePostcode
      (agent \ "whoShouldHmrcSendPaymentTo").as[String] shouldBe "charity"

      json.toString should not include trusteeLastName
      json.toString should not include trusteePostcode
      json.toString should not include trusteePhone
    }

    "round-trip back to the original claim on decrypt" in {
      val json = format.writes(claim)

      format.reads(json).get shouldBe claim
    }
  }
}
