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

package uk.gov.hmrc.charitiesclaims.validation

import uk.gov.hmrc.charitiesclaims.models.chris.*

import java.time.{LocalDate, ZoneId}
import java.time.format.DateTimeFormatter
import scala.util.Try

object SchematronValidator:

  private val CharityKeyType        = "CHARID"
  private val MaxAggDonationTotal   = BigDecimal(1000)
  private val MaxGADRecords         = 500000
  private val MaxOtherIncRecords    = 2000
  private val MaxConnectedCharities = 1000
  private val MaxCommunityBuildings = 1000
  private val YearLookbackWindow    = 3

  def validate(message: GovTalkMessage): Either[List[ValidationError], Unit] =
    List(
      validateClaimRule(message),
      validateAuthOfficialRule(message),
      validateAgtOrNomRule(message),
      validateDateRule(message),
      validateOIDateRule(message),
      validateKeyRule(message),
      validateAggDonationRule(message),
      validateSponsoredRule(message),
      validateRepaymentRule(message),
      validateTaxRule(message),
      validateAdjustmentRule(message),
      validateAdjRule(message),
      validateConnectedCharitiesRule(message),
      validateCommBldgsRule(message),
      validateGASDSRule(message),
      validateRegulatorRule(message),
      validateYearRule1(message),
      validateYearRule2(message)
    ).flatten match
      case Nil    => Right(())
      case errors => Left(errors)

  private def claim(message: GovTalkMessage): Claim =
    message.Body.IRenvelope.R68.Claim

  private def r68(message: GovTalkMessage): R68 =
    message.Body.IRenvelope.R68

  private def startsWithCHorCF(ref: String): Boolean =
    ref.startsWith("CH") || ref.startsWith("CF")

  private def parseDate(dateStr: String): Option[LocalDate] =
    Try(LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE))
      .orElse(Try(LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
      .toOption

  private def now: LocalDate = LocalDate.now(ZoneId.of("Europe/London"))

  def currentTaxYear: Int =
    if now.isAfter(LocalDate.of(now.getYear, 4, 5)) then now.getYear + 1 else now.getYear

  def validateClaimRule(message: GovTalkMessage): List[ValidationError] =
    val c          = claim(message)
    val errors7028 =
      if c.Repayment.isEmpty && c.GASDS.isEmpty
      then List(ValidationError.ClaimRule7028)
      else Nil

    val errors7029 =
      if !startsWithCHorCF(c.HMRCref) && c.Regulator.isEmpty
      then List(ValidationError.ClaimRule7029)
      else Nil

    errors7028 ++ errors7029

  def validateAuthOfficialRule(message: GovTalkMessage): List[ValidationError] =
    val r = r68(message)
    if r.AuthOfficial.isEmpty && r.AgtOrNom.isEmpty
    then List(ValidationError.AuthOfficialRule)
    else Nil

  def validateAgtOrNomRule(message: GovTalkMessage): List[ValidationError] =
    r68(message).AgtOrNom match
      case Some(agtOrNom) if agtOrNom.OrgName.trim.isEmpty =>
        List(ValidationError.AgtOrNomRule)
      case _                                               => Nil

  def validateDateRule(message: GovTalkMessage): List[ValidationError] =
    parseDate(message.Header.MessageDetails.GatewayTimestamp) match
      case None              => List(ValidationError.GatewayTimestampRule)
      case Some(gatewayDate) =>
        claim(message).Repayment
          .flatMap(_.GAD)
          .getOrElse(Nil)
          .flatMap { gad =>
            parseDate(gad.Date).filter(_.isAfter(gatewayDate)).map(_ => ValidationError.DateRule)
          }

  def validateOIDateRule(message: GovTalkMessage): List[ValidationError] =
    claim(message).Repayment
      .flatMap(_.OtherInc)
      .getOrElse(Nil)
      .flatMap { oi =>
        parseDate(oi.OIDate).filter(_.isAfter(now)).map(_ => ValidationError.OIDateRule)
      }

  def validateKeyRule(message: GovTalkMessage): List[ValidationError] =
    val govTalkKeys  = message.GovTalkDetails.Keys
      .filter(_.Type.attribute == CharityKeyType)
      .map(_.Value.attribute)
    val irHeaderKeys = message.Body.IRenvelope.IRheader.Keys
      .filter(_.Type.attribute == CharityKeyType)
      .map(_.Value.attribute)
    if govTalkKeys != irHeaderKeys
    then List(ValidationError.KeyRule)
    else Nil

  def validateAggDonationRule(message: GovTalkMessage): List[ValidationError] =
    claim(message).Repayment
      .flatMap(_.GAD)
      .getOrElse(Nil)
      .flatMap { gad =>
        gad.AggDonation.flatMap { _ =>
          Try(BigDecimal(gad.Total)).toOption.filter(_ > MaxAggDonationTotal).map(_ => ValidationError.AggDonationRule)
        }
      }

  def validateSponsoredRule(message: GovTalkMessage): List[ValidationError] =
    claim(message).Repayment
      .flatMap(_.GAD)
      .getOrElse(Nil)
      .flatMap { gad =>
        if gad.AggDonation.isDefined && gad.Sponsored.isDefined
        then Some(ValidationError.SponsoredRule)
        else None
      }

  def validateRepaymentRule(message: GovTalkMessage): List[ValidationError] =
    claim(message).Repayment match
      case None            => Nil
      case Some(repayment) =>
        val gadList      = repayment.GAD.getOrElse(Nil)
        val otherIncList = repayment.OtherInc.getOrElse(Nil)

        val err7034 =
          if gadList.nonEmpty && Some(repayment.EarliestGAdate).isEmpty
          then List(ValidationError.RepaymentRule7034)
          else Nil
        val err7035 =
          if gadList.isEmpty && otherIncList.isEmpty
          then List(ValidationError.RepaymentRule7035)
          else Nil
        val err7036 =
          if gadList.size > MaxGADRecords
          then List(ValidationError.RepaymentRule7036)
          else Nil
        val err7037 =
          if otherIncList.size > MaxOtherIncRecords
          then List(ValidationError.RepaymentRule7037)
          else Nil

        err7034 ++ err7035 ++ err7036 ++ err7037

  def validateTaxRule(message: GovTalkMessage): List[ValidationError] =
    claim(message).Repayment
      .flatMap(_.OtherInc)
      .getOrElse(Nil)
      .flatMap { oi =>
        if oi.Tax >= oi.Gross
        then Some(ValidationError.TaxRule)
        else None
      }

  def validateAdjustmentRule(message: GovTalkMessage): List[ValidationError] =
    val c = claim(message)
    c.Repayment match
      case Some(repayment) if repayment.Adjustment.isDefined && c.OtherInfo.isEmpty =>
        List(ValidationError.AdjustmentRule)
      case _                                                                        => Nil

  def validateAdjRule(message: GovTalkMessage): List[ValidationError] =
    val c = claim(message)
    c.GASDS match
      case Some(gasds) if gasds.Adj.isDefined && c.OtherInfo.isEmpty =>
        List(ValidationError.AdjRule)
      case _                                                         => Nil

  def validateConnectedCharitiesRule(message: GovTalkMessage): List[ValidationError] =
    claim(message).GASDS match
      case None        => Nil
      case Some(gasds) =>
        val charities = gasds.Charity.getOrElse(Nil)
        val err7047   =
          if gasds.ConnectedCharities == (true: YesNo) && charities.isEmpty
          then List(ValidationError.ConnectedCharitiesRule7047)
          else Nil
        val err7048   =
          if gasds.ConnectedCharities == (false: YesNo) && charities.nonEmpty
          then List(ValidationError.ConnectedCharitiesRule7048)
          else Nil
        err7047 ++ err7048

  def validateCommBldgsRule(message: GovTalkMessage): List[ValidationError] =
    val c = claim(message)
    c.GASDS match
      case None        => Nil
      case Some(gasds) =>
        val buildings = gasds.Building.getOrElse(Nil)
        val err7052   =
          if gasds.CommBldgs.contains(true: YesNo) && buildings.isEmpty
          then List(ValidationError.CommBldgsRule7052)
          else Nil
        val err7053   =
          if gasds.CommBldgs.contains(false: YesNo) && buildings.nonEmpty
          then List(ValidationError.CommBldgsRule7053)
          else Nil
        val err7060   =
          if startsWithCHorCF(c.HMRCref) && gasds.CommBldgs.contains(true: YesNo)
          then List(ValidationError.CommBldgsRule7060)
          else Nil
        err7052 ++ err7053 ++ err7060

  def validateGASDSRule(message: GovTalkMessage): List[ValidationError] =
    claim(message).GASDS match
      case None        => Nil
      case Some(gasds) =>
        val err7045 =
          if gasds.Charity.exists(_.size > MaxConnectedCharities)
          then List(ValidationError.GASDSRule7045)
          else Nil
        val err7046 =
          if gasds.Building.exists(_.size > MaxCommunityBuildings)
          then List(ValidationError.GASDSRule7046)
          else Nil
        err7045 ++ err7046

  def validateRegulatorRule(message: GovTalkMessage): List[ValidationError] =
    val c = claim(message)
    c.Regulator match
      case None      => Nil
      case Some(reg) =>
        val err7031 =
          if reg.RegName.isDefined && reg.RegNo.isEmpty
          then List(ValidationError.RegulatorRule7031)
          else Nil
        val err7033 =
          if startsWithCHorCF(c.HMRCref) && reg.NoReg.isEmpty
          then List(ValidationError.RegulatorRule7033)
          else Nil
        err7031 ++ err7033

  def validateYearRule1(message: GovTalkMessage): List[ValidationError] =
    val years = claim(message).GASDS
      .flatMap(_.GASDSClaim)
      .getOrElse(Nil)
      .flatMap(_.Year)
    years match
      case Nil => Nil
      case _   =>
        val taxYear  = currentTaxYear
        val yearInts = years.flatMap(y => Try(y.toInt).toOption)

        val err7049 = yearInts.filter(_ > taxYear).map(_ => ValidationError.YearRule1_7049)
        val err7050 = yearInts.filter(_ < taxYear - YearLookbackWindow).map(_ => ValidationError.YearRule1_7050)
        val err7051 =
          if yearInts.size != yearInts.distinct.size
          then List(ValidationError.YearRule1_7051)
          else Nil
        err7049 ++ err7050 ++ err7051

  def validateYearRule2(message: GovTalkMessage): List[ValidationError] = {
    val buildings = claim(message).GASDS
      .flatMap(_.Building)
      .getOrElse(Nil)

    val duplicateYearErrors = findDuplicateYearsInSameBuilding(buildings)
    val yearRangeErrors     = validateYearRange(extractAllYears(buildings))

    duplicateYearErrors ++ yearRangeErrors
  }

  private def extractAllYears(buildings: List[Building]): List[String] =
    buildings.flatMap(_.BldgClaim.map(_.Year))

  private def findDuplicateYearsInSameBuilding(buildings: List[Building]): List[ValidationError] =
    buildings
      .groupBy(b => (b.BldgName, b.Address, b.Postcode))
      .values
      .flatMap { sameBuildings =>
        val years = sameBuildings
          .flatMap(_.BldgClaim)
          .map(_.Year)
          .flatMap(y => Try(y.toInt).toOption)

        Option.when(years.size != years.distinct.size)(ValidationError.YearRule2_7056)
      }
      .toList

  private def validateYearRange(years: List[String]): List[ValidationError] =
    years match {
      case Nil => Nil
      case _   =>
        val taxYear  = currentTaxYear
        val yearInts = years.flatMap(y => Try(y.toInt).toOption)

        val err7054 = yearInts.filter(_ > taxYear).map(_ => ValidationError.YearRule2_7054)
        val err7055 = yearInts.filter(_ < taxYear - YearLookbackWindow).map(_ => ValidationError.YearRule2_7055)

        err7054 ++ err7055
    }
