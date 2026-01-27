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

package uk.gov.hmrc.charitiesclaims.validation.xml

import uk.gov.hmrc.charitiesclaims.util.{BaseSpec, ChRISTestData}
import uk.gov.hmrc.charitiesclaims.models.chris.*
import uk.gov.hmrc.charitiesclaims.validation.{ValidationError, ValidationResult}
import uk.gov.hmrc.charitiesclaims.xml.XmlWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class XmlSchematronValidatorSpec extends BaseSpec {

  val validMessage: GovTalkMessage = ChRISTestData.exampleMessage
  val validXml: String             = XmlWriter.writeCompact(validMessage)

  "XmlSchematronValidator" - {

    "validate" - {
      "should pass for a valid XML" in {
        val result = XmlSchematronValidator.validate(validXml)
        result shouldBe Right(())
      }

      "should return parse error for invalid XML" in {
        val result = XmlSchematronValidator.validate("<invalid>")
        result.isLeft shouldBe true
      }
    }

    "validateClaimRule (7028)" - {

      "should pass when Repayment is present" in {
        val doc    = XmlSchematronValidator.parseDocument(validXml)
        val result = XmlSchematronValidator.validateClaimRule(doc)
        result shouldBe ValidationResult.Success
      }

      "should pass when only GASDS is present" in {
        val messageWithGasdsOnly = validMessage.copy(
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              R68 = validMessage.Body.IRenvelope.R68.copy(
                Claim = validMessage.Body.IRenvelope.R68.Claim.copy(
                  Repayment = None,
                  GiftAidSmallDonationsScheme = Some(
                    GiftAidSmallDonationsScheme(
                      ConnectedCharities = false,
                      Adj = None
                    )
                  )
                )
              )
            )
          )
        )
        val xml                  = XmlWriter.writeCompact(messageWithGasdsOnly)
        val doc                  = XmlSchematronValidator.parseDocument(xml)

        val result = XmlSchematronValidator.validateClaimRule(doc)
        result shouldBe ValidationResult.Success
      }

      "should fail when neither Repayment nor GASDS is present" in {
        val messageWithNoClaim = validMessage.copy(
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              R68 = validMessage.Body.IRenvelope.R68.copy(
                Claim = validMessage.Body.IRenvelope.R68.Claim.copy(
                  Repayment = None,
                  GiftAidSmallDonationsScheme = None
                )
              )
            )
          )
        )
        val xml                = XmlWriter.writeCompact(messageWithNoClaim)
        val doc                = XmlSchematronValidator.parseDocument(xml)

        val result = XmlSchematronValidator.validateClaimRule(doc)
        result shouldBe ValidationResult.Error(ValidationError.ClaimRule)
      }
    }

    "validateAuthOfficialRule (7026)" - {

      "should pass when Trustee is present" in {
        val doc    = XmlSchematronValidator.parseDocument(validXml)
        val result = XmlSchematronValidator.validateAuthOfficialRule(doc)
        result shouldBe ValidationResult.Success
      }

      "should pass when OffName is present with first name" in {
        val messageWithOffName = validMessage.copy(
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              R68 = validMessage.Body.IRenvelope.R68.copy(
                AuthOfficial = Some(
                  AuthOfficial(
                    Trustee = None,
                    OffName = Some(OffName(Fore = Some("John"), Sur = Some("Smith"))),
                    Phone = Some("07777777777")
                  )
                )
              )
            )
          )
        )
        val xml                = XmlWriter.writeCompact(messageWithOffName)
        val doc                = XmlSchematronValidator.parseDocument(xml)

        val result = XmlSchematronValidator.validateAuthOfficialRule(doc)
        result shouldBe ValidationResult.Success
      }

      "should pass when AuthOfficial is not present (agent submission)" in {
        val messageWithoutAuthOfficial = validMessage.copy(
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              R68 = validMessage.Body.IRenvelope.R68.copy(
                AuthOfficial = None,
                AgtOrNom = Some(
                  AgtOrNom(
                    OrgName = "Test Agent",
                    RefNo = "A1234",
                    Phone = "07777777777"
                  )
                )
              )
            )
          )
        )
        val xml                        = XmlWriter.writeCompact(messageWithoutAuthOfficial)
        val doc                        = XmlSchematronValidator.parseDocument(xml)

        val result = XmlSchematronValidator.validateAuthOfficialRule(doc)
        result shouldBe ValidationResult.Success
      }

      "should fail when AuthOfficial has neither Trustee nor OffName" in {
        val messageWithEmptyAuthOfficial = validMessage.copy(
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              R68 = validMessage.Body.IRenvelope.R68.copy(
                AuthOfficial = Some(
                  AuthOfficial(
                    Trustee = None,
                    OffName = None,
                    Phone = Some("07777777777")
                  )
                )
              )
            )
          )
        )
        val xml                          = XmlWriter.writeCompact(messageWithEmptyAuthOfficial)
        val doc                          = XmlSchematronValidator.parseDocument(xml)

        val result = XmlSchematronValidator.validateAuthOfficialRule(doc)
        result shouldBe ValidationResult.Error(ValidationError.AuthOfficialRule)
      }
    }

    "validateDateRule (7040)" - {

      "should pass when EarliestGAdate is in the past" in {
        val doc    = XmlSchematronValidator.parseDocument(validXml)
        val result = XmlSchematronValidator.validateDateRule(doc)
        result shouldBe ValidationResult.Success
      }

      "should pass when EarliestGAdate is today" in {
        val today                = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val messageWithTodayDate = validMessage.copy(
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              R68 = validMessage.Body.IRenvelope.R68.copy(
                Claim = validMessage.Body.IRenvelope.R68.Claim.copy(
                  Repayment = validMessage.Body.IRenvelope.R68.Claim.Repayment.map(
                    _.copy(EarliestGAdate = today)
                  )
                )
              )
            )
          )
        )
        val xml                  = XmlWriter.writeCompact(messageWithTodayDate)
        val doc                  = XmlSchematronValidator.parseDocument(xml)

        val result = XmlSchematronValidator.validateDateRule(doc)
        result shouldBe ValidationResult.Success
      }

      "should fail when EarliestGAdate is in the future" in {
        val futureDate            = LocalDate.now().plusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val messageWithFutureDate = validMessage.copy(
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              R68 = validMessage.Body.IRenvelope.R68.copy(
                Claim = validMessage.Body.IRenvelope.R68.Claim.copy(
                  Repayment = validMessage.Body.IRenvelope.R68.Claim.Repayment.map(
                    _.copy(EarliestGAdate = futureDate)
                  )
                )
              )
            )
          )
        )
        val xml                   = XmlWriter.writeCompact(messageWithFutureDate)
        val doc                   = XmlSchematronValidator.parseDocument(xml)

        val result = XmlSchematronValidator.validateDateRule(doc)
        result shouldBe ValidationResult.Error(ValidationError.DateRule)
      }

      "should pass when Repayment is not present" in {
        val messageWithNoRepayment = validMessage.copy(
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              R68 = validMessage.Body.IRenvelope.R68.copy(
                Claim = validMessage.Body.IRenvelope.R68.Claim.copy(
                  Repayment = None,
                  GiftAidSmallDonationsScheme = Some(
                    GiftAidSmallDonationsScheme(ConnectedCharities = false, Adj = None)
                  )
                )
              )
            )
          )
        )
        val xml                    = XmlWriter.writeCompact(messageWithNoRepayment)
        val doc                    = XmlSchematronValidator.parseDocument(xml)

        val result = XmlSchematronValidator.validateDateRule(doc)
        result shouldBe ValidationResult.Success
      }
    }

    "validateKeyRule (5005)" - {

      "should pass when IRheader CHARID matches GovTalkDetails CHARID" in {
        val doc    = XmlSchematronValidator.parseDocument(validXml)
        val result = XmlSchematronValidator.validateKeyRule(doc)
        result shouldBe ValidationResult.Success
      }

      "should fail when CHARID keys don't match" in {
        val messageWithMismatchedKeys = validMessage.copy(
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              IRheader = validMessage.Body.IRenvelope.IRheader.copy(
                Keys = List(Key(Type = "CHARID", Value = "DIFFERENT_ID"))
              )
            )
          )
        )
        val xml                       = XmlWriter.writeCompact(messageWithMismatchedKeys)
        val doc                       = XmlSchematronValidator.parseDocument(xml)

        val result = XmlSchematronValidator.validateKeyRule(doc)
        result shouldBe ValidationResult.Error(ValidationError.KeyRule)
      }

      "should pass when neither has CHARID key" in {
        val messageWithNoCHARID = validMessage.copy(
          GovTalkDetails = validMessage.GovTalkDetails.copy(
            Keys = List(
              Key(Type = "CredentialID", Value = "authorityOrg1"),
              Key(Type = "SessionID", Value = "session-123")
            )
          ),
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              IRheader = validMessage.Body.IRenvelope.IRheader.copy(
                Keys = List(Key(Type = "OTHER", Value = "value"))
              )
            )
          )
        )
        val xml                 = XmlWriter.writeCompact(messageWithNoCHARID)
        val doc                 = XmlSchematronValidator.parseDocument(xml)

        val result = XmlSchematronValidator.validateKeyRule(doc)
        result shouldBe ValidationResult.Success
      }
    }

    "validateAggDonationRule (7038)" - {

      "should pass when no aggregated donations exist" in {
        val doc    = XmlSchematronValidator.parseDocument(validXml)
        val result = XmlSchematronValidator.validateAggDonationRule(doc)
        result shouldBe ValidationResult.Success
      }

      "should pass when aggregated donation total is under 1000" in {
        val messageWithSmallAggDonation = validMessage.copy(
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              R68 = validMessage.Body.IRenvelope.R68.copy(
                Claim = validMessage.Body.IRenvelope.R68.Claim.copy(
                  Repayment = validMessage.Body.IRenvelope.R68.Claim.Repayment.map(
                    _.copy(GAD =
                      Some(
                        List(
                          GAD(
                            AggDonation = Some("10 donors"),
                            Date = "2025-01-02",
                            Total = "500.00"
                          )
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
        val xml                         = XmlWriter.writeCompact(messageWithSmallAggDonation)
        val doc                         = XmlSchematronValidator.parseDocument(xml)

        val result = XmlSchematronValidator.validateAggDonationRule(doc)
        result shouldBe ValidationResult.Success
      }

      "should fail when aggregated donation total exceeds 1000" in {
        val messageWithLargeAggDonation = validMessage.copy(
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              R68 = validMessage.Body.IRenvelope.R68.copy(
                Claim = validMessage.Body.IRenvelope.R68.Claim.copy(
                  Repayment = validMessage.Body.IRenvelope.R68.Claim.Repayment.map(
                    _.copy(GAD =
                      Some(
                        List(
                          GAD(
                            AggDonation = Some("100 donors"),
                            Date = "2025-01-02",
                            Total = "1500.00"
                          )
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
        val xml                         = XmlWriter.writeCompact(messageWithLargeAggDonation)
        val doc                         = XmlSchematronValidator.parseDocument(xml)

        val result = XmlSchematronValidator.validateAggDonationRule(doc)
        result shouldBe ValidationResult.Error(ValidationError.AggDonationRule)
      }

      "should pass when Repayment is not present" in {
        val messageWithNoRepayment = validMessage.copy(
          Body = validMessage.Body.copy(
            IRenvelope = validMessage.Body.IRenvelope.copy(
              R68 = validMessage.Body.IRenvelope.R68.copy(
                Claim = validMessage.Body.IRenvelope.R68.Claim.copy(
                  Repayment = None,
                  GiftAidSmallDonationsScheme = Some(
                    GiftAidSmallDonationsScheme(ConnectedCharities = false, Adj = None)
                  )
                )
              )
            )
          )
        )
        val xml                    = XmlWriter.writeCompact(messageWithNoRepayment)
        val doc                    = XmlSchematronValidator.parseDocument(xml)

        val result = XmlSchematronValidator.validateAggDonationRule(doc)
        result shouldBe ValidationResult.Success
      }
    }

    "should accumulate multiple errors" in {
      val messageWithMultipleErrors = validMessage.copy(
        Body = validMessage.Body.copy(
          IRenvelope = validMessage.Body.IRenvelope.copy(
            IRheader = validMessage.Body.IRenvelope.IRheader.copy(
              Keys = List(Key(Type = "CHARID", Value = "DIFFERENT_ID"))
            ),
            R68 = validMessage.Body.IRenvelope.R68.copy(
              AuthOfficial = Some(
                AuthOfficial(
                  Trustee = None,
                  OffName = None,
                  Phone = Some("07777777777")
                )
              ),
              Claim = validMessage.Body.IRenvelope.R68.Claim.copy(
                Repayment = None,
                GiftAidSmallDonationsScheme = None
              )
            )
          )
        )
      )
      val xml                       = XmlWriter.writeCompact(messageWithMultipleErrors)

      val result = XmlSchematronValidator.validate(xml)
      result.isLeft   shouldBe true
      result.left.value should have size 3 // ClaimRule, AuthOfficialRule, KeyRule
    }
  }

  // Helper to expose parseDocument for testing
  extension (validator: XmlSchematronValidator.type)
    def parseDocument(xml: String): org.w3c.dom.Document =
      val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
      factory.setNamespaceAware(true)
      factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes("UTF-8")))
}
