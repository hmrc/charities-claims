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
import uk.gov.hmrc.charitiesclaims.models.{AgentUserOrganisationDetails, Claim, ClaimData, CommunityBuildingsScheduleData, ConnectedCharitiesScheduleData, GiftAidScheduleData, GiftAidSmallDonationsSchemeDonationDetails, OrganisationDetails, OtherIncomeScheduleData, RepaymentClaimDetails, ScheduleData, SubmissionDetails}
import uk.gov.hmrc.charitiesclaims.models.audit.{AuditAgentUserOrganisationDetails, AuditClaimData, AuditCommunityBuildingsScheduleData, AuditConnectedCharitiesScheduleData, AuditDeclarationDetails, AuditDonation, AuditEvent, AuditGiftAidScheduleData, AuditGiftAidSmallDonationsSchemeClaim, AuditGiftAidSmallDonationsSchemeScheduleData, AuditOrganisationDetails, AuditOtherIncome, AuditOtherIncomeScheduleData, AuditRepaymentClaimDetails}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.charitiesclaims.models.audit.AuditEventFormats.*

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class AuditService @Inject() (
  auditConnector: AuditConnector
)(implicit ec: ExecutionContext) {

  private val auditSource: String = "charities-claims"
  private val auditType: String   = "ClaimSubmission"

  private def buildAuditEvent(
    claim: Claim,
    scheduleData: ScheduleData,
    creationTimestamp: Instant,
    declarationLanguage: String,
    submissionDetails: SubmissionDetails
  ) =
    AuditEvent(
      claimId = claim.claimId,
      userId = claim.userId,
      userType = identifyUserType(claim.claimData),
      claimSubmitted = claim.claimSubmitted,
      creationTimestamp = creationTimestamp.toString,
      claimData = buildAuditClaimData(claim, scheduleData, declarationLanguage),
      submissionDetails = submissionDetails
    )

  private def identifyUserType(claimData: ClaimData): String =
    if claimData.organisationDetails.isDefined then "Organisation"
    else if claimData.agentUserOrganisationDetails.isDefined then "Agent"
    else "Unknown"

  private def buildAuditClaimData(
    claim: Claim,
    scheduleData: ScheduleData,
    declarationLanguage: String
  ): AuditClaimData = {

    val dRepaymentClaimDetails: RepaymentClaimDetails                                                    = claim.claimData.repaymentClaimDetails
    val odOrganisationDetails: Option[OrganisationDetails]                                               = claim.claimData.organisationDetails
    val odAgentUserOrganisationDetails: Option[AgentUserOrganisationDetails]                             =
      claim.claimData.agentUserOrganisationDetails
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
          notCorporateTrusteePostcode = Some(
            dOrganisationDetails.areYouACorporateTrustee &&
              (dOrganisationDetails.doYouHaveCorporateTrusteeUKAddress.contains(true) ||
                dOrganisationDetails.doYouHaveAuthorisedOfficialTrusteeUKAddress.contains(true))
          ),
          corporateTrusteeDaytimeTelephoneNumber = dOrganisationDetails.corporateTrusteeDaytimeTelephoneNumber,
          authorisedOfficialTrusteeTitle = dOrganisationDetails.authorisedOfficialTrusteeTitle,
          authorisedOfficialTrusteeFirstName = dOrganisationDetails.authorisedOfficialTrusteeFirstName,
          authorisedOfficialTrusteeLastName = dOrganisationDetails.authorisedOfficialTrusteeLastName,
          authorisedOfficialTrusteePostcode = dOrganisationDetails.authorisedOfficialTrusteePostcode,
          authorisedOfficialTrusteeDaytimeTelephoneNumber =
            dOrganisationDetails.authorisedOfficialTrusteeDaytimeTelephoneNumber
        )
      }

    val auditAgentUserOrganisationDetails: Option[AuditAgentUserOrganisationDetails] =
      odAgentUserOrganisationDetails.map { dAgentUserOrganisationDetails =>
        AuditAgentUserOrganisationDetails(
          whoShouldHmrcSendPaymentTo = dAgentUserOrganisationDetails.whoShouldHmrcSendPaymentTo,
          daytimeTelephoneNumber = dAgentUserOrganisationDetails.daytimeTelephoneNumber,
          doYouHaveAgentUKAddress = dAgentUserOrganisationDetails.doYouHaveAgentUKAddress,
          postcode = dAgentUserOrganisationDetails.postcode,
          nameOfCharityRegulator = dAgentUserOrganisationDetails.nameOfCharityRegulator.value,
          charityRegistrationNumber = dAgentUserOrganisationDetails.charityRegistrationNumber,
          reasonNotRegisteredWithRegulator = dAgentUserOrganisationDetails.reasonNotRegisteredWithRegulator
        )
      }

    val auditDonations: Option[Seq[AuditDonation]] =
      odGiftAid
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
        .filter(_.nonEmpty)

    val auditGiftAidScheduleData: Option[AuditGiftAidScheduleData] =
      odGiftAid.map { dGiftAid =>
        AuditGiftAidScheduleData(
          earliestDonationDate = dGiftAid.earliestDonationDate,
          prevOverclaimedGiftAid = dGiftAid.prevOverclaimedGiftAid,
          totalDonations = dGiftAid.totalDonations,
          donations = auditDonations
        )
      }

    val auditOtherIncomes: Option[Seq[AuditOtherIncome]] = odOtherIncome
      .map(_.otherIncomes.map { otherIncome =>
        AuditOtherIncome(
          otherIncomeItem = otherIncome.otherIncomeItem,
          payerName = otherIncome.payerName,
          paymentDate = otherIncome.paymentDate,
          grossPayment = otherIncome.grossPayment,
          taxDeducted = otherIncome.taxDeducted
        )
      })
      .filter(_.nonEmpty)

    val auditOtherIncomeScheduleData =
      odOtherIncome.map { dOtherIncome =>
        AuditOtherIncomeScheduleData(
          adjustmentForOtherIncomePreviousOverClaimed = dOtherIncome.adjustmentForOtherIncomePreviousOverClaimed,
          totalOfGrossPayments = dOtherIncome.totalOfGrossPayments,
          totalOfTaxDeducted = dOtherIncome.totalOfTaxDeducted,
          otherIncomes = auditOtherIncomes
        )
      }

    val auditGiftAidSmallDonationsSchemeClaim: Option[Seq[AuditGiftAidSmallDonationsSchemeClaim]] =
      odGiftAidSmallDonationsSchemeDonationDetails
        .map(_.claims.map { claim =>
          AuditGiftAidSmallDonationsSchemeClaim(
            taxYear = claim.taxYear,
            amountOfDonationsReceived = claim.amountOfDonationsReceived
          )
        })
        .filter(_.nonEmpty)

    val auditConnectedCharitiesScheduleData: Option[Seq[AuditConnectedCharitiesScheduleData]] =
      odConnectedCharities
        .map(_.charities.map { connectedCharity =>
          AuditConnectedCharitiesScheduleData(
            charityItem = connectedCharity.charityItem,
            charityName = connectedCharity.charityName,
            charityReference = connectedCharity.charityReference
          )
        })
        .filter(_.nonEmpty)

    val auditCommunityBuildingsScheduleData: Option[Seq[AuditCommunityBuildingsScheduleData]] =
      odCommunityBuildings
        .map(_.communityBuildings.map { communityBuilding =>
          AuditCommunityBuildingsScheduleData(
            communityBuildingItem = communityBuilding.communityBuildingItem,
            buildingName = communityBuilding.buildingName,
            firstLineOfAddress = communityBuilding.firstLineOfAddress,
            postcode = communityBuilding.postcode,
            taxYear1 = communityBuilding.taxYear1,
            amountYear1 = communityBuilding.amountYear1,
            taxYear2 = communityBuilding.taxYear2,
            amountYear2 = communityBuilding.amountYear2
          )
        })
        .filter(_.nonEmpty)

    val auditGiftAidSmallDonationsSchemeScheduleData: Option[AuditGiftAidSmallDonationsSchemeScheduleData] =
      Option.when(
        odGiftAidSmallDonationsSchemeDonationDetails.isDefined ||
          odConnectedCharities.isDefined ||
          odCommunityBuildings.isDefined
      ) {
        AuditGiftAidSmallDonationsSchemeScheduleData(
          totalDonations = odGiftAidSmallDonationsSchemeDonationDetails
            .flatMap(_.totalAmountOfDonationsReceived)
            .getOrElse(BigDecimal(0)),
          adjustmentForGiftAidOverClaimed = odGiftAidSmallDonationsSchemeDonationDetails
            .map(_.adjustmentForGiftAidOverClaimed)
            .getOrElse(BigDecimal(0)),
          claims = odGiftAidSmallDonationsSchemeDonationDetails.fold(None)(_ => auditGiftAidSmallDonationsSchemeClaim),
          connectedCharitiesScheduleData = auditConnectedCharitiesScheduleData,
          communityBuildingsScheduleData = auditCommunityBuildingsScheduleData
        )
      }

    val auditDeclarationDetails =
      AuditDeclarationDetails(
        understandFalseStatements = claim.claimData.understandFalseStatements,
        includedAnyAdjustmentsInClaimPrompt = claim.claimData.includedAnyAdjustmentsInClaimPrompt,
        language = declarationLanguage
      )

    AuditClaimData(
      repaymentClaimDetails = auditRepaymentClaimDetails,
      organisationDetails = auditOrganisationDetails,
      agentUserOrganisationDetails = auditAgentUserOrganisationDetails,
      giftAidScheduleData = auditGiftAidScheduleData,
      otherIncomeScheduleData = auditOtherIncomeScheduleData,
      giftAidSmallDonationsSchemeScheduleData = auditGiftAidSmallDonationsSchemeScheduleData,
      declarationDetails = auditDeclarationDetails
    )
  }

  def sendEvent(
    claim: Claim,
    scheduleData: ScheduleData,
    creationTimestamp: Instant,
    declarationLanguage: String,
    submissionDetails: SubmissionDetails
  )(implicit hc: HeaderCarrier): Future[AuditResult] = {
    val extendedDataEvent = ExtendedDataEvent(
      auditSource = auditSource,
      auditType = auditType,
      detail = Json.toJson(
        buildAuditEvent(claim, scheduleData, creationTimestamp, declarationLanguage, submissionDetails)
      )
    )

    auditConnector.sendExtendedEvent(extendedDataEvent)
  }

}
