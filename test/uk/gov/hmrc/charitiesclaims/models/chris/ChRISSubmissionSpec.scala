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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source
import uk.gov.hmrc.charitiesclaims.models.XmlWriter

class ChRISSubmissionSpec extends AnyWordSpec with Matchers {

  "ChRISSubmission" should {
    "be serialised to XML correctly" in {

      val govTalkMessage = GovTalkMessage(
        Header = Header(
          MessageDetails = MessageDetails(
            Class = "HMRC-CHAR-CLM",
            Qualifier = "request",
            Function = "submit",
            CorrelationID = "D240212BD8464107966120F5B312BA63",
            GatewayTimestamp = "2025-11-06T14:58:32.486"
          ),
          SenderDetails = SenderDetails()
        ),
        GovTalkDetails = GovTalkDetails(
          Keys = List(
            Key(Type = "CredentialID", Value = "authorityOrg1"),
            Key(Type = "CHARID", Value = "XR4010"),
            Key(Type = "SessionID", Value = "session-7dd01275-60b7-47fd-b08f-ab834ce94555")
          ),
          ChannelRouting = ChannelRouting(
            Channel = Channel(
              URI = "9998",
              Product = "Charities portal",
              Version = "1.0"
            )
          )
        ),
        Body = Body(
          IRenvelope = IRenvelope(
            IRheader = IRheader(
              Keys = List(
                Key(Type = "CHARID", Value = "XR4010")
              ),
              PeriodEnd = "2012-01-01",
              IRmark = IRmark(Type = "generic", Content = "oCHbGp+XAIi/AYdxWxLNLMmbEno="),
              Sender = "Other"
            ),
            R68 = R68(
              AuthOfficial = AuthOfficial(
                Trustee = Some("Joe Bloggs"),
//                OffName = OffName(
//                  Ttl = "Mr",
//                  Fore = Some("Joe"),
//                  Sur = Some("Bloggs")
//                ),
                OffID = OffID(
                  OverSeas = Some("No"),
                  Postcode = Some("AB12 3YZ")
                ),
                Phone = "07777777777"
              ),
              Declaration = true,
              Claim = Claim(
                OrgName = "CHARITY TC088",
                HMRCref = "XR4010",
                Regulator = Regulator(
                  RegName = Some("CCEW"),
                  NoReg = Some("Yes"),
                  RegNo = Some("1234")
                ),
                Repayment = Repayment(
                  GAD = GAD(
                    Donor = Donor(
                      Ttl = Some("Mr"),
                      Fore = Some("John"),
                      Sur = Some("Smith"),
                      House = Some("100 Champs Elysees, Paris"),
                      Overseas = Some(true),
                      Postcode = Some("AB12 3YZ")
                    ),
                    Date = "2025-01-02",
                    Total = "250.00",
                    EarliestGAdate = "2025-01-01"
                  ),
                  OtherInc = OtherInc(
                    Payer = "John Johns",
                    OIDate = "2025-10-08",
                    Gross = 123456.23,
                    Tax = 123.45
                  ),
                  Adjustment = Some(12345.23),
                  GASDS = GASDS (
                    
                  ),
                ),
                
                OtherInfo = Some("def")
                
              )
            )
          )
        )
      )

      val xml = XmlWriter.write(govTalkMessage)

      xml shouldEqual Source
        .fromInputStream(this.getClass.getResourceAsStream("/test-chris-submission-1.xml"))
        .getLines()
        .mkString("\n")

    }
  }
}
