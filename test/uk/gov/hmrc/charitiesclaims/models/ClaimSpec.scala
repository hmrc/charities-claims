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

      claim.claimId                                                            shouldBe "test-claim-submitted"
      claim.userId                                                             shouldBe "test-user-1"
      claim.creationTimestamp                                                  shouldBe "2025-11-10T13:45:56.016Z"
      claim.claimData.repaymentClaimDetails.claimingGiftAid                    shouldBe true
      claim.claimData.repaymentClaimDetails.claimingTaxDeducted                shouldBe false
      claim.claimData.repaymentClaimDetails.claimingUnderGasds                 shouldBe false
      claim.claimData.repaymentClaimDetails.claimReferenceNumber               shouldBe Some("New GA Claim")
      claim.claimData.organisationDetails.map(_.nameOfCharityRegulator)        shouldBe Some(
        "EnglandAndWales"
      )
      claim.claimData.organisationDetails.flatMap(_.charityRegistrationNumber) shouldBe Some(
        "1137948"
      )
      claim.claimData.organisationDetails.map(_.areYouACorporateTrustee)       shouldBe Some(true)
      claim.claimData.organisationDetails.flatMap(_.nameOfCorporateTrustee)    shouldBe Some(
        "Joe Bloggs"
      )
      claim.claimData.organisationDetails.flatMap(_.corporateTrusteePostcode)  shouldBe Some(
        "AB12 3YZ"
      )
      claim.claimData.organisationDetails
        .flatMap(_.corporateTrusteeDaytimeTelephoneNumber)                     shouldBe Some("071234567890")
      claim.claimData.giftAidScheduleData.map(_.earliestDonationDate)          shouldBe Some(
        "2025-01-31"
      )
      claim.claimData.giftAidScheduleData.map(_.prevOverclaimedGiftAid)        shouldBe Some(0.00)
      claim.claimData.giftAidScheduleData.map(_.totalDonations)                shouldBe Some(1450)

      claim.claimData.giftAidScheduleData.map(_.donations.size) shouldBe Some(4)

      claim.claimData.giftAidScheduleData.map(_.donations.head.donationItem)   shouldBe Some(1)
      claim.claimData.giftAidScheduleData.map(_.donations.head.donationDate)   shouldBe Some(
        "2025-03-24"
      )
      claim.claimData.giftAidScheduleData.map(_.donations.head.donationAmount) shouldBe Some(
        240
      )
      claim.claimData.giftAidScheduleData.flatMap(_.donations.head.donorTitle) shouldBe Some(
        "Mr"
      )
      claim.claimData.giftAidScheduleData
        .flatMap(_.donations.head.donorFirstName)                              shouldBe Some(
        "Henry"
      )
      claim.claimData.giftAidScheduleData
        .flatMap(_.donations.head.donorLastName)                               shouldBe Some(
        "House Martin"
      )
      claim.claimData.giftAidScheduleData.flatMap(_.donations.head.donorHouse) shouldBe Some(
        "152A"
      )
      claim.claimData.giftAidScheduleData
        .flatMap(_.donations.head.donorPostcode)                               shouldBe Some(
        "M99 2QD"
      )
      claim.claimData.giftAidScheduleData
        .flatMap(_.donations.head.sponsoredEvent)                              shouldBe Some(
        false
      )
      claim.claimData.giftAidScheduleData
        .flatMap(_.donations(2).aggregatedDonations)                           shouldBe Some("One off Gift Aid donations")
      claim.claimData.giftAidScheduleData.map(_.donations(2).donationDate)     shouldBe Some(
        "2025-03-31"
      )
      claim.claimData.giftAidScheduleData.map(_.donations(2).donationAmount)   shouldBe Some(
        880
      )
      claim.claimData.giftAidScheduleData.flatMap(_.donations.head.donorTitle) shouldBe Some(
        "Mr"
      )
      claim.claimData.giftAidScheduleData
        .flatMap(_.donations(1).donorFirstName)                                shouldBe Some(
        "John"
      )
    }
  }
}
