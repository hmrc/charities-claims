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

import uk.gov.hmrc.charitiesclaims.util.BaseSpec
import uk.gov.hmrc.charitiesclaims.xml.XmlUtils

class IRmarkCalculatorSpec extends BaseSpec {

  "IRmarkCalculator" - {

    "compute hash SHA1 Base64 for a given XML example 1" in {
      val xml = scala.io.Source
        .fromInputStream(getClass.getResourceAsStream("/test-irmark-1.xml"))
        .getLines()
        .mkString("\n")
      IRmarkCalculator.hashSHA1Base64(xml)                           shouldBe "AemngKi/VIqsnS+K8VsXE/Y7SgA="
      IRmarkCalculator.hashSHA1Base64(XmlUtils.canonicalizeXml(xml)) shouldBe "KIYpXR8h95dWC6kB6HDq2c0d5TY="
    }

    "compute hash SHA1 Base64 for a given XML example 2" in {
      val xml = scala.io.Source
        .fromInputStream(getClass.getResourceAsStream("/test-irmark-2.xml"))
        .getLines()
        .mkString("\n")
      IRmarkCalculator.hashSHA1Base64(xml)                           shouldBe "4YeSgqkJKGEgmCEhasYuwsxqfnM="
      IRmarkCalculator.hashSHA1Base64(XmlUtils.canonicalizeXml(xml)) shouldBe "4YeSgqkJKGEgmCEhasYuwsxqfnM="
    }

    "compute hash SHA1 Base64 for a given XML example 3" in {
      val xml = scala.io.Source
        .fromInputStream(getClass.getResourceAsStream("/test-irmark-3.xml"))
        .getLines()
        .mkString("\n")
      IRmarkCalculator.hashSHA1Base64(xml)                           shouldBe "RXvO9aWqS4RSVmxzx9uvPe2FmtI="
      IRmarkCalculator.hashSHA1Base64(XmlUtils.canonicalizeXml(xml)) shouldBe "RXvO9aWqS4RSVmxzx9uvPe2FmtI="
    }

    "compute the IRmark for a given body example 1" in {
      val body = Body(
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
                Phone = "07777777777"
              )
            ),
            Declaration = true,
            Claim = Claim(
              OrgName = "CHARITY TC088",
              HMRCref = "XR4010",
              Regulator = Some(
                Regulator(
                  RegName = Some("CCEW"),
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

      IRmarkCalculator.computeLiteIRmark(body) should be("oCHbGp+XAIi/AYdxWxLNLMmbEno=")
      IRmarkCalculator.computeFullIRmark(body) should be("oCHbGp+XAIi/AYdxWxLNLMmbEno=")
    }

    "compute the IRmark for a given body example 2" in {
      val body = Body(
        IRenvelope = IRenvelope(
          IRheader = IRheader(
            Keys = List(
              Key(Type = "CHARID", Value = "XR4010")
            ),
            PeriodEnd = "2012-01-01",
            IRmark = Some(IRmark(Type = "generic", Content = "HgZyqg72ReQKRBo4sTvTn5HZD5w=")),
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
                Phone = "07777777777"
              )
            ),
            Declaration = true,
            Claim = Claim(
              OrgName = "CHARITY TC088",
              HMRCref = "XR4010",
              Regulator = Some(
                Regulator(
                  RegName = Some("CCEW"),
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
              GASDS = Some(
                GASDS(
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
                  GASDSClaim = Some(
                    List(
                      GASDSClaim(Year = Some("2024"), Amount = Some(BigDecimal("67.09"))),
                      GASDSClaim(Year = Some("2023"), Amount = Some(BigDecimal("460.34")))
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

      IRmarkCalculator.computeLiteIRmark(body) should be("HgZyqg72ReQKRBo4sTvTn5HZD5w=")
      IRmarkCalculator.computeFullIRmark(body) should be("HgZyqg72ReQKRBo4sTvTn5HZD5w=")
    }

    "compute the IRmark for a given body example 3" in {
      val body = Body(
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
                Phone = "01234 567890"
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

      IRmarkCalculator.computeLiteIRmark(body) should be("zU5YSp/yiJMhQUQ0BHF8Qnsw5jo=")
      IRmarkCalculator.computeFullIRmark(body) should be("zU5YSp/yiJMhQUQ0BHF8Qnsw5jo=")
    }
  }
}
