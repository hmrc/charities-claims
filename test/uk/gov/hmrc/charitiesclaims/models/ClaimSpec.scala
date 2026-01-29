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
import play.api.libs.json.Json

import scala.io.Source

class ClaimSpec extends AnyWordSpec with Matchers {

  "Claim" should {
    "be serialised and deserialised correctly" in {

      val json =
        Source
          .fromInputStream(this.getClass.getResourceAsStream("/test-claim-submitted.json"))
          .getLines()
          .mkString("\n")

      val claim = Json.parse(json).as[Claim]

      Json.parse(Json.prettyPrint(Json.toJson(claim))).as[Claim] shouldBe claim

      claim.claimId                                                                  shouldBe "test-claim-submitted"
      claim.userId                                                                   shouldBe "test-user-1"
      claim.creationTimestamp                                                        shouldBe "2025-11-10T13:45:56.016Z"
      claim.claimData.repaymentClaimDetails.claimingGiftAid                          shouldBe true
      claim.claimData.repaymentClaimDetails.claimingTaxDeducted                      shouldBe false
      claim.claimData.repaymentClaimDetails.claimingUnderGiftAidSmallDonationsScheme shouldBe false
      claim.claimData.repaymentClaimDetails.claimReferenceNumber                     shouldBe Some("New GA Claim")
      claim.claimData.organisationDetails.map(_.nameOfCharityRegulator)              shouldBe Some(
        NameOfCharityRegulator.EnglandAndWales
      )
      claim.claimData.organisationDetails.flatMap(_.charityRegistrationNumber)       shouldBe Some(
        "1137948"
      )
      claim.claimData.organisationDetails.map(_.areYouACorporateTrustee)             shouldBe Some(true)
      claim.claimData.organisationDetails.flatMap(_.nameOfCorporateTrustee)          shouldBe Some(
        "Joe Bloggs"
      )
      claim.claimData.organisationDetails.flatMap(_.corporateTrusteePostcode)        shouldBe Some(
        "AB12 3YZ"
      )
      claim.claimData.organisationDetails
        .flatMap(_.corporateTrusteeDaytimeTelephoneNumber)                           shouldBe Some("071234567890")
    }
  }
}
