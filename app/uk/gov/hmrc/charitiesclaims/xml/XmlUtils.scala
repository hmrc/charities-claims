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

import scala.util.Try
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory
import java.io.ByteArrayInputStream
import javax.xml.validation.Validator
import javax.xml.transform.dom.DOMSource
import javax.xml.validation.Schema
import javax.xml.transform.stream.StreamSource
import javax.xml.transform.Source
import java.net.URL

object XmlUtils {

  val chrisSubmissionSchemaSourceMap = List(
    ("/xsd/xmldsig-core-schema.xsd", new URL("http://www.w3.org/2000/09/xmldsig#")),
    ("/xsd/r68-v0-3.xsd", new URL("http://www.govtalk.gov.uk/taxation/charities/r68/1")),
    ("/xsd/envelope-v2-0-HMRC.xsd", new URL("http://www.govtalk.gov.uk/CM/envelope"))
  )

  lazy val chrisSubmissionSchema: Schema = loadSchema(chrisSubmissionSchemaSourceMap).get

  def validateChRISSubmission(xml: String): Try[Document] =
    validateXml(xml, chrisSubmissionSchema)

  def validateXml(xml: String, schema: Schema): Try[Document] =
    parseDocument(xml)
      .flatMap { document =>
        Try {
          val validator: Validator          = schema.newValidator()
          val errorHandler: XMLErrorHandler = new XMLErrorHandler()
          validator.setErrorHandler(errorHandler)
          validator.validate(new DOMSource(document))
          if errorHandler.hasError
          then throw new Exception("Invalid XML: " + errorHandler.getLog)
          else document
        }
      }

  def parseDocument(document: String): Try[Document] = Try {
    val documentBuilderFactory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
    documentBuilderFactory.setNamespaceAware(true)
    documentBuilderFactory
      .newDocumentBuilder()
      .parse(new ByteArrayInputStream(document.getBytes("utf-8")))
  }

  class XMLErrorHandler extends org.xml.sax.helpers.DefaultHandler {
    import org.xml.sax.SAXParseException

    private val log = scala.collection.mutable.ListBuffer[String]()

    override def error(e: SAXParseException): Unit =
      log += ("ERROR: " + e.getMessage())

    override def fatalError(e: SAXParseException): Unit =
      log += ("FATAL: " + e.getMessage())

    override def warning(e: SAXParseException) =
      log += ("WARNING: " + e.getMessage())

    def hasError: Boolean = !log.isEmpty
    def getLog: String    = log.mkString(" ")
  }

  def loadSchema(schemaSourceMap: List[(String, URL)]): Try[Schema] = Try {
    import javax.xml.validation.SchemaFactory
    val schemaFactory: SchemaFactory = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI)
    schemaFactory.newSchema(
      schemaSourceMap
        .map((filename, url) => new StreamSource(this.getClass.getResourceAsStream(filename), url.toExternalForm))
        .toArray[Source]
    )
  }
}
