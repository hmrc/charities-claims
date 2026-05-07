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

import play.api.libs.json.Format
import play.api.libs.json.Json

final case class Claim(
  claimId: String,
  userId: String,
  claimSubmitted: Boolean,
  lastUpdatedReference: String,
  claimData: ClaimData,
  submissionDetails: Option[SubmissionDetails] = None,
  lastVisitedAt: Option[Long] = None
) {

  def uploadReferences: Set[FileUploadReference] = Set(
    claimData.giftAidScheduleFileUploadReference,
    claimData.otherIncomeScheduleFileUploadReference,
    claimData.communityBuildingsScheduleFileUploadReference,
    claimData.connectedCharitiesScheduleFileUploadReference
  ).collect { case Some(reference) => reference }
}

object Claim {
  given Format[Claim] = Json.format[Claim]
}

final case class ClaimData(
  repaymentClaimDetails: RepaymentClaimDetails,
  organisationDetails: Option[OrganisationDetails] = None,
  agentUserOrganisationDetails: Option[AgentUserOrganisationDetails] = None,
  understandFalseStatements: Option[Boolean] = None,
  includedAnyAdjustmentsInClaimPrompt: Option[String] = None,
  giftAidSmallDonationsSchemeDonationDetails: Option[GiftAidSmallDonationsSchemeDonationDetails] = None,
  giftAidScheduleFileUploadReference: Option[FileUploadReference] = None,
  otherIncomeScheduleFileUploadReference: Option[FileUploadReference] = None,
  communityBuildingsScheduleFileUploadReference: Option[FileUploadReference] = None,
  connectedCharitiesScheduleFileUploadReference: Option[FileUploadReference] = None,
  adjustmentForOtherIncomePreviousOverClaimed: Option[BigDecimal] = None,
  prevOverclaimedGiftAid: Option[BigDecimal] = None
)

object ClaimData {
  given Format[ClaimData] = Json.format[ClaimData]
}

final case class RepaymentClaimDetails(
  claimingGiftAid: Boolean,
  claimingTaxDeducted: Boolean,
  claimingUnderGiftAidSmallDonationsScheme: Boolean,
  claimReferenceNumber: Option[String] = None,
  claimingDonationsNotFromCommunityBuilding: Option[Boolean] = None,
  claimingDonationsCollectedInCommunityBuildings: Option[Boolean] = None,
  connectedToAnyOtherCharities: Option[Boolean] = None,
  makingAdjustmentToPreviousClaim: Option[Boolean] = None,
  hmrcCharitiesReference: Option[String] = None,
  nameOfCharity: Option[String] = None
)

object RepaymentClaimDetails {
  given Format[RepaymentClaimDetails] = Json.format[RepaymentClaimDetails]
}

final case class OrganisationDetails(
  nameOfCharityRegulator: NameOfCharityRegulator,
  reasonNotRegisteredWithRegulator: Option[ReasonNotRegisteredWithRegulator] = None,
  charityRegistrationNumber: Option[String] = None,
  areYouACorporateTrustee: Boolean,
  doYouHaveCorporateTrusteeUKAddress: Option[Boolean] = None,
  doYouHaveAuthorisedOfficialTrusteeUKAddress: Option[Boolean] = None,
  nameOfCorporateTrustee: Option[String] = None,
  corporateTrusteePostcode: Option[String] = None,
  corporateTrusteeDaytimeTelephoneNumber: Option[String] = None,
  authorisedOfficialTrusteePostcode: Option[String] = None,
  authorisedOfficialTrusteeDaytimeTelephoneNumber: Option[String] = None,
  authorisedOfficialTrusteeTitle: Option[String] = None,
  authorisedOfficialTrusteeFirstName: Option[String] = None,
  authorisedOfficialTrusteeLastName: Option[String] = None
)

object OrganisationDetails {
  given Format[OrganisationDetails] = Json.format[OrganisationDetails]
}

final case class AgentUserOrganisationDetails(
  whoShouldHmrcSendPaymentTo: String,
  daytimeTelephoneNumber: String,
  doYouHaveAgentUKAddress: Boolean,
  postcode: Option[String] = None,
  nameOfCharityRegulator: NameOfCharityRegulator,
  charityRegistrationNumber: Option[String] = None,
  unregulatedReason: Option[ReasonNotRegisteredWithRegulator] = None
)

object AgentUserOrganisationDetails {
  given Format[AgentUserOrganisationDetails] = Json.format[AgentUserOrganisationDetails]
}

final case class GiftAidScheduleData(
  earliestDonationDate: String,
  prevOverclaimedGiftAid: Option[BigDecimal] = None,
  totalDonations: BigDecimal,
  donations: Seq[Donation]
)

object GiftAidScheduleData {
  given Format[GiftAidScheduleData] = Json.format[GiftAidScheduleData]
}

final case class SubmissionDetails(
  submissionTimestamp: String,
  submissionReference: String
)

object SubmissionDetails {
  given Format[SubmissionDetails] = Json.format[SubmissionDetails]
}

final case class Donation(
  donationItem: Option[Int] = None,
  donationDate: String,
  donationAmount: BigDecimal,
  donorTitle: Option[String] = None,
  donorFirstName: Option[String] = None,
  donorLastName: Option[String] = None,
  donorHouse: Option[String] = None,
  donorPostcode: Option[String] = None,
  sponsoredEvent: Option[Boolean] = None,
  aggregatedDonations: Option[String] = None
)

object Donation {
  given Format[Donation] = Json.format[Donation]
}

final case class OtherIncomeScheduleData(
  adjustmentForOtherIncomePreviousOverClaimed: BigDecimal,
  totalOfGrossPayments: BigDecimal,
  totalOfTaxDeducted: BigDecimal,
  otherIncomes: Seq[OtherIncome]
)

object OtherIncomeScheduleData {
  given Format[OtherIncomeScheduleData] = Json.format[OtherIncomeScheduleData]
}

final case class OtherIncome(
  otherIncomeItem: Int,
  payerName: String,
  paymentDate: String,
  grossPayment: BigDecimal,
  taxDeducted: BigDecimal
)

object OtherIncome {
  given Format[OtherIncome] = Json.format[OtherIncome]
}

final case class GiftAidSmallDonationsSchemeDonationDetails(
  adjustmentForGiftAidOverClaimed: BigDecimal,
  claims: Seq[GiftAidSmallDonationsSchemeClaim]
) {
  def totalAmountOfDonationsReceived: Option[BigDecimal] =
    claims
      .map(_.amountOfDonationsReceived)
      .reduceOption(_ + _)
}

object GiftAidSmallDonationsSchemeDonationDetails {
  given Format[GiftAidSmallDonationsSchemeDonationDetails] = Json.format[GiftAidSmallDonationsSchemeDonationDetails]
}

final case class GiftAidSmallDonationsSchemeClaim(
  taxYear: Int,
  amountOfDonationsReceived: BigDecimal
)

object GiftAidSmallDonationsSchemeClaim {
  given Format[GiftAidSmallDonationsSchemeClaim] = Json.format[GiftAidSmallDonationsSchemeClaim]
}

final case class ConnectedCharity(
  charityItem: Int,
  charityName: String,
  charityReference: String
)

object ConnectedCharity {
  given Format[ConnectedCharity] = Json.format[ConnectedCharity]
}

final case class CommunityBuilding(
  communityBuildingItem: Int,
  buildingName: String,
  firstLineOfAddress: String,
  postcode: String,
  taxYear1: Int,
  amountYear1: BigDecimal,
  taxYear2: Option[Int] = None,
  amountYear2: Option[BigDecimal] = None
)

object CommunityBuilding {
  given Format[CommunityBuilding] = Json.format[CommunityBuilding]
}

case class CommunityBuildingsScheduleData(
  totalOfAllAmounts: BigDecimal,
  communityBuildings: Seq[CommunityBuilding]
)

object CommunityBuildingsScheduleData {
  given Format[CommunityBuildingsScheduleData] = Json.format[CommunityBuildingsScheduleData]
}

case class ConnectedCharitiesScheduleData(
  charities: Seq[ConnectedCharity]
)

object ConnectedCharitiesScheduleData {
  given Format[ConnectedCharitiesScheduleData] = Json.format[ConnectedCharitiesScheduleData]
}

final case class ScheduleData(
  giftAid: Option[GiftAidScheduleData] = None,
  connectedCharities: Option[ConnectedCharitiesScheduleData] = None,
  communityBuildings: Option[CommunityBuildingsScheduleData] = None,
  otherIncome: Option[OtherIncomeScheduleData] = None
)

object ScheduleData {
  val empty: ScheduleData = ScheduleData()
}
