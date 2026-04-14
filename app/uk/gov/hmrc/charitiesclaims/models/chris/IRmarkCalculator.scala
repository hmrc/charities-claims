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

import uk.gov.hmrc.charitiesclaims.xml.{XmlOutputBuilder, XmlUtils, XmlWriter}

import java.security.MessageDigest
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import uk.gov.hmrc.charitiesclaims.xml.XmlUtils.*

object IRmarkCalculator {

  def computeLiteIRmark(body: Body): String = {
    val builder   = new LiteIRmarkBuilder()
    val xmlWriter = summon[XmlWriter[Body]]
    xmlWriter.write(xmlWriter.label, body)(using builder)
    val xml       = builder.result.compactPrint(omitXmlDeclaration = true)
    hashSHA1Base64(xml)
  }

  def computeFullIRmark(body: Body): String = {
    val builder   = new LiteIRmarkBuilder()
    val xmlWriter = summon[XmlWriter[Body]]
    xmlWriter.write(xmlWriter.label, body)(using builder)
    val xml       = XmlUtils.canonicalizeXml(builder.result)
    hashSHA1Base64(xml)
  }

  def hashSHA1Base64(xml: String): String = {
    val hash   = MessageDigest.getInstance("SHA-1")
    hash.update(xml.getBytes("UTF-8"))
    val digest = hash.digest()
    Base64.getEncoder.encodeToString(digest)
  }

  def asBase32(base64IRmark: String): String = {
    val bytes = Base64.getDecoder.decode(base64IRmark)
    Base32.encodeToBase32(bytes)
  }

  private class LiteIRmarkBuilder extends XmlOutputBuilder {

    type Result = org.w3c.dom.Document

    private val factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setNamespaceAware(true)

    private val document = {
      val builder = factory.newDocumentBuilder();
      builder.newDocument();
    }

    val stack = scala.collection.mutable.Stack.empty[org.w3c.dom.Node]
    stack.push(document)

    private var skip = false

    final override def appendElementStart(name: String): Unit = {
      if name == "IRmark" then skip = true
      if !skip then {
        val node = document.createElement(name)
        if name == "Body" then node.setAttribute("xmlns", "http://www.govtalk.gov.uk/CM/envelope")
        stack.head.appendChild(node)
        stack.push(node)
      }
    }

    final override def appendElementStart(name: String, attributes: Iterable[(String, String)]): Unit = {
      if name == "IRmark" then skip = true
      if !skip then {
        val node = document.createElement(name)
        if name == "Body" then node.setAttribute("xmlns", "http://www.govtalk.gov.uk/CM/envelope")
        attributes.foreach { case (key, value) =>
          val attribute = document.createAttribute(key)
          attribute.setValue(value)
          node.setAttributeNode(attribute)
        }
        stack.head.appendChild(node)
        stack.push(node)
      }
    }

    final override def appendElementEnd(name: String): Unit =
      if !skip then stack.pop()
      if name == "IRmark" then skip = false

    final override def appendText(text: String): Unit =
      if !skip then {
        val node = document.createTextNode(text)
        stack.head.appendChild(node)
      }

    final override def result: org.w3c.dom.Document = document
  }
}
