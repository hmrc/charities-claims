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
import org.encalmo.writer.xml.XmlWriter
import uk.gov.hmrc.charitiesclaims.xml.XmlUtils

class ChRISSubmissionSpec extends AnyWordSpec with Matchers {

  def writeGovTalkMessage(govTalkMessage: GovTalkMessage): String =
    XmlWriter.writeIndented(govTalkMessage)

  "ChRISSubmission" should {
    "be serialised to XML correctly and equals an example 1 valid ChRIS submission XML" in {

      val govTalkMessage = GovTalkMessage(
        Header = Header(
          MessageDetails = MessageDetails(
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
            Channel = Channel()
          )
        ),
        Body = Body(
          IRenvelope = IRenvelope(
            IRheader = IRheader(
              Keys = List(
                Key(Type = "CHARID", Value = "XR4010")
              ),
              PeriodEnd = "2012-01-01",
              IRmark = Some(IRmark(Type = "generic", Content = "oCHbGp+XAIi/AYdxWxLNLMmbEno=")),
              Sender = "Other"
            ),
            R68 = R68(
              AuthOfficial = Some(
                AuthOfficial(
                  Trustee = Some("Joe Bloggs"),
                  OffID = Some(
                    OffID(
                      Postcode = Some("AB12 3YZ")
                    )
                  ),
                  Phone = Some("07777777777")
                )
              ),
              Declaration = true,
              Claim = Claim(
                OrgName = "CHARITY TC088",
                HMRCref = "XR4010",
                Regulator = Some(
                  Regulator(
                    RegName = Some(RegulatorName.CCEW),
                    RegNo = Some("1234")
                  )
                ),
                Repayment = Some(
                  Repayment(
                    GAD = Some(
                      List(
                        GAD(
                          Donor = Some(
                            Donor(
                              Ttl = Some("Mr"),
                              Fore = Some("John"),
                              Sur = Some("Smith"),
                              House = Some("100 Champs Elysees, Paris"),
                              Overseas = Some(true)
                            )
                          ),
                          Date = "2025-01-02",
                          Total = "250.00"
                        )
                      )
                    ),
                    EarliestGAdate = "2025-01-01"
                  )
                ),
                OtherInfo = Some("def")
              )
            )
          )
        )
      )

      val xml = writeGovTalkMessage(govTalkMessage)

      XmlUtils.validateChRISSubmission(xml).isSuccess shouldBe true

      xml shouldBe scala.io.Source
        .fromInputStream(this.getClass.getResourceAsStream("/test-chris-submission-1.xml"))
        .getLines()
        .mkString("\n")

      govTalkMessage.withLiteIRmark shouldBe govTalkMessage
    }

    "be serialised to XML correctly and equals an example 2 validChRIS submission XML" in {

      val govTalkMessage = GovTalkMessage(
        Header = Header(
          MessageDetails = MessageDetails(
            Class = "HMRC-CHAR-CLM",
            Qualifier = "request",
            Function = "submit",
            CorrelationID = "E5E33CCB3A0241BCB38252F2B0B6A7DC",
            GatewayTimestamp = "2025-12-04T12:53:29.124"
          ),
          SenderDetails = SenderDetails()
        ),
        GovTalkDetails = GovTalkDetails(
          Keys = List(
            Key(Type = "CredentialID", Value = "authorityOrg1"),
            Key(Type = "CHARID", Value = "XR4010"),
            Key(Type = "SessionID", Value = "session-7f2f616c-c50d-461e-93c9-57b8d60e04a6")
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
              IRmark = Some(IRmark(Type = "generic", Content = "ecYRGN8K5yfiZSK5RDXoskrwbJE=")),
              Sender = "Other"
            ),
            R68 = R68(
              AuthOfficial = Some(
                AuthOfficial(
                  Trustee = Some("Test Corp"),
                  ClaimNo = Some("CHR-DS-029"),
                  OffID = Some(
                    OffID(
                      Postcode = Some("AB12 3YZ")
                    )
                  ),
                  Phone = Some("07777777777")
                )
              ),
              Declaration = true,
              Claim = Claim(
                OrgName = "CHARITY TC088",
                HMRCref = "XR4010",
                Regulator = Some(
                  Regulator(
                    RegName = Some(RegulatorName.CCEW),
                    RegNo = Some("1137948")
                  )
                ),
                Repayment = Some(
                  Repayment(
                    GAD = Some(
                      List(
                        GAD(
                          Donor = Some(
                            Donor(
                              Ttl = Some("Prof"),
                              Fore = Some("Henry"),
                              Sur = Some("House Martin"),
                              House = Some("152A"),
                              Postcode = Some("M99 2QD")
                            )
                          ),
                          Date = "2025-03-24",
                          Total = "240.00"
                        ),
                        GAD(
                          Donor = Some(
                            Donor(
                              Ttl = Some("Mr"),
                              Fore = Some("John"),
                              Sur = Some("Smith"),
                              House = Some("100 Champs Elysees, Paris"),
                              Overseas = Some(true)
                            )
                          ),
                          Date = "2025-06-24",
                          Total = "250.00"
                        ),
                        GAD(
                          AggDonation = Some("One off Gift Aid donations"),
                          Date = "2025-03-31",
                          Total = "880.00"
                        ),
                        GAD(
                          Donor = Some(
                            Donor(
                              Ttl = Some("Miss"),
                              Fore = Some("B"),
                              Sur = Some("Chaudry"),
                              House = Some("21"),
                              Postcode = Some("L43 4FB")
                            )
                          ),
                          Sponsored = Some(true),
                          Date = "2025-04-26",
                          Total = "4000.00"
                        )
                      )
                    ),
                    OtherInc = Some(
                      List(
                        OtherInc(
                          Payer = "Joe Bloggs",
                          OIDate = "2025-01-01",
                          Gross = BigDecimal("100.00"),
                          Tax = BigDecimal("20.00")
                        ),
                        OtherInc(
                          Payer = "Imogen Smith",
                          OIDate = "2025-02-02",
                          Gross = BigDecimal("157.66"),
                          Tax = BigDecimal("31.53")
                        ),
                        OtherInc(
                          Payer = "Paul Robinson",
                          OIDate = "2025-03-03",
                          Gross = BigDecimal("45222.78"),
                          Tax = BigDecimal("9044.56")
                        )
                      )
                    ),
                    Adjustment = Some(BigDecimal("123.45")),
                    EarliestGAdate = "2025-01-01"
                  )
                ),
                GiftAidSmallDonationsScheme = Some(
                  GiftAidSmallDonationsScheme(
                    ConnectedCharities = true,
                    Charity = Some(
                      List(
                        Charity(Name = "Charity One", HMRCref = "X95442"),
                        Charity(Name = "Charity Two", HMRCref = "X95443"),
                        Charity(Name = "Charity Three", HMRCref = "X95444"),
                        Charity(Name = "Charity Four", HMRCref = "X95445"),
                        Charity(Name = "Charity Five", HMRCref = "X95446")
                      )
                    ),
                    GiftAidSmallDonationsSchemeClaim = Some(
                      List(
                        GiftAidSmallDonationsSchemeClaim(Year = Some("2024"), Amount = Some(BigDecimal("67.09"))),
                        GiftAidSmallDonationsSchemeClaim(Year = Some("2023"), Amount = Some(BigDecimal("460.34")))
                      )
                    ),
                    CommBldgs = Some(true),
                    Building = Some(
                      List(
                        Building(
                          BldgName = "YMCA",
                          Address = "123 New Street",
                          Postcode = "AB12 3CD",
                          BldgClaim = List(
                            BldgClaim(Year = "2024", Amount = BigDecimal("1257.21"))
                          )
                        ),
                        Building(
                          BldgName = "The Vault",
                          Address = "22 Liberty Place",
                          Postcode = "L20 3UD",
                          BldgClaim = List(
                            BldgClaim(Year = "2023", Amount = BigDecimal("1500.00")),
                            BldgClaim(Year = "2024", Amount = BigDecimal("2500.00")),
                            BldgClaim(Year = "2025", Amount = BigDecimal("2000.00"))
                          )
                        ),
                        Building(
                          BldgName = "Bootle Village Hall",
                          Address = "11A Grange Road",
                          Postcode = "L20 1KL",
                          BldgClaim = List(
                            BldgClaim(Year = "2023", Amount = BigDecimal("1750.00"))
                          )
                        )
                      )
                    ),
                    Adj = Some("56.89")
                  )
                ),
                OtherInfo = Some("This is my other info about my adjustments")
              )
            )
          )
        )
      )

      val xml = writeGovTalkMessage(govTalkMessage)

      XmlUtils.validateChRISSubmission(xml).isSuccess shouldBe true

      xml shouldBe scala.io.Source
        .fromInputStream(this.getClass.getResourceAsStream("/test-chris-submission-2.xml"))
        .getLines()
        .mkString("\n")

      govTalkMessage.withFullIRmark shouldBe govTalkMessage
    }

    "be serialised to XML correctly and equals an example 3 validChRIS submission XML" in {
      val govTalkMessage = GovTalkMessage(
        Header = Header(
          MessageDetails = MessageDetails(
            Class = "HMRC-CHAR-CLM",
            Qualifier = "request",
            Function = "submit",
            CorrelationID = "34F0B74139E8453D95324F9AC9866AFA",
            GatewayTimestamp = "2025-12-04T13:00:34.771"
          ),
          SenderDetails = SenderDetails()
        ),
        GovTalkDetails = GovTalkDetails(
          Keys = List(
            Key(Type = "CredentialID", Value = "authorityOrg1"),
            Key(Type = "CHARID", Value = "XR4010"),
            Key(Type = "SessionID", Value = "session-7f2f616c-c50d-461e-93c9-57b8d60e04a6")
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
              IRmark = Some(IRmark(Type = "generic", Content = "zU5YSp/yiJMhQUQ0BHF8Qnsw5jo=")),
              Sender = "Other"
            ),
            R68 = R68(
              AuthOfficial = Some(
                AuthOfficial(
                  OffName = Some(
                    OffName(
                      Ttl = Some("Dr"),
                      Fore = Some("Alice"),
                      Sur = Some("Howard")
                    )
                  ),
                  ClaimNo = Some("CHR-DS-029"),
                  OffID = Some(
                    OffID(
                      Overseas = Some(true)
                    )
                  ),
                  Phone = Some("01234 567890")
                )
              ),
              Declaration = true,
              Claim = Claim(
                OrgName = "CHARITY TC088",
                HMRCref = "XR4010",
                Regulator = Some(
                  Regulator(
                    NoReg = Some(true)
                  )
                ),
                Repayment = Some(
                  Repayment(
                    GAD = Some(
                      List(
                        GAD(
                          Donor = Some(
                            Donor(
                              Ttl = Some("Prof"),
                              Fore = Some("Henry"),
                              Sur = Some("House Martin"),
                              House = Some("152A"),
                              Postcode = Some("M99 2QD")
                            )
                          ),
                          Date = "2025-03-24",
                          Total = "240.00"
                        ),
                        GAD(
                          Donor = Some(
                            Donor(
                              Ttl = Some("Mr"),
                              Fore = Some("John"),
                              Sur = Some("Smith"),
                              House = Some("100 Champs Elysees, Paris"),
                              Overseas = Some(true)
                            )
                          ),
                          Date = "2025-06-24",
                          Total = "250.00"
                        ),
                        GAD(
                          AggDonation = Some("One off Gift Aid donations"),
                          Date = "2025-03-31",
                          Total = "880.00"
                        ),
                        GAD(
                          Donor = Some(
                            Donor(
                              Ttl = Some("Miss"),
                              Fore = Some("B"),
                              Sur = Some("Chaudry"),
                              House = Some("21"),
                              Postcode = Some("L43 4FB")
                            )
                          ),
                          Sponsored = Some(true),
                          Date = "2025-04-26",
                          Total = "4000.00"
                        )
                      )
                    ),
                    EarliestGAdate = "2025-01-01",
                    OtherInc = Some(
                      List(
                        OtherInc(
                          Payer = "Joe Bloggs",
                          OIDate = "2025-01-01",
                          Gross = BigDecimal("100.00"),
                          Tax = BigDecimal("20.00")
                        ),
                        OtherInc(
                          Payer = "Imogen Smith",
                          OIDate = "2025-02-02",
                          Gross = BigDecimal("157.66"),
                          Tax = BigDecimal("31.53")
                        ),
                        OtherInc(
                          Payer = "Paul Robinson",
                          OIDate = "2025-03-03",
                          Gross = BigDecimal("45222.78"),
                          Tax = BigDecimal("9044.56")
                        )
                      )
                    ),
                    Adjustment = Some(BigDecimal("123.45"))
                  )
                ),
                OtherInfo = Some("No adjustments")
              )
            )
          )
        )
      )

      val xml = writeGovTalkMessage(govTalkMessage)

      XmlUtils.validateChRISSubmission(xml).isSuccess shouldBe true

      xml shouldBe scala.io.Source
        .fromInputStream(this.getClass.getResourceAsStream("/test-chris-submission-3.xml"))
        .getLines()
        .mkString("\n")

      govTalkMessage.withLiteIRmark shouldBe govTalkMessage
    }

    "be serialised to XML correctly and equals an example 4 validChRIS submission XML" in {
      val govTalkMessage = GovTalkMessage(
        Header = Header(
          MessageDetails = MessageDetails(
            Class = "HMRC-CHAR-CLM",
            Qualifier = "request",
            Function = "submit",
            CorrelationID = "B483CF7FFA5A4100A25D7A316BF6F0FE",
            GatewayTimestamp = "2025-12-04T13:07:49.171"
          ),
          SenderDetails = SenderDetails()
        ),
        GovTalkDetails = GovTalkDetails(
          Keys = List(
            Key(Type = "CredentialID", Value = "authorityOrg1"),
            Key(Type = "CHARID", Value = "XR4010"),
            Key(Type = "SessionID", Value = "session-9da21f3e-57ff-463d-9296-79a432659b65")
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
              IRmark = Some(IRmark(Type = "generic", Content = "cKLS4eJ4/2jwRaKDI+cIDxBprIs=")),
              Sender = "Other"
            ),
            R68 = R68(
              WelshSubmission = Some(true),
              AuthOfficial = Some(
                AuthOfficial(
                  Trustee = Some("Test Corp"),
                  ClaimNo = Some("CHR-DS-029"),
                  OffID = Some(
                    OffID(
                      Overseas = Some(true)
                    )
                  ),
                  Phone = Some("07777777777")
                )
              ),
              Declaration = true,
              Claim = Claim(
                OrgName = "CHARITY TC088",
                HMRCref = "XR4010",
                Regulator = Some(
                  Regulator(
                    RegName = Some(RegulatorName.CCNI),
                    RegNo = Some("1137948")
                  )
                ),
                Repayment = Some(
                  Repayment(
                    GAD = Some(
                      List(
                        GAD(
                          Donor = Some(
                            Donor(
                              Ttl = Some("Prof"),
                              Fore = Some("Henry"),
                              Sur = Some("House Martin"),
                              House = Some("152A"),
                              Postcode = Some("M99 2QD")
                            )
                          ),
                          Date = "2025-03-24",
                          Total = "240.00"
                        ),
                        GAD(
                          Donor = Some(
                            Donor(
                              Ttl = Some("Mr"),
                              Fore = Some("John"),
                              Sur = Some("Smith"),
                              House = Some("100 Champs Elysees, Paris"),
                              Overseas = Some(true)
                            )
                          ),
                          Date = "2025-06-24",
                          Total = "250.00"
                        ),
                        GAD(
                          AggDonation = Some("One off Gift Aid donations"),
                          Date = "2025-03-31",
                          Total = "880.00"
                        ),
                        GAD(
                          Donor = Some(
                            Donor(
                              Ttl = Some("Miss"),
                              Fore = Some("B"),
                              Sur = Some("Chaudry"),
                              House = Some("21"),
                              Postcode = Some("L43 4FB")
                            )
                          ),
                          Sponsored = Some(true),
                          Date = "2025-04-26",
                          Total = "4000.00"
                        )
                      )
                    ),
                    EarliestGAdate = "2025-01-01"
                  )
                ),
                OtherInfo = Some("Other Info")
              )
            )
          )
        )
      )

      val xml = writeGovTalkMessage(govTalkMessage)

      XmlUtils.validateChRISSubmission(xml).isSuccess shouldBe true

      xml shouldBe scala.io.Source
        .fromInputStream(this.getClass.getResourceAsStream("/test-chris-submission-4.xml"))
        .getLines()
        .mkString("\n")
    }

    "be serialised to XML correctly and equals an example 6 validChRIS submission XML" in {
      val govTalkMessage = GovTalkMessage(
        Header = Header(
          MessageDetails = MessageDetails(
            Class = "HMRC-CHAR-CLM",
            Qualifier = "request",
            Function = "submit",
            CorrelationID = "A7593B6BFE0C4BE880909B531ADA507B",
            GatewayTimestamp = "2026-01-07T00:39:57.138"
          ),
          SenderDetails = SenderDetails()
        ),
        GovTalkDetails = GovTalkDetails(
          Keys = List(
            Key(Type = "CredentialID", Value = "9131010324569480"),
            Key(Type = "CHARID", Value = "XR4010"),
            Key(Type = "SessionID", Value = "session-11d2ed2e-aabe-46f2-908d-00206ce031bd")
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
              IRmark = Some(IRmark(Type = "generic", Content = "vCNb6l2nxXfOL9vMrD+o+sl05aU=")),
              Sender = "Other"
            ),
            R68 = R68(
              WelshSubmission = None,
              AuthOfficial = Some(
                AuthOfficial(
                  Trustee = Some("Mr Test User"),
                  ClaimNo = None,
                  OffID = Some(
                    OffID(
                      Postcode = Some("AB12 3YZ"),
                      Overseas = None
                    )
                  ),
                  Phone = Some("01234567890")
                )
              ),
              Declaration = true,
              Claim = Claim(
                OrgName = "CHARITY TC088",
                HMRCref = "XR4010",
                Regulator = Some(
                  Regulator(
                    RegName = None,
                    NoReg = Some(true),
                    RegNo = None
                  )
                ),
                Repayment = Some(
                  Repayment(
                    GAD = Some(
                      List(
                        GAD(
                          Donor = Some(
                            Donor(
                              Ttl = Some("Mr"),
                              Fore = Some("DEV"),
                              Sur = Some("Test"),
                              House = Some("100 Champs Elysees, Paris"),
                              Postcode = Some("SK8 1BX"),
                              Overseas = None
                            )
                          ),
                          Date = "2021-06-01",
                          Total = "5000.00"
                        )
                      )
                    ),
                    EarliestGAdate = "2021-06-01"
                  )
                ),
                OtherInfo = Some("test test")
              )
            )
          )
        )
      )

      val xml = XmlWriter.writeCompact(govTalkMessage)

      XmlUtils.validateChRISSubmission(xml).isSuccess shouldBe true

      xml shouldBe scala.io.Source
        .fromInputStream(this.getClass.getResourceAsStream("/test-chris-submission-6.xml"))
        .getLines()
        .mkString("\n")
    }
  }
}
