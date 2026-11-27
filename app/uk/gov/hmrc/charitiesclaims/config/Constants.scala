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

package uk.gov.hmrc.charitiesclaims.config

object Constants {
  inline val maxStackDepthChRISCheck = 10

  inline val CharityKeyType        = "CHARID"
  inline val MaxAggDonationTotal   = 1000
  inline val MaxGADRecords         = 500000
  inline val MaxOtherIncRecords    = 2000
  inline val MaxConnectedCharities = 1000
  inline val MaxCommunityBuildings = 1000
  inline val YearLookbackWindow    = 3
}
