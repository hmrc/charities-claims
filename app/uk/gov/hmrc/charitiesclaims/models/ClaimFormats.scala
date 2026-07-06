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

import play.api.libs.json.*
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

object ClaimFormats {

  private val organisationDetailsEncryptedFields: Seq[String] = Seq(
    "authorisedOfficialTrusteePostcode",
    "authorisedOfficialTrusteeDaytimeTelephoneNumber",
    "authorisedOfficialTrusteeTitle",
    "authorisedOfficialTrusteeFirstName",
    "authorisedOfficialTrusteeLastName"
  )

  private val agentUserOrganisationDetailsEncryptedFields: Seq[String] = Seq(
    "daytimeTelephoneNumber",
    "postcode"
  )

  def encryptedClaimFormat(using crypto: Encrypter & Decrypter): Format[Claim] = {
    given Format[OrganisationDetails] =
      encryptFields(OrganisationDetails.format, organisationDetailsEncryptedFields)

    given Format[AgentUserOrganisationDetails] =
      encryptFields(AgentUserOrganisationDetails.format, agentUserOrganisationDetailsEncryptedFields)

    given Format[ClaimData] = Json.format[ClaimData]

    Json.format[Claim]
  }

  private def encryptedString(using crypto: Encrypter & Decrypter): Format[String] = {
    val writes: Writes[String] = SensitiveWrapper.writes[String].contramap[String](SensitiveWrapper(_))
    val reads: Reads[String]   = SensitiveWrapper.reads[String].map(_.decryptedValue)
    Format(reads, writes)
  }

  /** Wraps `base`, encrypting the given top-level string fields on write and decrypting them on read. Absent (optional)
    * fields are left as-is.
    */
  private def encryptFields[T](base: Format[T], fields: Seq[String])(using crypto: Encrypter & Decrypter): Format[T] = {
    val enc = encryptedString

    val writes: Writes[T] = Writes { value =>
      fields.foldLeft(base.writes(value).as[JsObject]) { (obj, field) =>
        (obj \ field).asOpt[String].fold(obj)(plain => obj + (field -> enc.writes(plain)))
      }
    }

    val reads: Reads[T] = Reads { json =>
      val decrypted = fields.foldLeft[JsResult[JsObject]](JsSuccess(json.as[JsObject])) { (acc, field) =>
        acc.flatMap { obj =>
          (obj \ field).toOption match {
            case Some(encrypted) => enc.reads(encrypted).map(plain => obj + (field -> JsString(plain)))
            case None            => JsSuccess(obj)
          }
        }
      }
      decrypted.flatMap(base.reads)
    }

    Format(reads, writes)
  }
}
