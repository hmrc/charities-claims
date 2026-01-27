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

final case class ValidationError(
  code: String,
  message: String
)

object ValidationError {

  val ClaimRule: ValidationError = ValidationError(
    code = "7028",
    message = "You must provide details of the Tax Repayment claim or the GASDS claim or both."
  )

  val AuthOfficialRule: ValidationError = ValidationError(
    code = "7026",
    message = "You must provide either Authorised Official Name or Trustee details."
  )

  val DateRule: ValidationError = ValidationError(
    code = "7040",
    message = "The earliest Gift Aid donation date cannot be in the future."
  )

  val KeyRule: ValidationError = ValidationError(
    code = "5005",
    message = "The IRheader keys must match the GovTalkDetails keys."
  )

  val AggDonationRule: ValidationError = ValidationError(
    code = "7038",
    message = "The aggregated donations total must not exceed 1000."
  )
}
