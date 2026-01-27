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

package uk.gov.hmrc.charitiesclaims.validation.gsl

import uk.gov.hmrc.charitiesclaims.util.{BaseSpec, ChRISTestData}
import uk.gov.hmrc.charitiesclaims.xml.XmlWriter
import org.scalatest.Ignore

/** GSL Schematron Validator tests - IGNORED
  *
  * These tests are ignored because the GSL validator integration is blocked. See ADR:
  * docs/decisions/schematron-validation-approach.md
  *
  * The GSLSchematronValidator JAR requires complex runtime configuration (Eclipse JDT, temp directories, config files)
  * that we haven't been able to resolve in the Scala/Play environment.
  */
@Ignore
class GslSchematronValidatorSpec extends BaseSpec {

  val validXml: String = XmlWriter.writeCompact(ChRISTestData.exampleMessage)

  "GslSchematronValidator" - {

    "validate" - {
      "should pass for valid XML" in {
        val result = GslSchematronValidator.validate(validXml)
        result shouldBe Right(())
      }

      "should return errors for invalid XML" in {
        // XML with neither Repayment nor GASDS (violates rule 7028)
        val invalidXml = validXml
          .replace("<Repayment>", "<!-- removed -->")
          .replace("</Repayment>", "<!-- /removed -->")

        val result = GslSchematronValidator.validate(invalidXml)
        result.isLeft shouldBe true
      }
    }
  }
}
