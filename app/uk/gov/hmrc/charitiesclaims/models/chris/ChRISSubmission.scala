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

package uk.gov.hmrc.charitiesclaims.models.chris

import uk.gov.hmrc.charitiesclaims.models.XmlAttribute
import uk.gov.hmrc.charitiesclaims.models.XmlWriter
import uk.gov.hmrc.charitiesclaims.models.XmlContent
import uk.gov.hmrc.charitiesclaims.models.XmlStringBuilder
import java.util.UUID
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId

final case class GovTalkMessage(
  xmlns: XmlAttribute[String] = XmlAttribute("http://www.govtalk.gov.uk/CM/envelope"),
  EnvelopeVersion: String = "2.0",
  Header: Header,
  GovTalkDetails: GovTalkDetails,
  Body: Body
) derives XmlWriter

final case class Header(
  MessageDetails: MessageDetails,
  SenderDetails: SenderDetails // This is intentionally set to be empty (<SenderDetails></SenderDetails>)
) derives XmlWriter

val gatewayTimestampFormat: DateTimeFormatter =
  DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneId.of("UTC"))

final case class MessageDetails(
  Class: String = "HMRC-CHAR-CLM",
  Qualifier: String = "request",
  Function: String = "submit",
  CorrelationID: String = UUID
    .randomUUID()
    .toString()
    .replace("-", ""), // All messages coming into ChRIS must have this ID for tracing purposes.
  GatewayTimestamp: String =
    gatewayTimestampFormat.format(Instant.now()) // Current system datetime in format "yyyy-MM-ddTHH:mm:ss.SSS"
) derives XmlWriter

final case class SenderDetails() derives XmlWriter

final case class GovTalkDetails(
  Keys: List[Key],
  ChannelRouting: ChannelRouting
) derives XmlWriter

final case class Key(
  Type: XmlAttribute[String],
  Value: XmlContent[String]
) derives XmlWriter

final case class ChannelRouting(
  Channel: Channel
) derives XmlWriter

final case class Channel(
  URI: String = "9998",
  Product: String = "Charities portal",
  Version: String = "1.0"
) derives XmlWriter

final case class Body(
  IRenvelope: IRenvelope
) derives XmlWriter

final case class IRenvelope(
  xmlns: XmlAttribute[String] = XmlAttribute("http://www.govtalk.gov.uk/taxation/charities/r68/2"),
  IRheader: IRheader,
  R68: R68
) derives XmlWriter

final case class IRheader(
  Keys: List[Key],
  PeriodEnd: String,
  IRmark: IRmark,
  Sender: String
) derives XmlWriter

final case class IRmark(
  Type: XmlAttribute[String],
  Content: XmlContent[String]
) derives XmlWriter

final case class R68(
  WelshSubmission: Option[YesNo] =
    None, // If the claim has been completed in Welsh (language selector is "cym), then set to "yes" else omit this element.
  AuthOfficial: AuthOfficial,
  Declaration: YesNo,
  Claim: Claim
) derives XmlWriter

final case class AuthOfficial(
  Trustee: String,
  OffID: OffID,
  Phone: String
) derives XmlWriter

final case class OffID(
  Postcode: String
) derives XmlWriter

final case class Claim(
  OrgName: String,
  HMRCref: String,
  Regulator: Regulator,
  Repayment: Repayment,
  OtherInfo: String
) derives XmlWriter

final case class Regulator(
  RegName: String,
  RegNo: String
) derives XmlWriter

final case class Repayment(
  GAD: GAD,
  EarliestGAdate: String
) derives XmlWriter

final case class GAD(
  Donor: Donor,
  Date: String,
  Total: String
) derives XmlWriter

final case class Donor(
  Ttl: String,
  Fore: String,
  Sur: String,
  House: String,
  Overseas: YesNo
) derives XmlWriter

opaque type YesNo = Boolean

object YesNo {

  given Conversion[Boolean, YesNo] = {
    case true  => true
    case false => false
  }

  given XmlWriter[YesNo] = new XmlWriter[YesNo] {
    def label: String = "YesNo"

    override def isPrimitive: Boolean = true

    def write(name: String, value: YesNo)(using builder: XmlStringBuilder): Unit =
      builder.appendText(value match {
        case true  => "yes"
        case false => "no"
      })
  }
}
