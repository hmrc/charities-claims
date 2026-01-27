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

package uk.gov.hmrc.charitiesclaims.validation.model

import uk.gov.hmrc.charitiesclaims.models.chris.*
import uk.gov.hmrc.charitiesclaims.validation.*

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Success, Try}

object ModelSchematronValidator extends SchematronValidator[GovTalkMessage] {

  private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  override def validate(message: GovTalkMessage): SchematronResult =
    combineResults(
      List(
        validateClaimRule(message),
        validateAuthOfficialRule(message),
        validateDateRule(message),
        validateKeyRule(message),
        validateAggDonationRule(message)
      )
    )

  /** Rule 7028: Must have Repayment OR GASDS (or both)
    */
  def validateClaimRule(message: GovTalkMessage): ValidationResult =
    val claim        = message.Body.IRenvelope.R68.Claim
    val hasRepayment = claim.Repayment.isDefined
    val hasGasds     = claim.GiftAidSmallDonationsScheme.isDefined

    if hasRepayment || hasGasds then ValidationResult.Success
    else ValidationResult.Error(ValidationError.ClaimRule)

  /** Rule 7026: AuthOfficial must have either OffName or Trustee
    */
  def validateAuthOfficialRule(message: GovTalkMessage): ValidationResult =
    message.Body.IRenvelope.R68.AuthOfficial match
      case None               => ValidationResult.Success
      case Some(authOfficial) =>
        val hasOffName = authOfficial.OffName.exists(n => n.Fore.isDefined || n.Sur.isDefined)
        val hasTrustee = authOfficial.Trustee.isDefined

        if hasOffName || hasTrustee then ValidationResult.Success
        else ValidationResult.Error(ValidationError.AuthOfficialRule)

  /** Rule 7040: EarliestGAdate must not be in the future
    */
  def validateDateRule(message: GovTalkMessage): ValidationResult =
    message.Body.IRenvelope.R68.Claim.Repayment match
      case None            => ValidationResult.Success
      case Some(repayment) =>
        Try(LocalDate.parse(repayment.EarliestGAdate, dateFormatter)) match
          case Success(date) =>
            if !date.isAfter(LocalDate.now()) then ValidationResult.Success
            else ValidationResult.Error(ValidationError.DateRule)
          case Failure(_)    =>
            ValidationResult.Success

  /** Rule 5005: IRheader keys must match GovTalkDetails keys (for CHARID key)
    */
  def validateKeyRule(message: GovTalkMessage): ValidationResult =
    val govTalkKeys  = message.GovTalkDetails.Keys
    val irHeaderKeys = message.Body.IRenvelope.IRheader.Keys

    val govTalkCharId  = govTalkKeys.find(_.Type.attribute == "CHARID").map(_.Value.attribute)
    val irHeaderCharId = irHeaderKeys.find(_.Type.attribute == "CHARID").map(_.Value.attribute)

    (govTalkCharId, irHeaderCharId) match
      case (Some(gtId), Some(irId)) if gtId != irId =>
        ValidationResult.Error(ValidationError.KeyRule)
      case _                                        =>
        ValidationResult.Success

  /** Rule 7038: Aggregated donations total must not exceed 1000
    */
  def validateAggDonationRule(message: GovTalkMessage): ValidationResult =
    message.Body.IRenvelope.R68.Claim.Repayment match
      case None            => ValidationResult.Success
      case Some(repayment) =>
        repayment.GAD match
          case None          => ValidationResult.Success
          case Some(gadList) =>
            val aggDonations = gadList.filter(_.AggDonation.isDefined)
            val exceedsLimit = aggDonations.exists { gad =>
              gad.Total.toDoubleOption.exists(_ > 1000)
            }

            if exceedsLimit then ValidationResult.Error(ValidationError.AggDonationRule)
            else ValidationResult.Success
}
