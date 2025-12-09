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

import uk.gov.hmrc.charitiesclaims.xml.{XmlStringBuilder, XmlUtils, XmlWriter}

import java.security.MessageDigest
import java.util.Base64
import scala.collection.View

object IRmarkCalculator {

  def computeLiteIRmark(body: Body): String = {
    val builder   = new LiteIRmarkBuilder()
    val xmlWriter = summon[XmlWriter[Body]]
    xmlWriter.write(xmlWriter.label, body)(using builder)
    hashSHA1Base64(builder.xmlStringResult)
  }

  def computeFullIRmark(body: Body): String = {
    val builder   = new LiteIRmarkBuilder()
    val xmlWriter = summon[XmlWriter[Body]]
    xmlWriter.write(xmlWriter.label, body)(using builder)
    hashSHA1Base64(XmlUtils.canonicalizeXml(builder.xmlStringResult))
  }

  def hashSHA1Base64(xml: String): String = {
    val hash   = MessageDigest.getInstance("SHA-1")
    hash.update(xml.getBytes("UTF-8"))
    val digest = hash.digest()
    Base64.getEncoder.encodeToString(digest)
  }

  private class LiteIRmarkBuilder extends XmlStringBuilder {

    private val sb = new StringBuilder()

    private var context = 'e'
    private var skip    = false

    final def appendElementStart(name: String, attributes: View[(String, XmlWriter[?], Any)]): Unit = {
      if name == "IRmark" then skip = true
      if !skip then {
        sb.append(s"<$name")
        if name == "Body" then sb.append(s" xmlns=\"http://www.govtalk.gov.uk/CM/envelope\"")
        attributes.foreach { case (k, w, v) =>
          sb.append(s" $k=")
          context = 'a'
          sb.append(s"\"")
          w.asInstanceOf[XmlWriter[Any]].write(k, v)(using this)
          sb.append(s"\"")
          context = 'e'
        }
        sb.append(">")
      }
    }

    final def appendElementEnd(name: String): Unit = {
      if !skip then sb.append(s"</$name>")
      if name == "IRmark" then skip = false
    }

    final def appendText(text: String): Unit =
      if !skip then
        sb.append(
          context match {
            case 'a' => escapeForAttribute(text)
            case 'e' => escapeForElement(text)
          }
        )

    def xmlStringResult: String = sb.toString()
  }
}
