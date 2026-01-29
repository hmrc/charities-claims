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

package uk.gov.hmrc.charitiesclaims.util

import uk.gov.hmrc.charitiesclaims.models.*

object TestScheduleData {

  val exampleGiftAidScheduleData: GiftAidScheduleData = GiftAidScheduleData(
    earliestDonationDate = "2025-01-01",
    prevOverclaimedGiftAid = 2000.00,
    totalDonations = 1000.00,
    donations = Seq(
      Donation(donationItem = Some(1), donationDate = "2025-01-01", donationAmount = 1000.00),
      Donation(donationItem = Some(2), donationDate = "2025-01-02", donationAmount = 2000.00),
      Donation(donationItem = Some(3), donationDate = "2025-01-03", donationAmount = 3000.00)
    )
  )

  val exampleOtherIncomeScheduleData: OtherIncomeScheduleData = OtherIncomeScheduleData(
    adjustmentForOtherIncomePreviousOverClaimed = 1000.00,
    totalOfGrossPayments = 2000.00,
    totalOfTaxDeducted = 1000.00,
    otherIncomes = Seq(
      OtherIncome(
        otherIncomeItem = 1,
        payerName = "test-payer-name",
        paymentDate = "2025-01-01",
        grossPayment = 1000.00,
        taxDeducted = 100.00
      ),
      OtherIncome(
        otherIncomeItem = 2,
        payerName = "test-payer-name-2",
        paymentDate = "2025-01-02",
        grossPayment = 2000.00,
        taxDeducted = 200.00
      )
    )
  )

  val exampleCommunityBuildingsScheduleData: CommunityBuildingsScheduleData = CommunityBuildingsScheduleData(
    totalOfAllAmounts = 1000.00,
    communityBuildings = Seq(
      CommunityBuilding1(
        communityBuildingItem = 1,
        buildingName = "test-building-name",
        firstLineOfAddress = "test-building-address",
        postcode = "test-building-postcode",
        taxYear1 = 2025,
        amountYear1 = 1001.00,
        taxYear2 = Some(2026),
        amountYear2 = Some(1002.00)
      ),
      CommunityBuilding1(
        communityBuildingItem = 2,
        buildingName = "test-building-name-2",
        firstLineOfAddress = "test-building-address-2",
        postcode = "test-building-postcode-2",
        taxYear1 = 2025,
        amountYear1 = 2001.00
      )
    )
  )

  val exampleConnectedCharitiesScheduleData: ConnectedCharitiesScheduleData = ConnectedCharitiesScheduleData(
    charities = Seq(
      ConnectedCharity(
        charityItem = 1,
        charityName = "test-charity-name-1",
        charityReference = "test-charity-reference-1"
      ),
      ConnectedCharity(
        charityItem = 2,
        charityName = "test-charity-name-2",
        charityReference = "test-charity-reference-2"
      )
    )
  )
}
