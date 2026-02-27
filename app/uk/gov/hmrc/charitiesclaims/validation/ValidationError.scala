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

import play.api.libs.json.{Json, Writes}

final case class ValidationError(
  code: String,
  message: String
)

object ValidationError:
  given Writes[ValidationError] = Json.writes[ValidationError]

  val ClaimRule7028: ValidationError              =
    ValidationError("7028", "You must provide details of the Tax Repayment claim or the GASDS claim or both.")
  val ClaimRule7029: ValidationError              =
    ValidationError(
      "7029",
      "Regulator details must be provided if HMRC Charities Reference does not start with CH or CF and Collecting Agent details are absent."
    )
  val AuthOfficialRule: ValidationError           =
    ValidationError("7026", "You must provide the name of the Authorised Official or the Corporate Trustee or both.")
  val AgtOrNomRule: ValidationError               =
    ValidationError("7027", "If Agent or Nominee details are provided, Organisation Name must be present.")
  val GatewayTimestampRule: ValidationError       =
    ValidationError("7063", "The Gateway Timestamp could not be parsed as a valid date.")
  val DateRule: ValidationError                   =
    ValidationError("7040", "The Gift Aid Donation date must not be later than today.")
  val OIDateRule: ValidationError                 =
    ValidationError("7042", "The Other Income date must not be later than today.")
  val KeyRule: ValidationError                    =
    ValidationError("5005", "Keys in the GovTalkDetails do not match those in the IRheader.")
  val AggDonationRule: ValidationError            =
    ValidationError(
      "7038",
      "If details of Aggregated Donations are submitted then the Gift Aid Donation Total must not exceed 1000."
    )
  val SponsoredRule: ValidationError              =
    ValidationError("7039", "A Gift Aid Donation must not have both Aggregated Donation and Sponsored details.")
  val RepaymentRule7034: ValidationError          =
    ValidationError("7034", "If Gift Aid Donations are present then the Earliest Gift Aid Date must be provided.")
  val RepaymentRule7035: ValidationError          =
    ValidationError("7035", "A Tax Repayment claim must contain Gift Aid Donations or Other Income or both.")
  val RepaymentRule7036: ValidationError          =
    ValidationError("7036", "The number of Gift Aid Donations must not exceed 500000.")
  val RepaymentRule7037: ValidationError          =
    ValidationError("7037", "The number of Other Income items must not exceed 2000.")
  val TaxRule: ValidationError                    =
    ValidationError("7043", "The Tax deducted must be less than the Gross amount for Other Income.")
  val AdjustmentRule: ValidationError             =
    ValidationError("7059", "If a Repayment Adjustment is present then Other Information must be provided.")
  val AdjRule: ValidationError                    =
    ValidationError("7061", "If a GASDS Adjustment is present then Other Information must be provided.")
  val ConnectedCharitiesRule7047: ValidationError =
    ValidationError("7047", "If Connected Charities indicator is yes then Connected Charity details must be provided.")
  val ConnectedCharitiesRule7048: ValidationError =
    ValidationError(
      "7048",
      "If Connected Charities indicator is no then Connected Charity details must not be provided."
    )
  val CommBldgsRule7052: ValidationError          =
    ValidationError("7052", "If Community Buildings indicator is yes then Building details must be provided.")
  val CommBldgsRule7053: ValidationError          =
    ValidationError("7053", "If Community Buildings indicator is no then Building details must not be provided.")
  val CommBldgsRule7060: ValidationError          =
    ValidationError(
      "7060",
      "Community Buildings indicator must not be yes if HMRC Charities Reference starts with CH or CF."
    )
  val GASDSRule7045: ValidationError              =
    ValidationError("7045", "The number of Connected Charities must not exceed 1000.")
  val GASDSRule7046: ValidationError              =
    ValidationError("7046", "The number of Community Buildings must not exceed 1000.")
  val RegulatorRule7031: ValidationError          =
    ValidationError("7031", "If Regulator Name is provided then Regulator Number must also be provided.")
  val RegulatorRule7033: ValidationError          =
    ValidationError("7033", "Regulator details must not be provided if HMRC Charities Reference starts with CH or CF.")
  val YearRule1_7049: ValidationError             =
    ValidationError("7049", "GASDS claim year must not be later than the current tax year.")
  val YearRule1_7050: ValidationError             =
    ValidationError("7050", "GASDS claim year must not be earlier than 3 years before the current tax year.")
  val YearRule1_7051: ValidationError             =
    ValidationError("7051", "GASDS claim years must not contain duplicates.")
  val YearRule2_7054: ValidationError             =
    ValidationError("7054", "Community Building claim year must not be later than the current tax year.")
  val YearRule2_7055: ValidationError             =
    ValidationError(
      "7055",
      "Community Building claim year must not be earlier than 3 years before the current tax year."
    )
  val YearRule2_7056: ValidationError             =
    ValidationError("7056", "Community Building claim years must not contain duplicates.")

final case class SchematronValidationException(errors: List[ValidationError])
    extends Exception(s"Schematron validation failed with ${errors.size} error(s)")
