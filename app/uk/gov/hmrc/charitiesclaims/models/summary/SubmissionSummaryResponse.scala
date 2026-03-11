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

package uk.gov.hmrc.charitiesclaims.models.summary

import play.api.libs.json.{Json, OFormat}

case class SubmissionSummaryResponse(
  claimDetails: ClaimDetails,
  giftAidDetails: Option[GiftAidDetails],
  otherIncomeDetails: Option[OtherIncomeDetails],
  gasdsDetails: Option[GasdsDetails],
  adjustmentDetails: Option[AdjustmentDetails],
  submissionReferenceNumber: String
)

object SubmissionSummaryResponse {
  implicit val format: OFormat[SubmissionSummaryResponse] = Json.format[SubmissionSummaryResponse]
}

case class ClaimDetails(
  charityName: String,
  hmrcCharityReference: String,
  submissionTimestamp: String,
  submittedBy: String
)

object ClaimDetails {
  implicit val format: OFormat[ClaimDetails] = Json.format[ClaimDetails]
}

case class GiftAidDetails(
  numberGiftAidDonations: Int,
  totalValueGiftAidDonations: BigDecimal
)

object GiftAidDetails {
  implicit val format: OFormat[GiftAidDetails] = Json.format[GiftAidDetails]
}

case class OtherIncomeDetails(
  numberOtherIncomeItems: Int,
  totalValueOtherIncomeItems: BigDecimal
)

object OtherIncomeDetails {
  implicit val format: OFormat[OtherIncomeDetails] = Json.format[OtherIncomeDetails]
}

case class GasdsDetails(
  totalValueGasdsNotInCommunityBuilding: Option[BigDecimal],
  numberCommunityBuildings: Option[Int],
  totalValueGasdsInCommunityBuilding: Option[BigDecimal],
  numberConnectedCharities: Option[Int]
)

object GasdsDetails {
  implicit val format: OFormat[GasdsDetails] = Json.format[GasdsDetails]
}

case class AdjustmentDetails(
  previouslyOverclaimedGiftAidOtherIncome: Option[BigDecimal],
  previouslyOverclaimedGasds: Option[BigDecimal]
)

object AdjustmentDetails {
  implicit val format: OFormat[AdjustmentDetails] = Json.format[AdjustmentDetails]
}
