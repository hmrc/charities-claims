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

package uk.gov.hmrc.charitiesclaims.models.audit

import play.api.libs.json.{Json, OFormat}

final case class AuditEvent(
  claimId: String,
  userId: String,
  claimSubmitted: Boolean,
  creationTimestamp: String,
  claimData: AuditClaimData,
  submissionDetails: Option[AuditSubmissionDetails] = None
)

final case class AuditClaimData(
  repaymentClaimDetails: AuditRepaymentClaimDetails,
  organisationDetails: Option[AuditOrganisationDetails],
  giftAidScheduleData: Option[AuditGiftAidScheduleData],
  otherIncomeScheduleData: Option[AuditOtherIncomeScheduleData],
  giftAidSmallDonationsSchemeScheduleData: Option[AuditGiftAidSmallDonationsSchemeScheduleData],
  declarationDetails: AuditDeclarationDetails
)

final case class AuditRepaymentClaimDetails(
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

final case class AuditOrganisationDetails(
  nameOfCharityRegulator: String,
  reasonNotRegisteredWithRegulator: Option[String] = None,
  charityRegistrationNumber: Option[String] = None,
  areYouACorporateTrustee: Boolean,
  nameOfCorporateTrustee: Option[String] = None,
  corporateTrusteePostcode: Option[String] = None,
  // TODO: It doesn't exist in data model. Need to build it.
  // notCorporateTrusteePostcode: Option[Boolean] = None,
  corporateTrusteeDaytimeTelephoneNumber: Option[String] = None,
  authorisedOfficialTrusteeTitle: Option[String] = None,
  authorisedOfficialTrusteeFirstName: Option[String] = None,
  authorisedOfficialTrusteeLastName: Option[String] = None,
  authorisedOfficialTrusteePostcode: Option[String] = None,
  authorisedOfficialTrusteeDaytimeTelephoneNumber: Option[String] = None
)

final case class AuditGiftAidScheduleData(
  earliestDonationDate: String,
  prevOverclaimedGiftAid: Option[BigDecimal] = None,
  totalDonations: BigDecimal,
  donations: Seq[AuditDonation]
)

final case class AuditDonation(
  donationItem: Option[Int] = None,
  aggregatedDonations: Option[String] = None,
  donorTitle: Option[String] = None,
  donorFirstName: Option[String] = None,
  donorLastName: Option[String] = None,
  donorHouse: Option[String] = None,
  donorPostcode: Option[String] = None,
  sponsoredEvent: Option[Boolean] = None,
  donationDate: String,
  donationAmount: BigDecimal
)

final case class AuditOtherIncomeScheduleData(
  adjustmentForOtherIncomePreviousOverClaimed: BigDecimal,
  totalOfGrossPayments: BigDecimal,
  totalOfTaxDeducted: BigDecimal,
  otherIncomes: Seq[AuditOtherIncome]
)

final case class AuditOtherIncome(
  otherIncomeItem: Int,
  payerName: String,
  paymentDate: String,
  grossPayment: BigDecimal,
  taxDeducted: BigDecimal
)

final case class AuditGiftAidSmallDonationsSchemeScheduleData(
  totalDonations: BigDecimal,
  adjustmentForGiftAidOverClaimed: BigDecimal,
  claims: Seq[AuditGiftAidSmallDonationsSchemeClaim],
  connectedCharitiesScheduleData: Seq[AuditConnectedCharitiesScheduleData],
  communityBuildingsScheduleData: Seq[AuditCommunityBuildingsScheduleData]
)

final case class AuditGiftAidSmallDonationsSchemeClaim(
  taxYear: Int,
  amountOfDonationsReceived: BigDecimal
)

final case class AuditConnectedCharitiesScheduleData(
  charityItem: Int,
  charityName: String,
  charityReference: String
)

final case class AuditCommunityBuildingsScheduleData(
  communityBuildingItem: Int,
  buildingName: String,
  firstLineOfAddress: String,
  postcode: String,
  taxYear1: Int,
  amountYear1: BigDecimal,
  taxYear2: Option[Int] = None,
  amountYear2: Option[BigDecimal] = None
  // TODO: taxYearThreeEnd and taxYearThreeAmount doesn't exist in data model. Need to update data model.
  // taxYear3: Option[Int] = None,
  // amountYear3: Option[BigDecimal] = None
)

final case class AuditDeclarationDetails(
  understandFalseStatements: Option[Boolean] = None,
  includedAnyAdjustmentsInClaimPrompt: Option[String] = None
  // TODO: Doesn't exist in data model.
  // language: String
)

final case class AuditSubmissionDetails(
  submissionTimestamp: String,
  submissionReference: String
)

object AuditEventFormats {

  implicit val auditDonationFormat: OFormat[AuditDonation] =
    Json.format[AuditDonation]

  implicit val auditOtherIncomeFormat: OFormat[AuditOtherIncome] =
    Json.format[AuditOtherIncome]

  implicit val auditConnectedCharitiesFormat: OFormat[AuditConnectedCharitiesScheduleData] =
    Json.format[AuditConnectedCharitiesScheduleData]

  implicit val auditCommunityBuildingsFormat: OFormat[AuditCommunityBuildingsScheduleData] =
    Json.format[AuditCommunityBuildingsScheduleData]

  implicit val auditGiftAidSmallDonationsSchemeClaimFormat: OFormat[AuditGiftAidSmallDonationsSchemeClaim] =
    Json.format[AuditGiftAidSmallDonationsSchemeClaim]

  implicit val auditGiftAidScheduleDataFormat: OFormat[AuditGiftAidScheduleData] =
    Json.format[AuditGiftAidScheduleData]

  implicit val auditOtherIncomeScheduleDataFormat: OFormat[AuditOtherIncomeScheduleData] =
    Json.format[AuditOtherIncomeScheduleData]

  implicit val auditGiftAidSmallDonationsSchemeScheduleDataFormat
    : OFormat[AuditGiftAidSmallDonationsSchemeScheduleData] =
    Json.format[AuditGiftAidSmallDonationsSchemeScheduleData]

  implicit val auditRepaymentClaimDetailsFormat: OFormat[AuditRepaymentClaimDetails] =
    Json.format[AuditRepaymentClaimDetails]

  implicit val auditOrganisationDetailsFormat: OFormat[AuditOrganisationDetails] =
    Json.format[AuditOrganisationDetails]

  implicit val auditDeclarationDetailsFormat: OFormat[AuditDeclarationDetails] =
    Json.format[AuditDeclarationDetails]

  implicit val auditSubmissionDetailsFormat: OFormat[AuditSubmissionDetails] =
    Json.format[AuditSubmissionDetails]

  implicit val auditClaimDataFormat: OFormat[AuditClaimData] =
    Json.format[AuditClaimData]

  implicit val auditEventFormat: OFormat[AuditEvent] =
    Json.format[AuditEvent]
}
