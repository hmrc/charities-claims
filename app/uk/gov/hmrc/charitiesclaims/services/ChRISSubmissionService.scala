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
import uk.gov.hmrc.charitiesclaims.connectors.RdsDatacacheProxyConnector

import scala.concurrent.Future
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[ChRISSubmissionServiceImpl])
trait ChRISSubmissionService {

  def buildChRISSubmission(claim: models.Claim, currentUser: models.CurrentUser)(using
    HeaderCarrier
  ): Future[GovTalkMessage]

}

@Singleton
class ChRISSubmissionServiceImpl @Inject() (
  rdsConnector: RdsDatacacheProxyConnector
)(using ExecutionContext)
    extends ChRISSubmissionService {

  def buildChRISSubmission(
    claim: models.Claim,
    currentUser: models.CurrentUser
  )(using HeaderCarrier): Future[GovTalkMessage] = {

    val orgNameFuture =
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

    orgNameFuture.map(orgName =>
      GovTalkMessage(
        GovTalkDetails = buildGovTalkDetails(currentUser),
        Body = Body(
          IRenvelope = IRenvelope(
            IRheader = buildIRheader(currentUser),
            R68 = buildR68(claim, currentUser, orgName)
          )
        )
      ).withLiteIRmark
    )
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
    orgName: Option[String]
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
      Claim = buildClaim(claim, currentUser)
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

  def buildClaim(claim: models.Claim, currentUser: models.CurrentUser): Claim =
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
      OtherInfo = None // ToDo
    )

  def buildRegulator(claim: models.Claim): Option[Regulator] =
    claim.claimData.organisationDetails.map(organisationDetails =>
      Regulator(
        RegName = organisationDetails.nameOfCharityRegulator match {
          case "EnglandAndWales" => Some(RegulatorName.CCEW)
          case "NorthernIreland" => Some(RegulatorName.CCNI)
          case "Scottish"        => Some(RegulatorName.OSCR)
          case _                 => None
        },
        NoReg = organisationDetails.nameOfCharityRegulator match {
          case "None" => Some(true)
          case _      => None
        },
        RegNo = organisationDetails.charityRegistrationNumber
      )
    )

  def buildRepayment(claim: models.Claim): Option[Repayment] =
    None

}
