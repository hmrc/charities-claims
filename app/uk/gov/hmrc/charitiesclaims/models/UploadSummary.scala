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

package uk.gov.hmrc.charitiesclaims.models

import play.api.libs.json.{Format, Json}
import play.api.libs.json.Reads
import play.api.libs.json.Writes

opaque type FileUploadReference <: String = String
object FileUploadReference {
  def apply(value: String): FileUploadReference = value
  given Format[FileUploadReference]             =
    Format(
      Reads.of[String].map(FileUploadReference.apply),
      Writes.of[String].contramap(identity)
    )
}

final case class UploadSummary(
  reference: FileUploadReference,
  validationType: ValidationType,
  fileStatus: FileStatus,
  uploadUrl: Option[String] = None
)

object UploadSummary {
  given Format[UploadSummary] = Json.format[UploadSummary]
}

enum FileStatus {

  /** After initiate but before upload */
  case AWAITING_UPLOAD

  /** After upload but before callback */
  case VERIFYING

  /** After callback (failure) - skip validation - */
  case VERIFICATION_FAILED

  /** After callback (success) but before validation complete */
  case VALIDATING

  /** After validation complete (success) */
  case VALIDATED

  /** After validation complete (failure) */
  case VALIDATION_FAILED
}

object FileStatus extends Enumerable.Implicits {
  given Enumerable[FileStatus] =
    Enumerable[FileStatus](str => scala.util.Try(FileStatus.valueOf(str)).toOption)
}

enum ValidationType {
  case GiftAid, OtherIncome, CommunityBuildings, ConnectedCharities
}

object ValidationType extends Enumerable.Implicits {
  given Enumerable[ValidationType] =
    Enumerable[ValidationType](str => scala.util.Try(ValidationType.valueOf(str)).toOption)
}
