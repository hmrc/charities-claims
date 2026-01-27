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

import java.security.MessageDigest
import java.util.Base64
import org.encalmo.writer.xml.XmlWriter
import org.encalmo.writer.xml.XmlOutputBuilder

import uk.gov.hmrc.charitiesclaims.xml.XmlUtils

object IRmarkCalculator {

  def computeLiteIRmark(body: Body): String = {
    val builder = new LiteIRmarkBuilder()
    XmlWriter.append("Body", body)(using builder)
    hashSHA1Base64(builder.result)
  }

  def computeFullIRmark(body: Body): String = {
    val builder = new LiteIRmarkBuilder()
    XmlWriter.append("Body", body)(using builder)
    hashSHA1Base64(XmlUtils.canonicalizeXml(builder.result))
  }

  def hashSHA1Base64(xml: String): String = {
    val hash   = MessageDigest.getInstance("SHA-1")
    hash.update(xml.getBytes("UTF-8"))
    val digest = hash.digest()
    Base64.getEncoder.encodeToString(digest)
  }

  private class LiteIRmarkBuilder extends XmlOutputBuilder {

    type Result = String

    private val sb   = new StringBuilder()
    private var skip = false

    final def appendElementStart(
      name: String
    ): Unit = {
      if name == "IRmark" then skip = true
      if !skip then {
        sb.append("<")
        sb.append(transformElementName(name))
        sb.append(">")
      }
    }

    final def appendElementStart(
      name: String,
      attributes: Iterable[(String, String)]
    ): Unit = {
      if name == "IRmark" then skip = true
      if !skip then {
        sb.append(s"<$name")
        if name == "Body" then sb.append(s" xmlns=\"http://www.govtalk.gov.uk/CM/envelope\"")
        attributes.foreach { case (k, v) =>
          sb.append(" ")
          sb.append(transformAttributeName(k))
          sb.append("=")
          sb.append("\"")
          sb.append(escapeTextForAttribute(v))
          sb.append("\"")
        }
        sb.append(">")
      }
    }

    final def appendElementEnd(name: String): Unit = {
      if !skip then {
        sb.append("</")
        sb.append(transformElementName(name))
        sb.append(">")
      }
      if name == "IRmark" then skip = false
    }

    final def appendText(text: String): Unit =
      if !skip then sb.append(escapeTextForElement(text))

    def result: String = sb.toString()
  }
}
