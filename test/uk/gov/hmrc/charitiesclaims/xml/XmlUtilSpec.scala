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

package uk.gov.hmrc.charitiesclaims.xml

import java.net.URL
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class XmlUtilSpec extends AnyFreeSpec with Matchers {

  "XmlUtils" - {

    "should parse a valid XML document" in {
      val xml = XmlUtils.parseDocument("<xml><hello>world</hello></xml>")
      xml.isSuccess shouldBe true
    }

    "should fail to parse an invalid XML document" in {
      val xml = XmlUtils.parseDocument("<xml><hello>world</hella></xml>")
      xml.isFailure shouldBe true
    }

    "should load available XSD schemas" in {
      val schemaSoourceMap = List(
        ("/xsd/xmldsig-core-schema.xsd", new URL("http://www.w3.org/2000/09/xmldsig#")),
        ("/xsd/r68-v0-3.xsd", new URL("http://www.govtalk.gov.uk/taxation/charities/r68/1")),
        ("/xsd/envelope-v2-0-HMRC.xsd", new URL("http://www.govtalk.gov.uk/CM/envelope"))
      )
      val schema           = XmlUtils.loadSchema(schemaSoourceMap)
      schema.isSuccess shouldBe true
    }

    "should fail to load an incomplete XSD schema" in {
      val schemaSoourceMap = List(
        ("/xsd/r68-v0-3.xsd", new URL("http://www.govtalk.gov.uk/taxation/charities/r68/1")),
        ("/xsd/envelope-v2-0-HMRC.xsd", new URL("http://www.govtalk.gov.uk/CM/envelope"))
      )
      val schema           = XmlUtils.loadSchema(schemaSoourceMap)
      schema.isFailure shouldBe true
    }

    "should validate an example valid ChRIS submission XML document" in {
      val xml = scala.io.Source
        .fromInputStream(this.getClass.getResourceAsStream("/test-chris-submission-1.xml"))
        .getLines()
        .mkString("\n")

      XmlUtils.parseDocument(xml).isSuccess           shouldBe true
      XmlUtils.validateChRISSubmission(xml).isSuccess shouldBe true
    }

    "should fail to validate an example invalid ChRIS submission XML document" in {
      val xml = scala.io.Source
        .fromInputStream(this.getClass.getResourceAsStream("/test-chris-submission-invalid.xml"))
        .getLines()
        .mkString("\n")

      XmlUtils.parseDocument(xml).isSuccess           shouldBe true
      XmlUtils.validateChRISSubmission(xml).isSuccess shouldBe false
    }

    "should fail to validate a random XML document" in {
      val xml = "<xml><hello>world</hello></xml>"
      XmlUtils.parseDocument(xml).isSuccess           shouldBe true
      XmlUtils.validateChRISSubmission(xml).isFailure shouldBe true
    }
  }

}
