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

import com.google.inject.ImplementedBy
import play.api.Logger
import uk.gov.hmrc.charitiesclaims.connectors.{ClaimsValidationConnector, FormpProxyConnector}
import uk.gov.hmrc.charitiesclaims.models.*
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import cats.syntax.traverse.*
import cats.instances.future.*
import cats.instances.option.*

@ImplementedBy(classOf[UnregulatedDonationsServiceImpl])
trait UnregulatedDonationsService {

  def recordUnregulatedDonation(
    claim: Claim,
    currentUser: CurrentUser
  )(using HeaderCarrier): Future[Unit]
}

object UnregulatedDonationsService {

  def isUnregulatedDonation(claim: Claim): Boolean =
    claim.claimData.organisationDetails.exists { orgDetails =>
      orgDetails.nameOfCharityRegulator == NameOfCharityRegulator.None &&
      orgDetails.reasonNotRegisteredWithRegulator.exists(isQualifyingReason)
    }

  def isQualifyingReason(reason: ReasonNotRegisteredWithRegulator): Boolean =
    reason match {
      case ReasonNotRegisteredWithRegulator.LowIncome => true
      case ReasonNotRegisteredWithRegulator.Excepted  => true
      case _                                          => false
    }

  def calculateDonationsTotal(
    giftAidData: Option[GiftAidScheduleData]
  ): BigDecimal =
    giftAidData.map(_.totalDonations).getOrElse(BigDecimal(0))

  // Organisation users: use their CHARID enrolment identifier
  // Agent users: use the hmrcCharitiesReference from the claim data
  def resolveCharityReference(claim: Claim, currentUser: CurrentUser): Option[String] =
    if currentUser.isAgent then claim.claimData.repaymentClaimDetails.hmrcCharitiesReference
    else Some(currentUser.enrolmentIdentifierValue)

  // convert a String claimId (UUID) to an Int for the FormP oracle database
  def claimIdToInt(claimId: String): Int =
    claimId.hashCode & Int.MaxValue
}

@Singleton
class UnregulatedDonationsServiceImpl @Inject() (
  formpProxyConnector: FormpProxyConnector,
  claimsValidationConnector: ClaimsValidationConnector
)(using ExecutionContext)
    extends UnregulatedDonationsService {

  private val logger = Logger(this.getClass)

  import UnregulatedDonationsService.*

  def recordUnregulatedDonation(
    claim: Claim,
    currentUser: CurrentUser
  )(using HeaderCarrier): Future[Unit] =

    if !isUnregulatedDonation(claim) then
      logger.info(s"Claim: ${claim.claimId} does not qualify as an unregulated donation")
      Future.successful(())
    else
      resolveCharityReference(claim, currentUser) match {
        case None =>
          throw Exception("Cannot record unregulated donation: no charity reference available")

        case Some(charityReference) =>
          for
            giftAidData <- getGiftAidUploadData(claim)

            amount = calculateDonationsTotal(giftAidData)

            // save the unregulated donation record via FormP Proxy
            _  =
              logger.info(
                s"Recording unregulated donation for Claim: ${claim.claimId}, Charity Reference: $charityReference, Amount: $amount"
              )
            _ <- formpProxyConnector.saveUnregulatedDonation(
                   charityReference,
                   claimIdToInt(claim.claimId),
                   amount
                 )
          yield ()
      }

  private def getGiftAidUploadData(
    claim: Claim
  )(using HeaderCarrier): Future[Option[GiftAidScheduleData]] =
    claim.claimData.giftAidScheduleFileUploadReference.flatTraverse { ref =>
      claimsValidationConnector
        .getUploadResult(claim.claimId, ref)
        .map(_.collect { case GetUploadResultValidatedGiftAid(_, data) => data })
    }
}
