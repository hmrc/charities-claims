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

import uk.gov.hmrc.charitiesclaims.models.chris.*
import uk.gov.hmrc.charitiesclaims.util.{BaseSpec, ChRISTestData}
import uk.gov.hmrc.charitiesclaims.xml.{XmlAttribute, XmlContent}

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SchematronValidatorSpec extends BaseSpec {

  val validMessage: GovTalkMessage = ChRISTestData.exampleMessage

  private def withClaim(message: GovTalkMessage)(f: Claim => Claim): GovTalkMessage =
    message.copy(Body =
      message.Body.copy(IRenvelope =
        message.Body.IRenvelope
          .copy(R68 = message.Body.IRenvelope.R68.copy(Claim = f(message.Body.IRenvelope.R68.Claim)))
      )
    )

  private def withR68(message: GovTalkMessage)(f: R68 => R68): GovTalkMessage =
    message.copy(Body =
      message.Body.copy(IRenvelope = message.Body.IRenvelope.copy(R68 = f(message.Body.IRenvelope.R68)))
    )

  private def withRepayment(message: GovTalkMessage)(f: Option[Repayment] => Option[Repayment]): GovTalkMessage =
    withClaim(message)(c => c.copy(Repayment = f(c.Repayment)))

  private def withGasds(
    message: GovTalkMessage
  )(f: Option[GASDS] => Option[GASDS]): GovTalkMessage =
    withClaim(message)(c => c.copy(GASDS = f(c.GASDS)))

  "SchematronValidator" - {

    "All rules - validate" - {

      "should pass all rules for a valid, default message" in {
        SchematronValidator.validate(validMessage) shouldBe Right(())
      }
    }

    "Rule 1. AdjRule (7061) - validate" - {

      "should pass when GASDS Adj and OtherInfo both are present" in {
        SchematronValidator.validateAdjRule(validMessage) shouldBe Nil
      }

      "should pass when GASDS Adj and OtherInfo both aren't present" in {
        SchematronValidator.validateAdjRule(validMessage) shouldBe Nil
      }

      "should pass when GASDS Adj isn't present but OtherInfo is present" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            GASDS = None,
            OtherInfo = Some("Adjustment details")
          )
        }
        SchematronValidator.validateAdjRule(msg) shouldBe Nil
      }

      "should fail when GASDS Adj is present but OtherInfo isn't present" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            GASDS = Some(GASDS(ConnectedCharities = false, Adj = Some("100"))),
            OtherInfo = None
          )
        }
        SchematronValidator.validateAdjRule(msg) should contain(ValidationError.AdjRule)
      }
    }

    "Rule 2. AdjustmentRule (7059) - validate" - {

      "should pass when Repayment Adjustment and OtherInfo both are present" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            Repayment = c.Repayment.map(_.copy(Adjustment = Some(BigDecimal(100)))),
            OtherInfo = Some("Some Other Info")
          )
        }
        SchematronValidator.validateAdjustmentRule(msg) shouldBe Nil
      }

      "should pass when Repayment Adjustment and OtherInfo both aren't present" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            Repayment = None,
            OtherInfo = None
          )
        }
        SchematronValidator.validateAdjustmentRule(msg) shouldBe Nil
      }

      "should pass when Repayment Adjustment isn't present and OtherInfo is present" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            Repayment = None,
            OtherInfo = Some("Some Other Info")
          )
        }
        SchematronValidator.validateAdjustmentRule(msg) shouldBe Nil
      }

      "should fail when Repayment Adjustment is present and OtherInfo isn't present" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            Repayment = c.Repayment.map(_.copy(Adjustment = Some(BigDecimal(100)))),
            OtherInfo = None
          )
        }
        SchematronValidator.validateAdjustmentRule(msg) should contain(ValidationError.AdjustmentRule)
      }
    }

    // Rule 3. AgentNoRule (7025) is not required hence not implemented

    "Rule 4. AggDonationRule (7038) - validate" - {

      "should pass when AggDonation present and total < 1000" in {
        val msg = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(List(GAD(AggDonation = Some("AggDonation"), Date = "2025-01-01", Total = "999.99"))),
              EarliestGAdate = Some("2025-01-01")
            )
          )
        )
        SchematronValidator.validateAggDonationRule(msg) shouldBe Nil
      }

      "should pass when AggDonation present and total = 1000 in  GAD from List " in {
        val msg = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(
                List(
                  GAD(AggDonation = Some("AggDonation1"), Date = "2025-01-01", Total = "1000.00"),
                  GAD(AggDonation = Some("AggDonation2"), Date = "2025-01-01", Total = "1000.00")
                )
              ),
              EarliestGAdate = Some("2025-01-01")
            )
          )
        )
        SchematronValidator.validateAggDonationRule(msg) shouldBe Nil
      }

      "should pass when AggDonation not present and total > 1000" in {
        val msg = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(List(GAD(Date = "2025-01-01", Total = "1001.00"))),
              EarliestGAdate = Some("2025-01-01")
            )
          )
        )
        SchematronValidator.validateAggDonationRule(msg) shouldBe Nil
      }

      "should fail when AggDonation present and total > 1000 in 1st GAD from List" in {
        val msg = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(
                List(
                  GAD(AggDonation = Some("3"), Date = "2025-01-01", Total = "1000.01"),
                  GAD(AggDonation = Some("4"), Date = "2025-01-01", Total = "999.99")
                )
              ),
              EarliestGAdate = Some("2025-01-01")
            )
          )
        )
        SchematronValidator.validateAggDonationRule(msg) should contain(ValidationError.AggDonationRule)
      }

      "should fail when AggDonation present and total > 1000 in 2nd GAD from List" in {
        val msg = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(
                List(
                  GAD(AggDonation = Some("3"), Date = "2025-01-01", Total = "999.99"),
                  GAD(AggDonation = Some("4"), Date = "2025-01-01", Total = "1000.01")
                )
              ),
              EarliestGAdate = Some("2025-01-01")
            )
          )
        )
        SchematronValidator.validateAggDonationRule(msg) should contain(ValidationError.AggDonationRule)
      }
    }

    "Rule 5. AgtOrNomRule (7027) - validate" - {

      "should pass when AgtOrNom is not present" in {
        SchematronValidator.validateAgtOrNomRule(validMessage) shouldBe Nil
      }

      "should pass when AgtOrNom has OrgName" in {
        val msg = withR68(validMessage)(r =>
          r.copy(AgtOrNom = Some(AgtOrNom(OrgName = "TestOrg", RefNo = "REF1", Phone = "07777777777")))
        )
        SchematronValidator.validateAgtOrNomRule(msg) shouldBe Nil
      }

      "should fail when AgtOrNom has empty OrgName" in {
        val msg = withR68(validMessage)(r =>
          r.copy(AgtOrNom = Some(AgtOrNom(OrgName = "  ", RefNo = "REF1", Phone = "07777777777")))
        )
        SchematronValidator.validateAgtOrNomRule(msg) should contain(ValidationError.AgtOrNomRule)
      }
    }

    "Rule 6. AuthOfficialRule (7026)" - {

      "should pass when AuthOfficial is not present" in {
        SchematronValidator.validateAuthOfficialRule(validMessage) shouldBe Nil
      }

      "should pass when AuthOfficial has both OffName and Trustee" in {
        val msg = withR68(validMessage)(r =>
          r.copy(
            AuthOfficial = Some(
              AuthOfficial(
                Trustee = Some("Joe Bloggs"),
                OffName = Some(
                  OffName(
                    Ttl = Some("SomeTtl"),
                    Fore = Some("SomeFore"),
                    Sur = Some("SomeSur")
                  )
                )
              )
            )
          )
        )
        SchematronValidator.validateAuthOfficialRule(msg) shouldBe Nil
      }

      "should pass when AuthOfficial has OffName" in {
        val msg = withR68(validMessage)(r =>
          r.copy(
            AuthOfficial = Some(
              AuthOfficial(
                OffName = Some(
                  OffName(
                    Ttl = Some("SomeTtl"),
                    Fore = Some("SomeFore"),
                    Sur = Some("SomeSur")
                  )
                )
              )
            )
          )
        )
        SchematronValidator.validateAuthOfficialRule(msg) shouldBe Nil
      }

      "should pass when AuthOfficial has Trustee" in {
        val msg = withR68(validMessage)(r =>
          r.copy(
            AuthOfficial = Some(
              AuthOfficial(
                Trustee = Some("Joe Bloggs")
              )
            )
          )
        )
        SchematronValidator.validateAuthOfficialRule(msg) shouldBe Nil
      }

      "should fail when AuthOfficial has neither OffName nor Trustee" in {
        val msg = withR68(validMessage)(_.copy(AuthOfficial = Some(AuthOfficial())))
        SchematronValidator.validateAuthOfficialRule(msg) should contain(ValidationError.AuthOfficialRule)
      }
    }

    "Rule 7. ClaimRule (7028, 7029)" - {

      "should pass when Repayment and GASDS both are present" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            GASDS = Some(
              GASDS(ConnectedCharities = false, Adj = None)
            )
          )
        }
        SchematronValidator.validateClaimRule(msg) shouldBe Nil
      }

      "should pass when only Repayment is present" in {
        SchematronValidator.validateClaimRule(validMessage) shouldBe Nil
      }

      "should pass when only GASDS is present" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            Repayment = None,
            GASDS = Some(
              GASDS(ConnectedCharities = false, Adj = None)
            )
          )
        }
        SchematronValidator.validateClaimRule(msg) shouldBe Nil
      }

      "should fail 7028 when neither Repayment nor GASDS is present" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            Repayment = None
          )
        }
        SchematronValidator.validateClaimRule(msg) should contain(ValidationError.ClaimRule7028)
      }

      "should pass 7029 when HMRCref starts with CH" in {
        val msg = withClaim(validMessage)(_.copy(HMRCref = "CH1234", Regulator = None))
        SchematronValidator.validateClaimRule(msg) should not contain ValidationError.ClaimRule7029
      }

      "should pass 7029 when HMRCref starts with CF" in {
        val msg = withClaim(validMessage)(_.copy(HMRCref = "CF1234", Regulator = None))
        SchematronValidator.validateClaimRule(msg) should not contain ValidationError.ClaimRule7029
      }

      "should pass 7029 when HMRCref not start with CH or CF and Regulator is present" in {
        SchematronValidator.validateClaimRule(validMessage) should not contain ValidationError.ClaimRule7029
      }

      "should fail 7029 when HMRCref not start with CH or CF and Regulator is not present" in {
        val msg = withClaim(validMessage)(_.copy(Regulator = None))
        SchematronValidator.validateClaimRule(msg) should contain(ValidationError.ClaimRule7029)
      }
    }

    "Rule 8. CommBldgsRule (7052, 7053, 7060)" - {

      "should pass when no GASDS" in {
        SchematronValidator.validateCommBldgsRule(validMessage) shouldBe Nil
      }

      "should pass when indicator=yes and buildings present" in {
        val msg = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              CommBldgs = Some(true),
              Building = Some(
                List(
                  Building(
                    BldgName = "Hall",
                    Address = "1 Main St",
                    Postcode = "AB1 2CD",
                    BldgClaim = List(BldgClaim(Year = SchematronValidator.currentTaxYear.toString, Amount = 100))
                  )
                )
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateCommBldgsRule(msg) shouldBe Nil
      }

      "should fail 7052 when indicator=yes but no buildings" in {
        val msg = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              CommBldgs = Some(true),
              Building = None,
              Adj = None
            )
          )
        )
        SchematronValidator.validateCommBldgsRule(msg) should contain(ValidationError.CommBldgsRule7052)
      }

      "should pass when indicator=no and no buildings" in {
        val msg = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              CommBldgs = Some(false),
              Building = None,
              Adj = None
            )
          )
        )
        SchematronValidator.validateCommBldgsRule(msg) shouldBe Nil
      }

      "should fail 7053 when indicator=no but buildings present" in {
        val msg = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              CommBldgs = Some(false),
              Building = Some(
                List(
                  Building(
                    BldgName = "Hall",
                    Address = "1 Main St",
                    Postcode = "AB1 2CD",
                    BldgClaim = List(BldgClaim(Year = SchematronValidator.currentTaxYear.toString, Amount = 100))
                  )
                )
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateCommBldgsRule(msg) should contain(ValidationError.CommBldgsRule7053)
      }

      "should pass 7060 when HMRCref starts with CH and indicator=no" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            HMRCref = "CH1234",
            GASDS = Some(
              GASDS(
                ConnectedCharities = false,
                CommBldgs = Some(false),
                Building = None,
                Adj = None
              )
            )
          )
        }
        SchematronValidator.validateCommBldgsRule(msg) should not contain ValidationError.CommBldgsRule7060
      }

      "should pass 7060 when HMRCref starts with CF and indicator=no" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            HMRCref = "CF1234",
            GASDS = Some(
              GASDS(
                ConnectedCharities = false,
                CommBldgs = Some(false),
                Building = None,
                Adj = None
              )
            )
          )
        }
        SchematronValidator.validateCommBldgsRule(msg) should not contain ValidationError.CommBldgsRule7060
      }

      "should pass 7060 when HMRCref not starts with CH or CF and indicator=yes" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            GASDS = Some(
              GASDS(
                ConnectedCharities = false,
                CommBldgs = Some(true),
                Building = Some(
                  List(
                    Building(
                      BldgName = "Hall",
                      Address = "1 Main St",
                      Postcode = "AB1 2CD",
                      BldgClaim = List(BldgClaim(Year = SchematronValidator.currentTaxYear.toString, Amount = 100))
                    )
                  )
                ),
                Adj = None
              )
            )
          )
        }
        SchematronValidator.validateCommBldgsRule(msg) should not contain ValidationError.CommBldgsRule7060
      }

      "should fail 7060 when HMRCref starts with CH and indicator=yes" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            HMRCref = "CH1234",
            GASDS = Some(
              GASDS(
                ConnectedCharities = false,
                CommBldgs = Some(true),
                Building = Some(
                  List(
                    Building(
                      BldgName = "Hall",
                      Address = "1 Main St",
                      Postcode = "AB1 2CD",
                      BldgClaim = List(BldgClaim(Year = SchematronValidator.currentTaxYear.toString, Amount = 100))
                    )
                  )
                ),
                Adj = None
              )
            )
          )
        }
        SchematronValidator.validateCommBldgsRule(msg) should contain(ValidationError.CommBldgsRule7060)
      }

      "should fail 7060 when HMRCref starts with CF and indicator=yes" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            HMRCref = "CF1234",
            GASDS = Some(
              GASDS(
                ConnectedCharities = false,
                CommBldgs = Some(true),
                Building = Some(
                  List(
                    Building(
                      BldgName = "Hall",
                      Address = "1 Main St",
                      Postcode = "AB1 2CD",
                      BldgClaim = List(BldgClaim(Year = SchematronValidator.currentTaxYear.toString, Amount = 100))
                    )
                  )
                ),
                Adj = None
              )
            )
          )
        }
        SchematronValidator.validateCommBldgsRule(msg) should contain(ValidationError.CommBldgsRule7060)
      }
    }

    "Rule 9. ConnectedCharitiesRule (7047, 7048)" - {

      "should pass when no GASDS" in {
        SchematronValidator.validateConnectedCharitiesRule(validMessage) shouldBe Nil
      }

      "should pass when indicator=yes and charities present" in {
        val msg = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = true,
              Charity = Some(List(Charity(Name = "Test", HMRCref = "XR1234"))),
              Adj = None
            )
          )
        )
        SchematronValidator.validateConnectedCharitiesRule(msg) shouldBe Nil
      }

      "should fail 7047 when indicator=yes but no charities" in {
        val msg = withGasds(validMessage)(_ => Some(GASDS(ConnectedCharities = true, Charity = None, Adj = None)))
        SchematronValidator.validateConnectedCharitiesRule(msg) should contain(
          ValidationError.ConnectedCharitiesRule7047
        )
      }

      "should pass when indicator=no and no charities" in {
        val msg = withGasds(validMessage)(_ => Some(GASDS(ConnectedCharities = false, Charity = None, Adj = None)))
        SchematronValidator.validateConnectedCharitiesRule(msg) shouldBe Nil
      }

      "should fail 7048 when indicator=no but charities present" in {
        val msg = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              Charity = Some(List(Charity(Name = "Test", HMRCref = "XR1234"))),
              Adj = None
            )
          )
        )
        SchematronValidator.validateConnectedCharitiesRule(msg) should contain(
          ValidationError.ConnectedCharitiesRule7048
        )
      }
    }

    "Rule 10. DateRule (7040)" - {

      "should pass when all GAD dates are in the past" in {
        SchematronValidator.validateDateRule(validMessage) shouldBe Nil
      }

      "should pass when GAD date is today" in {
        val todayStr         = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val msg              = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(List(GAD(Date = todayStr, Total = "100.00"))),
              EarliestGAdate = Some(todayStr)
            )
          )
        )
        val msgWithTimestamp = msg.copy(Header =
          msg.Header.copy(MessageDetails =
            msg.Header.MessageDetails.copy(GatewayTimestamp = s"${todayStr}T12:00:00.000")
          )
        )
        SchematronValidator.validateDateRule(msgWithTimestamp) shouldBe Nil
      }

      "should fail when any GAD date is in the future" in {
        val futureDate       = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val todayStr         = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val msg              = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(List(GAD(Date = futureDate, Total = "100.00"))),
              EarliestGAdate = Some(todayStr)
            )
          )
        )
        val msgWithTimestamp = msg.copy(Header =
          msg.Header.copy(MessageDetails =
            msg.Header.MessageDetails.copy(GatewayTimestamp = s"${todayStr}T12:00:00.000")
          )
        )
        SchematronValidator.validateDateRule(msgWithTimestamp) should contain(ValidationError.DateRule)
      }

      "should pass when no Repayment and hence no GAD and DATE" in {
        val msg = withClaim(validMessage)(_.copy(Repayment = None))
        SchematronValidator.validateDateRule(msg) shouldBe Nil
      }

      "should pass when no GAD and hence no DATE" in {
        val msg = withRepayment(validMessage)(_.map(_.copy(GAD = None)))
        SchematronValidator.validateDateRule(msg) shouldBe Nil
      }

      "should fail when GatewayTimestamp is unparseable" in {
        val msg = validMessage.copy(Header =
          validMessage.Header.copy(MessageDetails =
            validMessage.Header.MessageDetails.copy(GatewayTimestamp = "not-a-date")
          )
        )
        SchematronValidator.validateDateRule(msg) should contain(ValidationError.GatewayTimestampRule)
      }
    }

    "Rule 11. GASDSRule (7045, 7046)" - {

      "should pass when no GASDS" in {
        SchematronValidator.validateGASDSRule(validMessage) shouldBe Nil
      }

      "should pass when charity count <= 1000" in {
        val charities = List.fill(1000)(Charity(Name = "Test", HMRCref = "XR1234"))
        val msg       = withGasds(validMessage)(_ =>
          Some(
            GASDS(ConnectedCharities = true, Charity = Some(charities), Adj = None)
          )
        )
        SchematronValidator.validateGASDSRule(msg) shouldBe Nil
      }

      "should fail 7045 when charity count > 1000" in {
        val charities = List.fill(1001)(Charity(Name = "Test", HMRCref = "XR1234"))
        val msg       = withGasds(validMessage)(_ =>
          Some(
            GASDS(ConnectedCharities = true, Charity = Some(charities), Adj = None)
          )
        )
        SchematronValidator.validateGASDSRule(msg) should contain(ValidationError.GASDSRule7045)
      }

      "should pass when buildings count <= 1000" in {
        val buildings = List.fill(1000)(
          Building(
            BldgName = "Hall",
            Address = "1 Main St",
            Postcode = "AB1 2CD",
            BldgClaim = List(BldgClaim(Year = SchematronValidator.currentTaxYear.toString, Amount = 100))
          )
        )
        val msg       = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              CommBldgs = Some(true),
              Building = Some(buildings),
              Adj = None
            )
          )
        )
        SchematronValidator.validateGASDSRule(msg) shouldBe Nil
      }

      "should pass when buildings is None" in {
        val msg = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              CommBldgs = Some(true),
              Building = None,
              Adj = None
            )
          )
        )
        SchematronValidator.validateGASDSRule(msg) shouldBe Nil
      }

      "should fail 7046 when building count > 1000" in {
        val buildings = List.fill(1001)(
          Building(
            BldgName = "Hall",
            Address = "1 Main St",
            Postcode = "AB1 2CD",
            BldgClaim = List(BldgClaim(Year = SchematronValidator.currentTaxYear.toString, Amount = 100))
          )
        )
        val msg       = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              CommBldgs = Some(true),
              Building = Some(buildings),
              Adj = None
            )
          )
        )
        SchematronValidator.validateGASDSRule(msg) should contain(ValidationError.GASDSRule7046)
      }
    }

    "Rule 12. HMRCrefRule (7030)" - {

      "should pass when keys match" in {
        SchematronValidator.validateHMRCrefRule(validMessage) shouldBe Nil
      }

      "should fail when CHARID key do not match" in {
        val msg = validMessage.copy(
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              IRheader = validMessage.Body.IRenvelope.IRheader.copy(
                Keys = List(
                  Key(Type = "CredentialID", Value = "authorityOrg1"),
                  Key(Type = "CHARID", Value = "DIFFERENT"),
                  Key(Type = "SessionID", Value = "session-123")
                )
              )
            )
          )
        )
        SchematronValidator.validateHMRCrefRule(msg) should contain(ValidationError.HMRCrefRule)
      }

      "should fail when CHARID key do not exist" in {
        val msg = validMessage.copy(
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              IRheader = validMessage.Body.IRenvelope.IRheader.copy(
                Keys = List(
                  Key(Type = "CredentialID", Value = "authorityOrg1"),
                  Key(Type = "SessionID", Value = "session-123")
                )
              )
            )
          )
        )
        SchematronValidator.validateHMRCrefRule(msg) should contain(ValidationError.HMRCrefRule)
      }

    }

    "Rule 14. KeyRule (5005)" - {

      "should pass when keys match" in {
        SchematronValidator.validateKeyRule(validMessage) shouldBe Nil
      }

      "should fail when CHARID key do not match" in {
        val msg = validMessage.copy(GovTalkDetails =
          validMessage.GovTalkDetails.copy(Keys =
            List(
              Key(Type = "CredentialID", Value = "authorityOrg1"),
              Key(Type = "CHARID", Value = "DIFFERENT"),
              Key(Type = "SessionID", Value = "session-123")
            )
          )
        )
        SchematronValidator.validateKeyRule(msg) should contain(ValidationError.KeyRule)
      }

      "should fail when CHARID key do not exist" in {
        val msg = validMessage.copy(GovTalkDetails =
          validMessage.GovTalkDetails.copy(Keys =
            List(
              Key(Type = "CredentialID", Value = "authorityOrg1"),
              Key(Type = "SessionID", Value = "session-123")
            )
          )
        )
        SchematronValidator.validateKeyRule(msg) should contain(ValidationError.KeyRule)
      }
    }

    "Rule 15. OIDateRule (7042)" - {

      "should pass when all OtherInc dates are in the past" in {
        val todayStr         = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val pastDate         = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val msg              = withRepayment(validMessage)(
          _.map(r => r.copy(OtherInc = Some(List(OtherInc(Payer = "Test", OIDate = pastDate, Gross = 100, Tax = 20)))))
        )
        val msgWithTimestamp = msg.copy(Header =
          msg.Header.copy(MessageDetails =
            msg.Header.MessageDetails.copy(GatewayTimestamp = s"${todayStr}T12:00:00.000")
          )
        )
        SchematronValidator.validateOIDateRule(msgWithTimestamp) shouldBe Nil
      }

      "should fail when any OtherInc date is in the future" in {
        val futureDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val msg        = withRepayment(validMessage)(
          _.map(r =>
            r.copy(OtherInc = Some(List(OtherInc(Payer = "Test", OIDate = futureDate, Gross = 100, Tax = 20))))
          )
        )
        SchematronValidator.validateOIDateRule(msg) should contain(ValidationError.OIDateRule)
      }

      "should pass when no OtherInc" in {
        SchematronValidator.validateOIDateRule(validMessage) shouldBe Nil
      }

      "should fail when GatewayTimestamp is unparseable" in {

        val msg = validMessage.copy(Header =
          validMessage.Header.copy(MessageDetails =
            validMessage.Header.MessageDetails.copy(GatewayTimestamp = "not-a-date")
          )
        )

        SchematronValidator.validateOIDateRule(msg) should contain(ValidationError.GatewayTimestampRule)
      }
    }

    "Rule 18. RegulatorRule (7031, 7033)" - {

      "should pass when no Regulator" in {
        val msg = withClaim(validMessage)(_.copy(HMRCref = "CH1234", Regulator = None))
        SchematronValidator.validateRegulatorRule(msg) shouldBe Nil
      }

      "should pass when RegName and RegNo both present" in {
        SchematronValidator.validateRegulatorRule(validMessage) shouldBe Nil
      }

      "should fail 7031 when RegName present but no RegNo" in {
        val msg = withClaim(validMessage)(c =>
          c.copy(Regulator = Some(Regulator(RegName = Some(RegulatorName.CCEW), RegNo = None)))
        )
        SchematronValidator.validateRegulatorRule(msg) should contain(ValidationError.RegulatorRule7031)
      }

      "should fail 7033 when HMRCref starts with CH and Regulator present (not NoReg)" in {
        val msg = withClaim(validMessage)(c =>
          c.copy(
            HMRCref = "CH1234",
            Regulator = Some(Regulator(RegName = Some(RegulatorName.CCEW), RegNo = Some("1234")))
          )
        )
        SchematronValidator.validateRegulatorRule(msg) should contain(ValidationError.RegulatorRule7033)
      }

      "should fail 7033 when HMRCref starts with CF and Regulator has no NoReg" in {
        val msg = withClaim(validMessage)(c =>
          c.copy(
            HMRCref = "CF1234",
            Regulator = Some(Regulator(RegName = Some(RegulatorName.CCEW), RegNo = Some("1234")))
          )
        )
        SchematronValidator.validateRegulatorRule(msg) should contain(ValidationError.RegulatorRule7033)
      }

      "should pass 7033 when HMRCref starts with CH and Regulator has NoReg" in {
        val msg = withClaim(validMessage)(c =>
          c.copy(
            HMRCref = "CH1234",
            Regulator = Some(Regulator(NoReg = Some(true)))
          )
        )
        SchematronValidator.validateRegulatorRule(msg) should not contain ValidationError.RegulatorRule7033
      }

      "should pass 7033 when HMRCref starts with CF and Regulator has NoReg" in {
        val msg = withClaim(validMessage)(c =>
          c.copy(
            HMRCref = "CF1234",
            Regulator = Some(Regulator(NoReg = Some(true)))
          )
        )
        SchematronValidator.validateRegulatorRule(msg) should not contain ValidationError.RegulatorRule7033
      }

      "should pass when HMRCref doesn't starts with CH or CF and Regulator has no NoReg" in {
        val msg = withClaim(validMessage)(c =>
          c.copy(
            Regulator = Some(Regulator(RegName = Some(RegulatorName.CCEW), RegNo = Some("1234")))
          )
        )
        SchematronValidator.validateRegulatorRule(msg) shouldBe Nil
      }
    }

    "Rule 19. RepaymentRule (7034, 7035, 7036, 7037)" - {

      "should pass when no Repayment" in {
        val msg = withClaim(validMessage)(_.copy(Repayment = None))
        SchematronValidator.validateRepaymentRule(msg) shouldBe Nil
      }

      "should pass for valid repayment with GAD" in {
        SchematronValidator.validateRepaymentRule(validMessage) shouldBe Nil
      }

      "should pass when GAD don't exist and EarliestGAdate is empty" in {
        val msg = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = None,
              OtherInc = Some(List(OtherInc(Payer = "Test", OIDate = "2025-01-01", Gross = 100, Tax = 20))),
              EarliestGAdate = Some("  ")
            )
          )
        )
        SchematronValidator.validateRepaymentRule(msg) shouldBe Nil
      }

      "should fail 7034 when GAD exists but EarliestGAdate is empty" in {
        val msg = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(List(GAD(Date = "2025-01-01", Total = "100.00"))),
              EarliestGAdate = None
            )
          )
        )
        SchematronValidator.validateRepaymentRule(msg) should contain(ValidationError.RepaymentRule7034)
      }

      "should pass when Repayment has GAD but not OtherInc" in {
        val msg =
          withRepayment(validMessage)(_ =>
            Some(
              Repayment(
                GAD = Some(List(GAD(Date = "2025-01-01", Total = "100.00"))),
                OtherInc = None,
                EarliestGAdate = Some("2025-01-01")
              )
            )
          )
        SchematronValidator.validateRepaymentRule(msg) shouldBe Nil
      }

      "should pass when Repayment has OtherInc but not GAD" in {
        val msg =
          withRepayment(validMessage)(_ =>
            Some(
              Repayment(
                GAD = None,
                OtherInc = Some(List(OtherInc(Payer = "Test", OIDate = "2025-01-01", Gross = 100, Tax = 20))),
                EarliestGAdate = Some("2025-01-01")
              )
            )
          )
        SchematronValidator.validateRepaymentRule(msg) shouldBe Nil
      }

      "should fail 7035 when Repayment has neither GAD nor OtherInc" in {
        val msg =
          withRepayment(validMessage)(_ => Some(Repayment(GAD = None, OtherInc = None, EarliestGAdate = None)))
        SchematronValidator.validateRepaymentRule(msg) should contain(ValidationError.RepaymentRule7035)
      }

      "should pass when GAD count less than or equal to 500000" in {
        val bigGadList = List.fill(500000)(GAD(Date = "2025-01-01", Total = "10.00"))
        val msg        =
          withRepayment(validMessage)(_ => Some(Repayment(GAD = Some(bigGadList), EarliestGAdate = Some("2025-01-01"))))
        SchematronValidator.validateRepaymentRule(msg) shouldBe Nil
      }

      "should fail 7036 when GAD count exceeds 500000" in {
        val bigGadList = List.fill(500001)(GAD(Date = "2025-01-01", Total = "10.00"))
        val msg        =
          withRepayment(validMessage)(_ => Some(Repayment(GAD = Some(bigGadList), EarliestGAdate = Some("2025-01-01"))))
        SchematronValidator.validateRepaymentRule(msg) should contain(ValidationError.RepaymentRule7036)
      }

      "should pass when OtherInc count less than or equal to 2000" in {
        val bigOtherIncList = List.fill(2000)(OtherInc(Payer = "Test", OIDate = "2025-01-01", Gross = 100, Tax = 20))
        val msg             = withRepayment(validMessage)(_.map(_.copy(OtherInc = Some(bigOtherIncList))))
        SchematronValidator.validateRepaymentRule(msg) shouldBe Nil
      }

      "should fail 7037 when OtherInc count exceeds 2000" in {
        val bigOtherIncList = List.fill(2001)(OtherInc(Payer = "Test", OIDate = "2025-01-01", Gross = 100, Tax = 20))
        val msg             = withRepayment(validMessage)(_.map(_.copy(OtherInc = Some(bigOtherIncList))))
        SchematronValidator.validateRepaymentRule(msg) should contain(ValidationError.RepaymentRule7037)
      }
    }

    "Rule 20. SponsoredRule (7039)" - {

      "should pass when neither AggDonation nor Sponsored exists" in {
        SchematronValidator.validateSponsoredRule(validMessage) shouldBe Nil
      }

      "should pass when AggDonation exists without Sponsored" in {
        val msg = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(List(GAD(AggDonation = Some("3"), Date = "2025-01-01", Total = "500.00"))),
              EarliestGAdate = Some("2025-01-01")
            )
          )
        )
        SchematronValidator.validateSponsoredRule(msg) shouldBe Nil
      }

      "should pass when Sponsored exists without AggDonation" in {
        val msg = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(List(GAD(Sponsored = Some(true), Date = "2025-01-01", Total = "500.00"))),
              EarliestGAdate = Some("2025-01-01")
            )
          )
        )
        SchematronValidator.validateSponsoredRule(msg) shouldBe Nil
      }

      "should fail when AggDonation and Sponsored both present" in {
        val msg = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD =
                Some(List(GAD(AggDonation = Some("3"), Sponsored = Some(true), Date = "2025-01-01", Total = "500.00"))),
              EarliestGAdate = Some("2025-01-01")
            )
          )
        )
        SchematronValidator.validateSponsoredRule(msg) should contain(ValidationError.SponsoredRule)
      }
    }

    "Rule 21. TaxRule (7043)" - {

      "should pass when no OtherInc" in {
        SchematronValidator.validateTaxRule(validMessage) shouldBe Nil
      }

      "should pass when Tax < Gross" in {
        val msg = withRepayment(validMessage)(
          _.map(r =>
            r.copy(OtherInc =
              Some(
                List(
                  OtherInc(Payer = "Test", OIDate = "2025-01-01", Gross = 100, Tax = 99),
                  OtherInc(Payer = "Test", OIDate = "2025-01-01", Gross = 100, Tax = 98)
                )
              )
            )
          )
        )
        SchematronValidator.validateTaxRule(msg) shouldBe Nil
      }

      "should fail when Tax == Gross" in {
        val msg = withRepayment(validMessage)(
          _.map(r =>
            r.copy(OtherInc =
              Some(
                List(
                  OtherInc(Payer = "Test", OIDate = "2025-01-01", Gross = 100, Tax = 99),
                  OtherInc(Payer = "Test", OIDate = "2025-01-01", Gross = 100, Tax = 100)
                )
              )
            )
          )
        )
        SchematronValidator.validateTaxRule(msg) should contain(ValidationError.TaxRule)
      }

      "should fail when Tax > Gross" in {
        val msg = withRepayment(validMessage)(
          _.map(r =>
            r.copy(OtherInc =
              Some(
                List(
                  OtherInc(Payer = "Test", OIDate = "2025-01-01", Gross = 100, Tax = 99),
                  OtherInc(Payer = "Test", OIDate = "2025-01-01", Gross = 100, Tax = 101)
                )
              )
            )
          )
        )
        SchematronValidator.validateTaxRule(msg) should contain(ValidationError.TaxRule)
      }
    }

    "Rule 22. YearRule1 (7049, 7050, 7051)" - {

      "should pass when no GASDS" in {
        SchematronValidator.validateYearRule1(validMessage) shouldBe Nil
      }

      "should pass when all years in valid range" in {
        val taxYear = SchematronValidator.currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              GASDSClaim = Some(
                List(
                  GASDSClaim(Year = Some(taxYear.toString), Amount = Some(100)),
                  GASDSClaim(Year = Some((taxYear - 2).toString), Amount = Some(300)),
                  GASDSClaim(Year = Some((taxYear - 3).toString), Amount = Some(400))
                )
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule1(msg) shouldBe Nil
      }

      "should fail 7049 when year > current tax year" in {
        val taxYear = SchematronValidator.currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              GASDSClaim = Some(
                List(GASDSClaim(Year = Some((taxYear + 1).toString), Amount = Some(100)))
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule1(msg) should contain(ValidationError.YearRule1_7049)
      }

      "should fail 7050 when year < current tax year - 3" in {
        val taxYear = SchematronValidator.currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              GASDSClaim = Some(
                List(GASDSClaim(Year = Some((taxYear - 4).toString), Amount = Some(100)))
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule1(msg) should contain(ValidationError.YearRule1_7050)
      }

      "should fail 7051 when duplicate years" in {
        val taxYear = SchematronValidator.currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              GASDSClaim = Some(
                List(
                  GASDSClaim(Year = Some(taxYear.toString), Amount = Some(100)),
                  GASDSClaim(Year = Some(taxYear.toString), Amount = Some(200))
                )
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule1(msg) should contain(ValidationError.YearRule1_7051)
      }
    }

    "Rule 23. YearRule2 (7054, 7055, 7056)" - {

      "should pass when no GASDS" in {
        SchematronValidator.validateYearRule2(validMessage) shouldBe Nil
      }

      "should pass when all building years in valid range" in {
        val taxYear = SchematronValidator.currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              CommBldgs = Some(true),
              Building = Some(
                List(
                  Building(
                    BldgName = "Hall",
                    Address = "1 Main St",
                    Postcode = "AB1 2CD",
                    BldgClaim = List(
                      BldgClaim(Year = taxYear.toString, Amount = 100),
                      BldgClaim(Year = (taxYear - 1).toString, Amount = 200)
                    )
                  )
                )
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule2(msg) shouldBe Nil
      }

      "should pass when duplicate years across buildings with different BldgName" in {
        val taxYear = SchematronValidator.currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              CommBldgs = Some(true),
              Building = Some(
                List(
                  Building(
                    BldgName = "Hall1",
                    Address = "1 Main St",
                    Postcode = "AB1 2CD",
                    BldgClaim = List(BldgClaim(Year = taxYear.toString, Amount = 100))
                  ),
                  Building(
                    BldgName = "Hall2",
                    Address = "1 Main St",
                    Postcode = "AB1 2CD",
                    BldgClaim = List(BldgClaim(Year = taxYear.toString, Amount = 200))
                  )
                )
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule2(msg) shouldBe Nil
      }

      "should pass when duplicate years across buildings with different Address" in {
        val taxYear = SchematronValidator.currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              CommBldgs = Some(true),
              Building = Some(
                List(
                  Building(
                    BldgName = "Hall1",
                    Address = "1 Main St",
                    Postcode = "AB1 2CD",
                    BldgClaim = List(BldgClaim(Year = taxYear.toString, Amount = 100))
                  ),
                  Building(
                    BldgName = "Hall1",
                    Address = "2 Main St",
                    Postcode = "AB1 2CD",
                    BldgClaim = List(BldgClaim(Year = taxYear.toString, Amount = 200))
                  )
                )
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule2(msg) shouldBe Nil
      }

      "should pass when duplicate years across buildings with different Postcode" in {
        val taxYear = SchematronValidator.currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              CommBldgs = Some(true),
              Building = Some(
                List(
                  Building(
                    BldgName = "Hall1",
                    Address = "1 Main St",
                    Postcode = "AB1 2CD",
                    BldgClaim = List(BldgClaim(Year = taxYear.toString, Amount = 100))
                  ),
                  Building(
                    BldgName = "Hall1",
                    Address = "1 Main St",
                    Postcode = "EF1 2GH",
                    BldgClaim = List(BldgClaim(Year = taxYear.toString, Amount = 200))
                  )
                )
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule2(msg) shouldBe Nil
      }

      "should fail 7054 when year > current tax year" in {
        val taxYear = SchematronValidator.currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              CommBldgs = Some(true),
              Building = Some(
                List(
                  Building(
                    BldgName = "Hall",
                    Address = "1 Main St",
                    Postcode = "AB1 2CD",
                    BldgClaim = List(BldgClaim(Year = (taxYear + 1).toString, Amount = 100))
                  )
                )
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule2(msg) should contain(ValidationError.YearRule2_7054)
      }

      "should fail 7055 when year < current tax year - 3" in {
        val taxYear = SchematronValidator.currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              CommBldgs = Some(true),
              Building = Some(
                List(
                  Building(
                    BldgName = "Hall",
                    Address = "1 Main St",
                    Postcode = "AB1 2CD",
                    BldgClaim = List(BldgClaim(Year = (taxYear - 4).toString, Amount = 100))
                  )
                )
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule2(msg) should contain(ValidationError.YearRule2_7055)
      }

      "should fail 7056 when duplicate years across buildings with same BldgName, Address and Postcode" in {
        val taxYear = SchematronValidator.currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GASDS(
              ConnectedCharities = false,
              CommBldgs = Some(true),
              Building = Some(
                List(
                  Building(
                    BldgName = "Hall1",
                    Address = "1 Main St",
                    Postcode = "AB1 2CD",
                    BldgClaim = List(BldgClaim(Year = taxYear.toString, Amount = 100))
                  ),
                  Building(
                    BldgName = "Hall1",
                    Address = "1 Main St",
                    Postcode = "AB1 2CD",
                    BldgClaim = List(BldgClaim(Year = taxYear.toString, Amount = 200))
                  )
                )
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule2(msg) should contain(ValidationError.YearRule2_7056)
      }
    }

    "error accumulation" - {

      "should accumulate multiple errors from different rules" in {
        val msg    = withClaim(validMessage) { c =>
          c.copy(
            Repayment = None,
            GASDS = None,
            Regulator = None
          )
        }
        val result = SchematronValidator.validate(msg)
        result.isLeft shouldBe true
        val errors = result.left.value
        errors should contain(ValidationError.ClaimRule7028)
        errors should contain(ValidationError.ClaimRule7029)
      }
    }
  }
}
