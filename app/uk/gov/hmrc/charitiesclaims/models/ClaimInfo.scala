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

package uk.gov.hmrc.charitiesclaims.models

import play.api.libs.json.Format
import play.api.libs.json.Json

final case class ClaimInfo(
  claimId: String,
  userId: String,
  claimSubmitted: Boolean,
  lastUpdatedReference: String,
  hmrcCharitiesReference: Option[String] = None,
  nameOfCharity: Option[String] = None,
  claimData: Option[ClaimInfoData] = None
) {

  def flatten: ClaimInfo = this.copy(
    hmrcCharitiesReference = this.claimData.flatMap(_.repaymentClaimDetails.hmrcCharitiesReference),
    nameOfCharity = this.claimData.flatMap(_.repaymentClaimDetails.nameOfCharity),
    claimData = None
  )

}

object ClaimInfo {
  given format: Format[ClaimInfo] = Json.format[ClaimInfo]
}

case class ClaimInfoData(
  repaymentClaimDetails: RepaymentClaimInfoDetails
)

object ClaimInfoData {
  given format: Format[ClaimInfoData] = Json.format[ClaimInfoData]
}

case class RepaymentClaimInfoDetails(
  hmrcCharitiesReference: Option[String] = None,
  nameOfCharity: Option[String] = None
)

object RepaymentClaimInfoDetails {
  given format: Format[RepaymentClaimInfoDetails] = Json.format[RepaymentClaimInfoDetails]
}
