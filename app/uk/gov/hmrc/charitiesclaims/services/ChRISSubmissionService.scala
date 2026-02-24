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

import uk.gov.hmrc.charitiesclaims.models.chris.*
import uk.gov.hmrc.charitiesclaims.models
import com.google.inject.ImplementedBy
import uk.gov.hmrc.charitiesclaims.connectors.{ClaimsValidationConnector, RdsDatacacheProxyConnector}
import uk.gov.hmrc.charitiesclaims.models.NameOfCharityRegulator
import uk.gov.hmrc.charitiesclaims.models.{CommunityBuildingsScheduleData, ConnectedCharitiesScheduleData, FileUploadReference, GetUploadResultValidatedCommunityBuildings, GetUploadResultValidatedConnectedCharities}

import scala.concurrent.Future
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext

import cats.syntax.traverse.*
import cats.instances.future.*
import cats.instances.option.*

@ImplementedBy(classOf[ChRISSubmissionServiceImpl])
trait ChRISSubmissionService {

  def buildChRISSubmission(claim: models.Claim, currentUser: models.CurrentUser)(using
    HeaderCarrier
  ): Future[GovTalkMessage]

}

@Singleton
class ChRISSubmissionServiceImpl @Inject() (
  rdsConnector: RdsDatacacheProxyConnector,
  claimsValidationConnector: ClaimsValidationConnector
)(using ExecutionContext)
    extends ChRISSubmissionService {

  def buildChRISSubmission(
    claim: models.Claim,
    currentUser: models.CurrentUser
  )(using HeaderCarrier): Future[GovTalkMessage] =

    for
      orgName                <- getOrganisationName(currentUser)
      communityBuildingsData <- getCommunityBuildingsUploadData(claim)
      connectedCharitiesData <- getConnectedCharitiesUploadData(claim)
    yield GovTalkMessage(
      GovTalkDetails = buildGovTalkDetails(currentUser),
      Body = Body(
        IRenvelope = IRenvelope(
          IRheader = buildIRheader(currentUser),
          R68 = buildR68(claim, currentUser, orgName, connectedCharitiesData, communityBuildingsData)
        )
      )
    ).withLiteIRmark

  def getOrganisationName(currentUser: models.CurrentUser)(using HeaderCarrier): Future[Option[String]] =
    if currentUser.isAgent
    then
      rdsConnector
        .getAgentName(currentUser.enrolmentIdentifierValue)
        .flatMap {
          case Some(agentName) => Future.successful(Some(agentName))
          case None            =>
            Future.failed(
              new Exception(
                s"No agent name found for the given agent reference ${currentUser.enrolmentIdentifierValue}"
              )
            )
        }
    else Future.successful(None)

  def getCommunityBuildingsUploadData(
    claim: models.Claim
  )(using HeaderCarrier): Future[Option[CommunityBuildingsScheduleData]] =
    claim.claimData.communityBuildingsScheduleFileUploadReference.flatTraverse { ref =>
      claimsValidationConnector
        .getUploadResult(claim.claimId, ref)
        .map(_.collect { case GetUploadResultValidatedCommunityBuildings(_, data) =>
          data
        })
    }

  def getConnectedCharitiesUploadData(
    claim: models.Claim
  )(using HeaderCarrier): Future[Option[ConnectedCharitiesScheduleData]] =
    claim.claimData.connectedCharitiesScheduleFileUploadReference.flatTraverse { ref =>
      claimsValidationConnector
        .getUploadResult(claim.claimId, ref)
        .map(_.collect { case GetUploadResultValidatedConnectedCharities(_, data) =>
          data
        })
    }

  def buildGovTalkDetails(currentUser: models.CurrentUser)(using hc: HeaderCarrier): GovTalkDetails =
    GovTalkDetails(
      Keys = List(
        Key(Type = "CredentialID", Value = currentUser.userId),
        if currentUser.isAgent
        then Key(Type = "CHARID", Value = "FOO") // TODO
        else Key(Type = currentUser.enrolmentIdentifierKey, Value = currentUser.enrolmentIdentifierValue),
        Key(Type = "SessionID", Value = hc.sessionId.map(_.value).getOrElse("unknown"))
      )
    )

  def buildIRheader(currentUser: models.CurrentUser): IRheader =
    IRheader(
      Keys = List(
        if currentUser.isAgent
        then Key(Type = "CHARID", Value = "FOO") // TODO
        else Key(Type = currentUser.enrolmentIdentifierKey, Value = currentUser.enrolmentIdentifierValue)
      ),
      PeriodEnd = "2012-01-01",
      Sender = "Other" // constant value
    )

  def buildR68(
    claim: models.Claim,
    currentUser: models.CurrentUser,
    orgName: Option[String],
    connectedCharitiesData: Option[ConnectedCharitiesScheduleData],
    communityBuildingsData: Option[CommunityBuildingsScheduleData]
  ): R68 =
    R68(
      AgtOrNom =
        if currentUser.isAgent
        then buildAgtOrNom(claim, orgName, currentUser.enrolmentIdentifierValue)
        else None,
      AuthOfficial =
        if currentUser.isAgent
        then None
        else buildAuthOfficial(claim),
      Declaration = true,
      Claim = buildClaim(claim, currentUser, connectedCharitiesData, communityBuildingsData)
    )

  def buildAuthOfficial(claim: models.Claim): Option[AuthOfficial] =
    claim.claimData.organisationDetails.map(organisationDetails =>
      AuthOfficial(
        Trustee =
          if organisationDetails.areYouACorporateTrustee then organisationDetails.nameOfCorporateTrustee else None,
        OffName = buildOffName(claim),
        ClaimNo = None, // TODO
        OffID = buildOffId(claim),
        Phone =
          if (organisationDetails.areYouACorporateTrustee)
            organisationDetails.corporateTrusteeDaytimeTelephoneNumber
          else
            organisationDetails.authorisedOfficialTrusteeDaytimeTelephoneNumber
      )
    )

  def buildAgtOrNom(claim: models.Claim, orgName: Option[String], refNo: String): Option[AgtOrNom] =
    for {
      o <- orgName
    } yield AgtOrNom(
      OrgName = o,
      RefNo = refNo,
      ClaimNo = claim.claimData.repaymentClaimDetails.hmrcCharitiesReference, // TODO
      PayToAoN =
        if claim.claimData.repaymentClaimDetails.hmrcCharitiesReference.contains("Tax Agent") then Some(true)
        else None, // TODO
      AoNID = None, // TODO
      Phone = "1234567890" // TODO
    )

  def buildOffName(claim: models.Claim): Option[OffName] =
    claim.claimData.organisationDetails.flatMap(organisationDetails =>
      if !organisationDetails.areYouACorporateTrustee
      then
        Some(
          OffName(
            Ttl = organisationDetails.authorisedOfficialTrusteeTitle,
            Fore = organisationDetails.authorisedOfficialTrusteeFirstName,
            Sur = organisationDetails.authorisedOfficialTrusteeLastName
          )
        )
      else None
    )

  def buildOffId(claim: models.Claim): Option[OffID] =
    claim.claimData.organisationDetails.map(organisationDetails =>
      // areYouACorporateTrustee == true and doYouHaveUKAddress == false then Overseas = "yes"
      // areYouACorporateTrustee == false and doYouHaveUKAddress == false then Overseas = "yes"
      // postcode
      // areYouACorporateTrustee == true and doYouHaveUKAddress == true then set the value of Postcode
      // areYouACorporateTrustee == false and doYouHaveUKAddress == true then set the value of Postcode
      OffID(
        Overseas =
          if organisationDetails.areYouACorporateTrustee && organisationDetails.doYouHaveCorporateTrusteeUKAddress
              .contains(false)
          then Some(true)
          else if !organisationDetails.areYouACorporateTrustee && organisationDetails.doYouHaveAuthorisedOfficialTrusteeUKAddress
              .contains(false)
          then Some(true)
          else None,
        Postcode =
          if organisationDetails.areYouACorporateTrustee && organisationDetails.doYouHaveCorporateTrusteeUKAddress
              .contains(true)
          then organisationDetails.corporateTrusteePostcode
          else if !organisationDetails.areYouACorporateTrustee && organisationDetails.doYouHaveAuthorisedOfficialTrusteeUKAddress
              .contains(true)
          then organisationDetails.authorisedOfficialTrusteePostcode
          else None
      )
    )

  def buildClaim(
    claim: models.Claim,
    currentUser: models.CurrentUser,
    connectedCharitiesData: Option[ConnectedCharitiesScheduleData],
    communityBuildingsData: Option[CommunityBuildingsScheduleData]
  ): Claim =
    Claim(
      // If user has an affinity group of "Agent", then set to the value of "Name of Charity or CASC"
      // Else set to the Organisation name returned from I3 - RDS DataCache Proxy Microservice - GetOrganisationNamebyCharityReference
      OrgName =
        if currentUser.isAgent
        then "CASC" // TODO
        else "", // TODO
      // If user has an affinity group of "Agent", then set to the value of "HMRC Charities Reference"
      // Else set to the Charities Reference (derived from their HMRC-CHAR-ORG enrolment and CHARID identifier)
      HMRCref =
        if currentUser.isAgent
        then "FOO" // TODO
        else currentUser.enrolmentIdentifierValue,
      Regulator = buildRegulator(claim),
      Repayment = buildRepayment(claim),
      GiftAidSmallDonationsScheme =
        buildGiftAidSmallDonationsScheme(claim, connectedCharitiesData, communityBuildingsData),
      OtherInfo = None // ToDo
    )

  def buildRegulator(claim: models.Claim): Option[Regulator] =
    claim.claimData.organisationDetails.map(organisationDetails =>
      Regulator(
        RegName = organisationDetails.nameOfCharityRegulator match {
          case NameOfCharityRegulator.EnglandAndWales => Some(RegulatorName.CCEW)
          case NameOfCharityRegulator.NorthernIreland => Some(RegulatorName.CCNI)
          case NameOfCharityRegulator.Scottish        => Some(RegulatorName.OSCR)
          case _                                      => None
        },
        NoReg = organisationDetails.nameOfCharityRegulator match {
          case NameOfCharityRegulator.None => Some(true)
          case _                           => None
        },
        RegNo = organisationDetails.charityRegistrationNumber
      )
    )

  def buildRepayment(claim: models.Claim,
                     currentUser: models.CurrentUser
                    ): Repayment = {

    val paymentList: Option[List[OtherInc]] = claim.claimData.repaymentClaimDetails.OtherIncome.Payment.map(p => Charity(Payer = p.nameOfPayer,
        OIDate = p.dateOfPayment,
        Gross = p.grossPayment,
        Tax = p.taxDeducted))
      .toList match
      case Nil => None
      case list => Some(list)

    val adjGiftAid=  Option.when(claim.claimData.giftAidSmallDonationsSchemeDonationDetails.adjustmentForGiftAidOverClaimed > 0)(
      claim.claimData.giftAidSmallDonationsSchemeDonationDetails.adjustmentForGiftAidOverClaimed
    )
    val adjOtherIncome=
      Option.when(models.OtherIncomeScheduleData.adjustmentForOtherIncomePreviousOverClaimed > 0)(
        models.OtherIncomeScheduleData.adjustmentForOtherIncomePreviousOverClaimed
      )
    val adj: Option[String] = (Some(adjGiftAid) + Some(adjOtherIncome)).toString


    Repayment(
      GAD = None, // TODO - buildRepaymentGAD(claim)
      EarliestGAdate = models.GiftAidScheduleData.earliestDonationDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), // TODO - earliestDonationDate (YYYY-MM-DD)
      OtherInc = paymentList,
      Adjustment = adj
    )
  }

  def buildGiftAidSmallDonationsScheme(
    claim: models.Claim,
    connectedCharitiesData: Option[ConnectedCharitiesScheduleData],
    communityBuildingsData: Option[CommunityBuildingsScheduleData]
  ): Option[GiftAidSmallDonationsScheme] =
    if !claim.claimData.repaymentClaimDetails.claimingUnderGiftAidSmallDonationsScheme then None
    else
      claim.claimData.giftAidSmallDonationsSchemeDonationDetails.map { gasdsDetails =>
        val repaymentDetails = claim.claimData.repaymentClaimDetails

        val connectedCharities: YesNo = repaymentDetails.connectedToAnyOtherCharities.getOrElse(false)

        val charities: Option[List[Charity]] =
          connectedCharitiesData
            .map(_.charities)
            .getOrElse(Seq.empty)
            .map(c => Charity(Name = c.charityName, HMRCref = c.charityReference))
            .toList match
            case Nil  => None
            case list => Some(list)

        val gasdsClaims: Option[List[GiftAidSmallDonationsSchemeClaim]] =
          gasdsDetails.claims
            .map(c =>
              GiftAidSmallDonationsSchemeClaim(
                Year = Some(c.taxYear.toString),
                Amount = Some(c.amountOfDonationsReceived)
              )
            )
            .toList match
            case Nil  => None
            case list => Some(list)

        val commBldgs: Option[YesNo] =
          Some(repaymentDetails.claimingDonationsCollectedInCommunityBuildings.getOrElse(false))

        val buildings: Option[List[Building]] =
          communityBuildingsData
            .map(_.communityBuildings)
            .getOrElse(Seq.empty)
            .map { b =>
              val year1Claim = List(BldgClaim(Year = b.taxYear1.toString, Amount = b.amountYear1))
              val year2Claim = (b.taxYear2, b.amountYear2) match
                case (Some(year), Some(amount)) => List(BldgClaim(Year = year.toString, Amount = amount))
                case _                          => Nil

              Building(
                BldgName = b.buildingName,
                Address = b.firstLineOfAddress,
                Postcode = b.postcode,
                BldgClaim = year1Claim ++ year2Claim
              )
            }
            .toList match
            case Nil  => None
            case list => Some(list)

        val adj: Option[String] =
          Option.when(gasdsDetails.adjustmentForGiftAidOverClaimed > 0)(
            gasdsDetails.adjustmentForGiftAidOverClaimed.toString
          )

        GiftAidSmallDonationsScheme(
          ConnectedCharities = connectedCharities,
          Charity = charities,
          GiftAidSmallDonationsSchemeClaim = gasdsClaims,
          CommBldgs = commBldgs,
          Building = buildings,
          Adj = adj
        )
      }

}
