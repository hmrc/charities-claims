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

import cats.syntax.traverse.*
import com.google.inject.ImplementedBy
import uk.gov.hmrc.charitiesclaims.connectors.{ClaimsValidationConnector, RdsDatacacheProxyConnector}
import uk.gov.hmrc.charitiesclaims.models.*
import uk.gov.hmrc.charitiesclaims.models.summary.*
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SubmissionSummaryServiceImpl])
trait SubmissionSummaryService {

  def getSummary(
    claim: Claim,
    currentUser: CurrentUser
  )(using HeaderCarrier): Future[SubmissionSummaryResponse]
}

@Singleton
class SubmissionSummaryServiceImpl @Inject() (
  rdsConnector: RdsDatacacheProxyConnector,
  claimsValidationConnector: ClaimsValidationConnector
)(using ExecutionContext)
    extends SubmissionSummaryService {

  override def getSummary(
    claim: Claim,
    currentUser: CurrentUser
  )(using HeaderCarrier): Future[SubmissionSummaryResponse] =
    for
      orgOrAgentName         <- getAgentOrOrgName(currentUser)
      giftAidData            <- getGiftAidUploadData(claim)
      communityBuildingsData <- getCommunityBuildingsUploadData(claim)
      connectedCharitiesData <- getConnectedCharitiesUploadData(claim)
      otherIncomeData        <- getOtherIncomeUploadData(claim)
    yield SubmissionSummaryResponse(
      claimDetails = buildClaimDetails(claim, currentUser, orgOrAgentName),
      giftAidDetails = buildGiftAidDetails(giftAidData),
      otherIncomeDetails = buildOtherIncomeDetails(otherIncomeData),
      gasdsDetails = buildGasdsDetails(
        communityBuildingsData,
        connectedCharitiesData,
        claim.claimData.giftAidSmallDonationsSchemeDonationDetails
      ),
      adjustmentDetails = buildAdjustmentDetails(
        giftAidData,
        otherIncomeData,
        claim.claimData.giftAidSmallDonationsSchemeDonationDetails
      ),
      submissionReferenceNumber = claim.submissionDetails.fold("")(_.submissionReference)
    )

  private def buildClaimDetails(
    claim: Claim,
    currentUser: CurrentUser,
    orgOrAgentName: String
  ): ClaimDetails =
    ClaimDetails(
      charityName = orgOrAgentName,
      hmrcCharityReference = getCharityRef(currentUser),
      submissionTimestamp = claim.submissionDetails.map(_.submissionTimestamp).getOrElse(""),
      submittedBy = getSubmittedBy(claim)
    )

  private def buildGiftAidDetails(
    giftAidData: Option[GiftAidScheduleData]
  ): Option[GiftAidDetails] =
    giftAidData.map { data =>
      GiftAidDetails(
        numberGiftAidDonations = data.donations.size,
        totalValueGiftAidDonations = data.totalDonations
      )
    }

  private def buildOtherIncomeDetails(
    otherIncomeData: Option[OtherIncomeScheduleData]
  ): Option[OtherIncomeDetails] =
    otherIncomeData.map { data =>
      OtherIncomeDetails(
        numberOtherIncomeItems = data.otherIncomes.size,
        totalValueOtherIncomeItems = data.totalOfTaxDeducted
      )
    }

  private def buildGasdsDetails(
    communityBuildingsData: Option[CommunityBuildingsScheduleData],
    connectedCharitiesData: Option[ConnectedCharitiesScheduleData],
    giftAidSmallDonationsSchemeDonationDetails: Option[GiftAidSmallDonationsSchemeDonationDetails]
  ): Option[GasdsDetails] =
    Option.when(
      communityBuildingsData.nonEmpty
        || connectedCharitiesData.nonEmpty
        || giftAidSmallDonationsSchemeDonationDetails.nonEmpty
    ) {
      GasdsDetails(
        totalValueGasdsNotInCommunityBuilding =
          giftAidSmallDonationsSchemeDonationDetails.flatMap(_.totalAmountOfDonationsReceived),
        numberCommunityBuildings = communityBuildingsData.map(_.communityBuildings.size),
        totalValueGasdsInCommunityBuilding = communityBuildingsData.map(_.totalOfAllAmounts),
        numberConnectedCharities = connectedCharitiesData.map(_.charities.size)
      )
    }

  extension (option: Option[BigDecimal]) {
    def nonZeroOption: Option[BigDecimal] = option.filter(_ > BigDecimal(0))
  }

  private def buildAdjustmentDetails(
    giftAidData: Option[GiftAidScheduleData],
    otherIncomeData: Option[OtherIncomeScheduleData],
    giftAidSmallDonationsSchemeDonationDetails: Option[GiftAidSmallDonationsSchemeDonationDetails]
  ): Option[AdjustmentDetails] = {
    val giftAidAdjustment     = giftAidData.flatMap(_.prevOverclaimedGiftAid).nonZeroOption
    val otherIncomeAdjustment = otherIncomeData.map(_.adjustmentForOtherIncomePreviousOverClaimed).nonZeroOption
    val gasdsAdjustment       =
      giftAidSmallDonationsSchemeDonationDetails.map(_.adjustmentForGiftAidOverClaimed).nonZeroOption

    Option.when(
      giftAidAdjustment.isDefined || otherIncomeAdjustment.isDefined || gasdsAdjustment.isDefined
    ) {
      AdjustmentDetails(
        previouslyOverclaimedGiftAidOtherIncome = Some(
          giftAidAdjustment.getOrElse(BigDecimal(0)) + otherIncomeAdjustment.getOrElse(BigDecimal(0))
        ).nonZeroOption,
        previouslyOverclaimedGasds = gasdsAdjustment
      )
    }
  }

  private def getCharityRef(currentUser: CurrentUser): String =
    // TODO: It will be implemented as part of Agent flow
    if currentUser.isAgent then "HMRC Charities Reference"
    else currentUser.enrolmentIdentifierValue

  private def getSubmittedBy(claim: Claim): String =
    claim.claimData.organisationDetails
      .flatMap { org =>
        if org.areYouACorporateTrustee then org.nameOfCorporateTrustee
        else {
          val name =
            Seq(
              org.authorisedOfficialTrusteeTitle,
              org.authorisedOfficialTrusteeFirstName,
              org.authorisedOfficialTrusteeLastName
            ).flatten.mkString(" ")

          Option.when(name.nonEmpty)(name)
        }
      }
      .getOrElse("")

  private def getAgentOrOrgName(currentUser: CurrentUser)(using HeaderCarrier): Future[String] =
    if currentUser.isAgent then Future.successful("CASC")
    else
      rdsConnector
        .getOrganisationName(currentUser.enrolmentIdentifierValue)
        .map(
          _.getOrElse(
            throw new Exception(
              s"No org name found for ${currentUser.enrolmentIdentifierValue}"
            )
          )
        )

  def getCommunityBuildingsUploadData(
    claim: Claim
  )(using HeaderCarrier): Future[Option[CommunityBuildingsScheduleData]] =
    claim.claimData.communityBuildingsScheduleFileUploadReference.flatTraverse { ref =>
      claimsValidationConnector
        .getUploadResult(claim.claimId, ref)
        .map(_.collect { case GetUploadResultValidatedCommunityBuildings(_, data) => data })
    }

  def getGiftAidUploadData(
    claim: Claim
  )(using HeaderCarrier): Future[Option[GiftAidScheduleData]] =
    claim.claimData.giftAidScheduleFileUploadReference.flatTraverse { ref =>
      claimsValidationConnector
        .getUploadResult(claim.claimId, ref)
        .map(_.collect { case GetUploadResultValidatedGiftAid(_, data) => data })
    }

  def getConnectedCharitiesUploadData(
    claim: Claim
  )(using HeaderCarrier): Future[Option[ConnectedCharitiesScheduleData]] =
    claim.claimData.connectedCharitiesScheduleFileUploadReference.flatTraverse { ref =>
      claimsValidationConnector
        .getUploadResult(claim.claimId, ref)
        .map(_.collect { case GetUploadResultValidatedConnectedCharities(_, data) => data })
    }

  def getOtherIncomeUploadData(
    claim: Claim
  )(using HeaderCarrier): Future[Option[OtherIncomeScheduleData]] =
    claim.claimData.otherIncomeScheduleFileUploadReference.flatTraverse { ref =>
      claimsValidationConnector
        .getUploadResult(claim.claimId, ref)
        .map(_.collect { case GetUploadResultValidatedOtherIncome(_, data) => data })
    }
}
