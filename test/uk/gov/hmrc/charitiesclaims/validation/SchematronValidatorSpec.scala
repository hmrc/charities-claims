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
  )(f: Option[GiftAidSmallDonationsScheme] => Option[GiftAidSmallDonationsScheme]): GovTalkMessage =
    withClaim(message)(c => c.copy(GiftAidSmallDonationsScheme = f(c.GiftAidSmallDonationsScheme)))

  private def currentTaxYear: Int =
    val now = LocalDate.now()
    if now.isAfter(LocalDate.of(now.getYear, 4, 5)) then now.getYear else now.getYear - 1

  "SchematronValidator" - {

    "validate" - {

      "should pass for a valid message" in {
        SchematronValidator.validate(validMessage) shouldBe Right(())
      }
    }

    "validateClaimRule (7028, 7029)" - {

      "should pass when Repayment is present" in {
        SchematronValidator.validateClaimRule(validMessage) shouldBe Nil
      }

      "should pass when only GASDS is present" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            Repayment = None,
            GiftAidSmallDonationsScheme = Some(
              GiftAidSmallDonationsScheme(ConnectedCharities = false, Adj = None)
            )
          )
        }
        SchematronValidator.validateClaimRule(msg) shouldBe Nil
      }

      "should fail 7028 when neither Repayment nor GASDS is present" in {
        val msg = withClaim(validMessage)(_.copy(Repayment = None, GiftAidSmallDonationsScheme = None))
        SchematronValidator.validateClaimRule(msg) should contain(ValidationError.ClaimRule7028)
      }

      "should fail 7029 when HMRCref not CH/CF and no Regulator" in {
        val msg = withClaim(validMessage)(_.copy(Regulator = None))
        SchematronValidator.validateClaimRule(msg) should contain(ValidationError.ClaimRule7029)
      }

      "should pass 7029 when HMRCref starts with CH" in {
        val msg = withClaim(validMessage)(_.copy(HMRCref = "CH1234", Regulator = None))
        SchematronValidator.validateClaimRule(msg) should not contain ValidationError.ClaimRule7029
      }

      "should pass 7029 when Regulator is present" in {
        SchematronValidator.validateClaimRule(validMessage) should not contain ValidationError.ClaimRule7029
      }
    }

    "validateAuthOfficialRule (7026)" - {

      "should pass when AuthOfficial is present" in {
        SchematronValidator.validateAuthOfficialRule(validMessage) shouldBe Nil
      }

      "should pass when AgtOrNom is present" in {
        val msg = withR68(validMessage)(r =>
          r.copy(
            AuthOfficial = None,
            AgtOrNom = Some(AgtOrNom(OrgName = "TestOrg", RefNo = "REF1", Phone = "07777777777"))
          )
        )
        SchematronValidator.validateAuthOfficialRule(msg) shouldBe Nil
      }

      "should fail when neither AuthOfficial nor AgtOrNom is present" in {
        val msg = withR68(validMessage)(_.copy(AuthOfficial = None, AgtOrNom = None))
        SchematronValidator.validateAuthOfficialRule(msg) should contain(ValidationError.AuthOfficialRule)
      }
    }

    "validateAgtOrNomRule (7027)" - {

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

    "validateDateRule (7040)" - {

      "should pass when all GAD dates are in the past" in {
        SchematronValidator.validateDateRule(validMessage) shouldBe Nil
      }

      "should pass when GAD date is today" in {
        val todayStr         = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val msg              = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(List(GAD(Date = todayStr, Total = "100.00"))),
              EarliestGAdate = todayStr
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
              EarliestGAdate = todayStr
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

      "should pass when no Repayment" in {
        val msg = withClaim(validMessage)(_.copy(Repayment = None))
        SchematronValidator.validateDateRule(msg) shouldBe Nil
      }

      "should pass when no GAD" in {
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

    "validateOIDateRule (7042)" - {

      "should pass when all OtherInc dates are in the past" in {
        val pastDate = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val msg      = withRepayment(validMessage)(
          _.map(r => r.copy(OtherInc = Some(List(OtherInc(Payer = "Test", OIDate = pastDate, Gross = 100, Tax = 20)))))
        )
        SchematronValidator.validateOIDateRule(msg) shouldBe Nil
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
    }

    "validateKeyRule (5005)" - {

      "should pass when keys match" in {
        SchematronValidator.validateKeyRule(validMessage) shouldBe Nil
      }

      "should fail when CHARID keys do not match" in {
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
    }

    "validateAggDonationRule (7038)" - {

      "should pass when no AggDonation" in {
        SchematronValidator.validateAggDonationRule(validMessage) shouldBe Nil
      }

      "should pass when AggDonation present and total <= 1000" in {
        val msg = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(List(GAD(AggDonation = Some("3"), Date = "2025-01-01", Total = "999.99"))),
              EarliestGAdate = "2025-01-01"
            )
          )
        )
        SchematronValidator.validateAggDonationRule(msg) shouldBe Nil
      }

      "should fail when AggDonation present and total > 1000" in {
        val msg = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(List(GAD(AggDonation = Some("3"), Date = "2025-01-01", Total = "1000.01"))),
              EarliestGAdate = "2025-01-01"
            )
          )
        )
        SchematronValidator.validateAggDonationRule(msg) should contain(ValidationError.AggDonationRule)
      }
    }

    "validateSponsoredRule (7039)" - {

      "should pass when no AggDonation" in {
        SchematronValidator.validateSponsoredRule(validMessage) shouldBe Nil
      }

      "should pass when AggDonation exists without Sponsored" in {
        val msg = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(List(GAD(AggDonation = Some("3"), Date = "2025-01-01", Total = "500.00"))),
              EarliestGAdate = "2025-01-01"
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
              EarliestGAdate = "2025-01-01"
            )
          )
        )
        SchematronValidator.validateSponsoredRule(msg) should contain(ValidationError.SponsoredRule)
      }
    }

    "validateRepaymentRule (7034-7037)" - {

      "should pass when no Repayment" in {
        val msg = withClaim(validMessage)(_.copy(Repayment = None))
        SchematronValidator.validateRepaymentRule(msg) shouldBe Nil
      }

      "should pass for valid repayment with GAD" in {
        SchematronValidator.validateRepaymentRule(validMessage) shouldBe Nil
      }

      "should fail 7034 when GAD exists but EarliestGAdate is empty" in {
        val msg = withRepayment(validMessage)(_ =>
          Some(
            Repayment(
              GAD = Some(List(GAD(Date = "2025-01-01", Total = "100.00"))),
              EarliestGAdate = "  "
            )
          )
        )
        SchematronValidator.validateRepaymentRule(msg) should contain(ValidationError.RepaymentRule7034)
      }

      "should fail 7035 when Repayment has neither GAD nor OtherInc" in {
        val msg =
          withRepayment(validMessage)(_ => Some(Repayment(GAD = None, OtherInc = None, EarliestGAdate = "2025-01-01")))
        SchematronValidator.validateRepaymentRule(msg) should contain(ValidationError.RepaymentRule7035)
      }

      "should fail 7036 when GAD count exceeds 500000" in {
        val bigGadList = List.fill(500001)(GAD(Date = "2025-01-01", Total = "10.00"))
        val msg        =
          withRepayment(validMessage)(_ => Some(Repayment(GAD = Some(bigGadList), EarliestGAdate = "2025-01-01")))
        SchematronValidator.validateRepaymentRule(msg) should contain(ValidationError.RepaymentRule7036)
      }

      "should fail 7037 when OtherInc count exceeds 2000" in {
        val bigOtherIncList = List.fill(2001)(OtherInc(Payer = "Test", OIDate = "2025-01-01", Gross = 100, Tax = 20))
        val msg             = withRepayment(validMessage)(_.map(_.copy(OtherInc = Some(bigOtherIncList))))
        SchematronValidator.validateRepaymentRule(msg) should contain(ValidationError.RepaymentRule7037)
      }
    }

    "validateTaxRule (7043)" - {

      "should pass when Tax < Gross" in {
        val msg = withRepayment(validMessage)(
          _.map(r =>
            r.copy(OtherInc = Some(List(OtherInc(Payer = "Test", OIDate = "2025-01-01", Gross = 100, Tax = 20))))
          )
        )
        SchematronValidator.validateTaxRule(msg) shouldBe Nil
      }

      "should fail when Tax >= Gross" in {
        val msg = withRepayment(validMessage)(
          _.map(r =>
            r.copy(OtherInc = Some(List(OtherInc(Payer = "Test", OIDate = "2025-01-01", Gross = 100, Tax = 100))))
          )
        )
        SchematronValidator.validateTaxRule(msg) should contain(ValidationError.TaxRule)
      }

      "should fail when Tax > Gross" in {
        val msg = withRepayment(validMessage)(
          _.map(r =>
            r.copy(OtherInc = Some(List(OtherInc(Payer = "Test", OIDate = "2025-01-01", Gross = 100, Tax = 150))))
          )
        )
        SchematronValidator.validateTaxRule(msg) should contain(ValidationError.TaxRule)
      }

      "should pass when no OtherInc" in {
        SchematronValidator.validateTaxRule(validMessage) shouldBe Nil
      }
    }

    "validateAdjustmentRule (7059)" - {

      "should pass when no Adjustment" in {
        SchematronValidator.validateAdjustmentRule(validMessage) shouldBe Nil
      }

      "should pass when Adjustment present with OtherInfo" in {
        val msg = withRepayment(validMessage)(_.map(_.copy(Adjustment = Some(BigDecimal(100)))))
        SchematronValidator.validateAdjustmentRule(msg) shouldBe Nil
      }

      "should fail when Adjustment present without OtherInfo" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            Repayment = c.Repayment.map(_.copy(Adjustment = Some(BigDecimal(100)))),
            OtherInfo = None
          )
        }
        SchematronValidator.validateAdjustmentRule(msg) should contain(ValidationError.AdjustmentRule)
      }
    }

    "validateAdjRule (7061)" - {

      "should pass when no GASDS" in {
        SchematronValidator.validateAdjRule(validMessage) shouldBe Nil
      }

      "should pass when GASDS Adj present with OtherInfo" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            GiftAidSmallDonationsScheme =
              Some(GiftAidSmallDonationsScheme(ConnectedCharities = false, Adj = Some("100"))),
            OtherInfo = Some("Adjustment details")
          )
        }
        SchematronValidator.validateAdjRule(msg) shouldBe Nil
      }

      "should fail when GASDS Adj present without OtherInfo" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            GiftAidSmallDonationsScheme =
              Some(GiftAidSmallDonationsScheme(ConnectedCharities = false, Adj = Some("100"))),
            OtherInfo = None
          )
        }
        SchematronValidator.validateAdjRule(msg) should contain(ValidationError.AdjRule)
      }
    }

    "validateConnectedCharitiesRule (7047, 7048)" - {

      "should pass when no GASDS" in {
        SchematronValidator.validateConnectedCharitiesRule(validMessage) shouldBe Nil
      }

      "should pass when indicator=yes and charities present" in {
        val msg = withGasds(validMessage)(_ =>
          Some(
            GiftAidSmallDonationsScheme(
              ConnectedCharities = true,
              Charity = Some(List(Charity(Name = "Test", HMRCref = "XR1234"))),
              Adj = None
            )
          )
        )
        SchematronValidator.validateConnectedCharitiesRule(msg) shouldBe Nil
      }

      "should fail 7047 when indicator=yes but no charities" in {
        val msg = withGasds(validMessage)(_ =>
          Some(GiftAidSmallDonationsScheme(ConnectedCharities = true, Charity = None, Adj = None))
        )
        SchematronValidator.validateConnectedCharitiesRule(msg) should contain(
          ValidationError.ConnectedCharitiesRule7047
        )
      }

      "should pass when indicator=no and no charities" in {
        val msg = withGasds(validMessage)(_ =>
          Some(GiftAidSmallDonationsScheme(ConnectedCharities = false, Charity = None, Adj = None))
        )
        SchematronValidator.validateConnectedCharitiesRule(msg) shouldBe Nil
      }

      "should fail 7048 when indicator=no but charities present" in {
        val msg = withGasds(validMessage)(_ =>
          Some(
            GiftAidSmallDonationsScheme(
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

    "validateCommBldgsRule (7052, 7053, 7060)" - {

      "should pass when no GASDS" in {
        SchematronValidator.validateCommBldgsRule(validMessage) shouldBe Nil
      }

      "should pass when indicator=yes and buildings present" in {
        val msg = withGasds(validMessage)(_ =>
          Some(
            GiftAidSmallDonationsScheme(
              ConnectedCharities = false,
              CommBldgs = Some(true),
              Building = Some(
                List(
                  Building(
                    BldgName = "Hall",
                    Address = "1 Main St",
                    Postcode = "AB1 2CD",
                    BldgClaim = List(BldgClaim(Year = currentTaxYear.toString, Amount = 100))
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
            GiftAidSmallDonationsScheme(
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
            GiftAidSmallDonationsScheme(
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
            GiftAidSmallDonationsScheme(
              ConnectedCharities = false,
              CommBldgs = Some(false),
              Building = Some(
                List(
                  Building(
                    BldgName = "Hall",
                    Address = "1 Main St",
                    Postcode = "AB1 2CD",
                    BldgClaim = List(BldgClaim(Year = currentTaxYear.toString, Amount = 100))
                  )
                )
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateCommBldgsRule(msg) should contain(ValidationError.CommBldgsRule7053)
      }

      "should fail 7060 when HMRCref starts with CH and indicator=yes" in {
        val msg = withClaim(validMessage) { c =>
          c.copy(
            HMRCref = "CH1234",
            GiftAidSmallDonationsScheme = Some(
              GiftAidSmallDonationsScheme(
                ConnectedCharities = false,
                CommBldgs = Some(true),
                Building = Some(
                  List(
                    Building(
                      BldgName = "Hall",
                      Address = "1 Main St",
                      Postcode = "AB1 2CD",
                      BldgClaim = List(BldgClaim(Year = currentTaxYear.toString, Amount = 100))
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

    "validateGASDSRule (7045, 7046)" - {

      "should pass when no GASDS" in {
        SchematronValidator.validateGASDSRule(validMessage) shouldBe Nil
      }

      "should pass when charity count <= 1000" in {
        val charities = List.fill(1000)(Charity(Name = "Test", HMRCref = "XR1234"))
        val msg       = withGasds(validMessage)(_ =>
          Some(
            GiftAidSmallDonationsScheme(ConnectedCharities = true, Charity = Some(charities), Adj = None)
          )
        )
        SchematronValidator.validateGASDSRule(msg) shouldBe Nil
      }

      "should fail 7045 when charity count > 1000" in {
        val charities = List.fill(1001)(Charity(Name = "Test", HMRCref = "XR1234"))
        val msg       = withGasds(validMessage)(_ =>
          Some(
            GiftAidSmallDonationsScheme(ConnectedCharities = true, Charity = Some(charities), Adj = None)
          )
        )
        SchematronValidator.validateGASDSRule(msg) should contain(ValidationError.GASDSRule7045)
      }

      "should fail 7046 when building count > 1000" in {
        val buildings = List.fill(1001)(
          Building(
            BldgName = "Hall",
            Address = "1 Main St",
            Postcode = "AB1 2CD",
            BldgClaim = List(BldgClaim(Year = currentTaxYear.toString, Amount = 100))
          )
        )
        val msg       = withGasds(validMessage)(_ =>
          Some(
            GiftAidSmallDonationsScheme(
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

    "validateRegulatorRule (7031, 7033)" - {

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

      "should pass 7033 when HMRCref starts with CH and Regulator has NoReg" in {
        val msg = withClaim(validMessage)(c =>
          c.copy(
            HMRCref = "CH1234",
            Regulator = Some(Regulator(NoReg = Some(true)))
          )
        )
        SchematronValidator.validateRegulatorRule(msg) should not contain ValidationError.RegulatorRule7033
      }
    }

    "validateYearRule1 (7049-7051)" - {

      "should pass when no GASDS" in {
        SchematronValidator.validateYearRule1(validMessage) shouldBe Nil
      }

      "should pass when all years in valid range" in {
        val taxYear = currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GiftAidSmallDonationsScheme(
              ConnectedCharities = false,
              GiftAidSmallDonationsSchemeClaim = Some(
                List(
                  GiftAidSmallDonationsSchemeClaim(Year = Some(taxYear.toString), Amount = Some(100)),
                  GiftAidSmallDonationsSchemeClaim(Year = Some((taxYear - 1).toString), Amount = Some(200))
                )
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule1(msg) shouldBe Nil
      }

      "should fail 7049 when year > current tax year" in {
        val taxYear = currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GiftAidSmallDonationsScheme(
              ConnectedCharities = false,
              GiftAidSmallDonationsSchemeClaim = Some(
                List(GiftAidSmallDonationsSchemeClaim(Year = Some((taxYear + 1).toString), Amount = Some(100)))
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule1(msg) should contain(ValidationError.YearRule1_7049)
      }

      "should fail 7050 when year < current tax year - 3" in {
        val taxYear = currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GiftAidSmallDonationsScheme(
              ConnectedCharities = false,
              GiftAidSmallDonationsSchemeClaim = Some(
                List(GiftAidSmallDonationsSchemeClaim(Year = Some((taxYear - 4).toString), Amount = Some(100)))
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule1(msg) should contain(ValidationError.YearRule1_7050)
      }

      "should fail 7051 when duplicate years" in {
        val taxYear = currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GiftAidSmallDonationsScheme(
              ConnectedCharities = false,
              GiftAidSmallDonationsSchemeClaim = Some(
                List(
                  GiftAidSmallDonationsSchemeClaim(Year = Some(taxYear.toString), Amount = Some(100)),
                  GiftAidSmallDonationsSchemeClaim(Year = Some(taxYear.toString), Amount = Some(200))
                )
              ),
              Adj = None
            )
          )
        )
        SchematronValidator.validateYearRule1(msg) should contain(ValidationError.YearRule1_7051)
      }
    }

    "validateYearRule2 (7054-7056)" - {

      "should pass when no GASDS" in {
        SchematronValidator.validateYearRule2(validMessage) shouldBe Nil
      }

      "should pass when all building years in valid range" in {
        val taxYear = currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GiftAidSmallDonationsScheme(
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

      "should fail 7054 when year > current tax year" in {
        val taxYear = currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GiftAidSmallDonationsScheme(
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
        val taxYear = currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GiftAidSmallDonationsScheme(
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

      "should fail 7056 when duplicate years across buildings" in {
        val taxYear = currentTaxYear
        val msg     = withGasds(validMessage)(_ =>
          Some(
            GiftAidSmallDonationsScheme(
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
        SchematronValidator.validateYearRule2(msg) should contain(ValidationError.YearRule2_7056)
      }
    }

    "error accumulation" - {

      "should accumulate multiple errors from different rules" in {
        val msg    = withClaim(validMessage) { c =>
          c.copy(
            Repayment = None,
            GiftAidSmallDonationsScheme = None,
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
