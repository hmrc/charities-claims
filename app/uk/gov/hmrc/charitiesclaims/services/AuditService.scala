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

package uk.gov.hmrc.charitiesclaims.services

import javax.inject.Inject
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.charitiesclaims.models.{Claim, CommunityBuildingsScheduleData, ConnectedCharitiesScheduleData, GiftAidScheduleData, GiftAidSmallDonationsSchemeDonationDetails, OrganisationDetails, OtherIncomeScheduleData, RepaymentClaimDetails, ScheduleData, SubmissionDetails}
import uk.gov.hmrc.charitiesclaims.models.audit.{AuditClaimData, AuditCommunityBuildingsScheduleData, AuditConnectedCharitiesScheduleData, AuditDeclarationDetails, AuditDonation, AuditEvent, AuditGiftAidScheduleData, AuditGiftAidSmallDonationsSchemeClaim, AuditGiftAidSmallDonationsSchemeScheduleData, AuditOrganisationDetails, AuditOtherIncome, AuditOtherIncomeScheduleData, AuditRepaymentClaimDetails, AuditSubmissionDetails}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.charitiesclaims.models.audit.AuditEventFormats._

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class AuditService @Inject() (
  auditConnector: AuditConnector
)(implicit ec: ExecutionContext) {

  private val auditSource: String = "charities-claims"
  private val auditType: String   = "ClaimSubmission"

  private def buildAuditEvent(claim: Claim, scheduleData: ScheduleData, creationTimestamp: Instant) =
    AuditEvent(
      claimId = claim.claimId,
      userId = claim.userId,
      claimSubmitted = claim.claimSubmitted,
      creationTimestamp = creationTimestamp.toString,
      claimData = buildAuditClaimData(claim, scheduleData),
      submissionDetails = buildSubmissionDetails(claim.submissionDetails)
    )

  private def buildAuditClaimData(claim: Claim, scheduleData: ScheduleData): AuditClaimData = {

    val dRepaymentClaimDetails: RepaymentClaimDetails                                                    = claim.claimData.repaymentClaimDetails
    val odOrganisationDetails: Option[OrganisationDetails]                                               = claim.claimData.organisationDetails
    val odGiftAid: Option[GiftAidScheduleData]                                                           = scheduleData.giftAid
    val odOtherIncome: Option[OtherIncomeScheduleData]                                                   = scheduleData.otherIncome
    val odConnectedCharities: Option[ConnectedCharitiesScheduleData]                                     = scheduleData.connectedCharities
    val odCommunityBuildings: Option[CommunityBuildingsScheduleData]                                     = scheduleData.communityBuildings
    val odGiftAidSmallDonationsSchemeDonationDetails: Option[GiftAidSmallDonationsSchemeDonationDetails] =
      claim.claimData.giftAidSmallDonationsSchemeDonationDetails

    val auditRepaymentClaimDetails: AuditRepaymentClaimDetails =
      AuditRepaymentClaimDetails(
        claimingGiftAid = dRepaymentClaimDetails.claimingGiftAid,
        claimingTaxDeducted = dRepaymentClaimDetails.claimingTaxDeducted,
        claimingUnderGiftAidSmallDonationsScheme = dRepaymentClaimDetails.claimingUnderGiftAidSmallDonationsScheme,
        claimReferenceNumber = dRepaymentClaimDetails.claimReferenceNumber,
        claimingDonationsNotFromCommunityBuilding = dRepaymentClaimDetails.claimingDonationsNotFromCommunityBuilding,
        claimingDonationsCollectedInCommunityBuildings =
          dRepaymentClaimDetails.claimingDonationsCollectedInCommunityBuildings,
        connectedToAnyOtherCharities = dRepaymentClaimDetails.connectedToAnyOtherCharities,
        makingAdjustmentToPreviousClaim = dRepaymentClaimDetails.makingAdjustmentToPreviousClaim,
        hmrcCharitiesReference = dRepaymentClaimDetails.hmrcCharitiesReference,
        nameOfCharity = dRepaymentClaimDetails.nameOfCharity
      )

    val auditOrganisationDetails: Option[AuditOrganisationDetails] =
      odOrganisationDetails.map { dOrganisationDetails =>
        AuditOrganisationDetails(
          nameOfCharityRegulator = dOrganisationDetails.nameOfCharityRegulator.value,
          reasonNotRegisteredWithRegulator = dOrganisationDetails.reasonNotRegisteredWithRegulator.map(_.value),
          charityRegistrationNumber = dOrganisationDetails.charityRegistrationNumber,
          areYouACorporateTrustee = dOrganisationDetails.areYouACorporateTrustee,
          nameOfCorporateTrustee = dOrganisationDetails.nameOfCorporateTrustee,
          corporateTrusteePostcode = dOrganisationDetails.corporateTrusteePostcode,
          corporateTrusteeDaytimeTelephoneNumber = dOrganisationDetails.corporateTrusteeDaytimeTelephoneNumber,
          authorisedOfficialTitle = dOrganisationDetails.authorisedOfficialTrusteeTitle,
          authorisedOfficialFirstName = dOrganisationDetails.authorisedOfficialTrusteeFirstName,
          authorisedOfficialLastName = dOrganisationDetails.authorisedOfficialTrusteeLastName,
          authorisedOfficialPostcode = dOrganisationDetails.authorisedOfficialTrusteePostcode,
          authorisedOfficialDaytimeTelephoneNumber =
            dOrganisationDetails.authorisedOfficialTrusteeDaytimeTelephoneNumber
        )
      }

    val auditDonations: Seq[AuditDonation] = odGiftAid
      .map(_.donations.map { donation =>
        AuditDonation(
          donationItem = donation.donationItem,
          aggregatedDonations = donation.aggregatedDonations,
          donorTitle = donation.donorTitle,
          donorFirstName = donation.donorFirstName,
          donorLastName = donation.donorLastName,
          donorHouse = donation.donorHouse,
          donorPostcode = donation.donorPostcode,
          sponsoredEvent = donation.sponsoredEvent,
          donationDate = donation.donationDate,
          donationAmount = donation.donationAmount
        )
      })
      .getOrElse(Nil)

    val auditGiftAidScheduleData: Option[AuditGiftAidScheduleData] =
      odGiftAid.map { dGiftAid =>
        AuditGiftAidScheduleData(
          earliestDonationDate = dGiftAid.earliestDonationDate,
          previouslyOverclaimedGiftAid = dGiftAid.prevOverclaimedGiftAid,
          totalDonations = dGiftAid.totalDonations,
          donations = auditDonations
        )
      }

    val auditOtherIncomes: Seq[AuditOtherIncome] = odOtherIncome
      .map(_.otherIncomes.map { otherIncome =>
        AuditOtherIncome(
          otherIncomeItem = otherIncome.otherIncomeItem,
          payerName = otherIncome.payerName,
          paymentDate = otherIncome.paymentDate,
          grossPayment = otherIncome.grossPayment,
          taxDeducted = otherIncome.taxDeducted
        )
      })
      .getOrElse(Nil)

    val auditOtherIncomeScheduleData =
      odOtherIncome.map { dOtherIncome =>
        AuditOtherIncomeScheduleData(
          adjustmentForOtherIncomePreviousOverClaimed = dOtherIncome.adjustmentForOtherIncomePreviousOverClaimed,
          totalOfGrossPayments = dOtherIncome.totalOfGrossPayments,
          totalOfTaxDeducted = dOtherIncome.totalOfTaxDeducted,
          otherIncomes = auditOtherIncomes
        )
      }

    val auditGiftAidSmallDonationsSchemeClaim: Seq[AuditGiftAidSmallDonationsSchemeClaim] =
      odGiftAidSmallDonationsSchemeDonationDetails
        .map(_.claims.map { claim =>
          AuditGiftAidSmallDonationsSchemeClaim(
            taxYear = claim.taxYear,
            amountOfDonationsReceived = claim.amountOfDonationsReceived
          )
        })
        .getOrElse(Nil)

    val auditConnectedCharitiesScheduleData: Seq[AuditConnectedCharitiesScheduleData] =
      odConnectedCharities
        .map(_.charities.map { connectedCharity =>
          AuditConnectedCharitiesScheduleData(
            charityItem = connectedCharity.charityItem,
            charityName = connectedCharity.charityName,
            charityReference = connectedCharity.charityReference
          )
        })
        .getOrElse(Nil)

    val auditCommunityBuildingsScheduleData: Seq[AuditCommunityBuildingsScheduleData] =
      odCommunityBuildings
        .map(_.communityBuildings.map { communityBuilding =>
          AuditCommunityBuildingsScheduleData(
            buildingItem = communityBuilding.communityBuildingItem,
            buildingName = communityBuilding.buildingName,
            firstLineOfAddress = communityBuilding.firstLineOfAddress,
            postcode = communityBuilding.postcode,
            taxYearOneEnd = communityBuilding.taxYear1,
            taxYearOneAmount = communityBuilding.amountYear1,
            taxYearTwoEnd = communityBuilding.taxYear2,
            taxYearTwoAmount = communityBuilding.amountYear2
          )
        })
        .getOrElse(Nil)

    val auditGiftAidSmallDonationsSchemeScheduleData: Option[AuditGiftAidSmallDonationsSchemeScheduleData] =
      odGiftAidSmallDonationsSchemeDonationDetails
        .map { dGiftAidSmallDonationsSchemeDonationDetails =>
          AuditGiftAidSmallDonationsSchemeScheduleData(
            adjustmentForGiftAidOverClaimed =
              dGiftAidSmallDonationsSchemeDonationDetails.adjustmentForGiftAidOverClaimed,
            claims = auditGiftAidSmallDonationsSchemeClaim,
            connectedCharitiesScheduleData = auditConnectedCharitiesScheduleData,
            communityBuildingsScheduleData = auditCommunityBuildingsScheduleData
          )
        }

    val auditDeclarationDetails =
      AuditDeclarationDetails(
        understandFalseStatements = claim.claimData.understandFalseStatements,
        includedAnyAdjustmentsInClaimPrompt = claim.claimData.includedAnyAdjustmentsInClaimPrompt
      )

    AuditClaimData(
      repaymentClaimDetails = auditRepaymentClaimDetails,
      organisationDetails = auditOrganisationDetails,
      giftAidScheduleData = auditGiftAidScheduleData,
      otherIncomeScheduleData = auditOtherIncomeScheduleData,
      giftAidSmallDonationsSchemeScheduleData = auditGiftAidSmallDonationsSchemeScheduleData,
      declarationDetails = auditDeclarationDetails
    )
  }

  private def buildSubmissionDetails(oSubmissionDetails: Option[SubmissionDetails]): Option[AuditSubmissionDetails] =
    oSubmissionDetails.map { submissionDetails =>
      AuditSubmissionDetails(
        submissionTimestamp = submissionDetails.submissionTimestamp,
        submissionReference = submissionDetails.submissionReference
      )
    }

  def sendEvent(claim: Claim, scheduleData: ScheduleData, creationTimestamp: Instant)(implicit
    hc: HeaderCarrier
  ): Future[AuditResult] = {
    val extendedDataEvent = ExtendedDataEvent(
      auditSource = auditSource,
      auditType = auditType,
      detail = Json.toJson(buildAuditEvent(claim, scheduleData, creationTimestamp))
    )

    auditConnector.sendExtendedEvent(extendedDataEvent)
  }

}
