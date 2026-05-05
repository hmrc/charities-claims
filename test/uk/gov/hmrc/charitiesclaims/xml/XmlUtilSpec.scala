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
import uk.gov.hmrc.charitiesclaims.xml.XmlUtils.*

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
        ("/xsd/r68-v2-0.xsd", new URL("http://www.govtalk.gov.uk/taxation/charities/r68/2")),
        ("/xsd/envelope-v2-0-HMRC.xsd", new URL("http://www.govtalk.gov.uk/CM/envelope"))
      )
      val schema           = XmlUtils.loadSchema(schemaSoourceMap)
      schema.isSuccess shouldBe true
    }

    "should fail to load an incomplete XSD schema" in {
      val schemaSourceMap = List(
        ("/xsd/r68-v2-0.xsd", new URL("http://www.govtalk.gov.uk/taxation/charities/r68/2")),
        ("/xsd/envelope-v2-0-HMRC.xsd", new URL("http://www.govtalk.gov.uk/CM/envelope"))
      )
      val schema          = XmlUtils.loadSchema(schemaSourceMap)
      schema.isFailure shouldBe true
    }

    "should validate an example valid ChRIS submission XML document" in {
      val xml = scala.io.Source
        .fromInputStream(this.getClass.getResourceAsStream("/test-chris-submission-1.xml"))
        .getLines()
        .mkString("\n")

      val document = XmlUtils.parseDocument(xml)
      document.isSuccess                                       shouldBe true
      XmlUtils.validateChRISSubmission(document.get).isSuccess shouldBe true
    }

    "should validate an example valid ChRIS submission XML document with max occurs" in {
      val xml = scala.io.Source
        .fromInputStream(this.getClass.getResourceAsStream("/test-chris-submission-9.xml"))
        .getLines()
        .mkString("\n")

      val document = XmlUtils.parseDocument(xml)
      document.isSuccess shouldBe true
      val result = XmlUtils.validateChRISSubmission(document.get)
      result.isSuccess shouldBe true
    }

    "should fail to validate an example invalid ChRIS submission XML document" in {
      val xml = scala.io.Source
        .fromInputStream(this.getClass.getResourceAsStream("/test-chris-submission-invalid.xml"))
        .getLines()
        .mkString("\n")

      val document = XmlUtils.parseDocument(xml)
      document.isSuccess shouldBe true
      val result = XmlUtils.validateChRISSubmission(document.get)
      result.isSuccess shouldBe false
      result.fold(
        exception =>
          exception.getMessage shouldBe """Invalid XML: ERROR: cvc-complex-type.2.4.a: Invalid content was found starting with element '{"http://www.govtalk.gov.uk/CM/envelope":Body}'. One of '{"http://www.govtalk.gov.uk/CM/envelope":GovTalkDetails}' is expected.""",
        _ => fail("Expected errors")
      )
    }

    "should fail to validate an example invalid ChRIS submission XML document with max occurs" in {
      val xml = scala.io.Source
        .fromInputStream(this.getClass.getResourceAsStream("/test-chris-submission-invalid-max-occurs.xml"))
        .getLines()
        .mkString("\n")

      val document = XmlUtils.parseDocument(xml)
      document.isSuccess shouldBe true
      val result = XmlUtils.validateChRISSubmission(document.get)
      result.isSuccess shouldBe false
      result.fold(
        exception =>
          exception.getMessage shouldBe """Invalid XML: ERROR: cvc-complex-type.2.4.e: 'GASDSClaimed' can occur a maximum of '3' times in the current sequence. This limit was exceeded. At this point one of '{"http://www.govtalk.gov.uk/taxation/charities/r68/2":CommBldgs}' is expected. ERROR: cvc-complex-type.2.4.a: Invalid content was found starting with element '{"http://www.govtalk.gov.uk/taxation/charities/r68/2":Address}'. One of '{"http://www.govtalk.gov.uk/taxation/charities/r68/2":Postcode}' is expected.""",
        _ => fail("Expected errors")
      )
    }

    "should fail to validate a random XML document" in {
      val xml      = "<xml><hello>world</hello></xml>"
      val document = XmlUtils.parseDocument(xml)
      document.isSuccess                                       shouldBe true
      XmlUtils.validateChRISSubmission(document.get).isFailure shouldBe true
    }

    "should canonicalize an XML document" in {
      val xml              =
        "<ns1:hello xmlns:ns1=\"http://www.example.com\"><the value=\"Foo\" name=\"Bar\" >world</the><org>ORG</org></ns1:hello >"
      val document         = XmlUtils.parseDocument(xml)
      val canonicalizedXml = XmlUtils.canonicalizeXml(document.get)
      canonicalizedXml shouldBe "<ns1:hello xmlns:ns1=\"http://www.example.com\"><the name=\"Bar\" value=\"Foo\">world</the><org>ORG</org></ns1:hello>"
    }

    "should pretty print an XML document" in {
      val xml      = "<xml><hello>world</hello></xml>"
      val document = XmlUtils.parseDocument(xml)
      document.isSuccess                                         shouldBe true
      document.get
        .prettyPrint(indentation = 4, omitXmlDeclaration = true) shouldBe """|<xml>
                                                                             |    <hello>world</hello>
                                                                             |</xml>""".stripMargin
    }

    "should pretty print an XML document with XML declaration" in {
      val xml      = "<xml><hello>world</hello></xml>"
      val document = XmlUtils.parseDocument(xml)
      document.isSuccess              shouldBe true
      document.get
        .prettyPrint(indentation = 4) shouldBe """|<?xml version='1.0' encoding='UTF-8'?>
                                                  |<xml>
                                                                             |    <hello>world</hello>
                                                                             |</xml>""".stripMargin
    }

    "should compact print an XML document" in {
      val xml      = "<xml><hello>world</hello></xml>"
      val document = XmlUtils.parseDocument(xml)
      document.isSuccess                                   shouldBe true
      document.get.compactPrint(omitXmlDeclaration = true) shouldBe "<xml><hello>world</hello></xml>"
    }

    "should compact print an XML document with XML declaration" in {
      val xml      = "<xml><hello>world</hello></xml>"
      val document = XmlUtils.parseDocument(xml)
      document.isSuccess          shouldBe true
      document.get.compactPrint() shouldBe """<?xml version='1.0' encoding='UTF-8'?><xml><hello>world</hello></xml>"""
    }
  }

}
