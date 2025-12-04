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

package uk.gov.hmrc.charitiesclaims.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class XmlWriterSpec extends AnyWordSpec with Matchers {

  case class Person(
    name: String,
    age: Int,
    email: Option[String],
    address: Option[Address],
    isStudent: Boolean,
    tags: List[tag] = Nil,
    citizenship: Citizenship
  ) derives XmlWriter

  enum Citizenship derives XmlWriter {
    case UK
    case other
  }

  case class tag(
    name: XmlAttribute[String],
    value: XmlContent[String]
  ) derives XmlWriter

  case class Address(
    street: String,
    city: String,
    postcode: String
  ) derives XmlWriter

  "XmlWriter" should {
    "serialise String to XML correctly" in {
      val entity = "Hello, world!"
      val xml    = XmlWriter.write(entity, addXmlDeclaration = false)
      xml shouldEqual entity
    }
    "serialise Int to XML correctly" in {
      val entity = 5
      val xml    = XmlWriter.write(entity, addXmlDeclaration = false)
      xml shouldEqual "5"
    }
    "serialise Double to XML correctly" in {
      val entity = 5.15
      val xml    = XmlWriter.write(entity, addXmlDeclaration = false)
      xml shouldEqual "5.15"
    }
    "serialise Boolean true to XML correctly" in {
      val entity = true
      val xml    = XmlWriter.write(entity, addXmlDeclaration = false)
      xml shouldEqual "true"
    }
    "serialise object to XML correctly" in {
      val entity = Person(
        name = "John Doe",
        age = 30,
        email = Some("john.doe@example.com"),
        address = Some(Address(street = "123 <Main> St", city = "&Anytown", postcode = "12345")),
        isStudent = false,
        tags = List(
          tag(name = "tag1\"", value = "value1"),
          tag(name = "<tag2>", value = "value2")
        ),
        citizenship = Citizenship.UK
      )
      val xml    = XmlWriter.write(entity, addXmlDeclaration = false)
      println(xml)
      xml shouldEqual
        """<Person>
          |    <name>John Doe</name>
          |    <age>30</age>
          |    <email>john.doe@example.com</email>
          |    <address>
          |        <street>123 &lt;Main&gt; St</street>
          |        <city>&amp;Anytown</city>
          |        <postcode>12345</postcode>
          |    </address>
          |    <isStudent>false</isStudent>
          |    <tags>
          |        <tag name="tag1&quot;">value1</tag>
          |        <tag name="&lt;tag2&gt;">value2</tag>
          |    </tags>
          |    <citizenship>UK</citizenship>
          |</Person>""".stripMargin
    }
  }
}
