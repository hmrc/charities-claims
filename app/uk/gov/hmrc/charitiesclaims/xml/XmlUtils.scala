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

import org.w3c.dom.traversal.{DocumentTraversal, NodeFilter, NodeIterator}
import org.w3c.dom.{Document, Node}

import java.io.StringWriter
import java.net.{URI, URL}
import java.util.Iterator
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec
import javax.xml.crypto.dsig.{CanonicalizationMethod, XMLSignatureFactory}
import javax.xml.crypto.{Data, NodeSetData, OctetStreamData}
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.{StreamResult, StreamSource}
import javax.xml.transform.{OutputKeys, Source, TransformerFactory}
import javax.xml.validation.{Schema, Validator}
import scala.io.Codec
import scala.util.Try
import javax.xml.parsers.DocumentBuilderFactory
import java.io.ByteArrayInputStream

object XmlUtils {

  val chrisSubmissionSchemaSourceMap = List(
    ("/xsd/xmldsig-core-schema.xsd", new URI("http://www.w3.org/2000/09/xmldsig#").toURL),
    ("/xsd/r68-v0-3.xsd", new URI("http://www.govtalk.gov.uk/taxation/charities/r68/1").toURL),
    ("/xsd/envelope-v2-0-HMRC.xsd", new URI("http://www.govtalk.gov.uk/CM/envelope").toURL)
  )

  lazy val chrisSubmissionSchema: Schema = loadSchema(chrisSubmissionSchemaSourceMap).get

  def validateChRISSubmission(document: org.w3c.dom.Document): Try[Document] =
    validate(document, chrisSubmissionSchema)

  def validate(document: org.w3c.dom.Document, schema: Schema): Try[Document] =
    Try {
      val validator: Validator          = schema.newValidator()
      val errorHandler: XMLErrorHandler = new XMLErrorHandler()
      validator.setErrorHandler(errorHandler)
      validator.validate(DOMSource.apply(document.getDocumentElement()))
      if errorHandler.hasError
      then throw new Exception("Invalid XML: " + errorHandler.getLog)
      else document
    }

  private class XMLErrorHandler extends org.xml.sax.helpers.DefaultHandler {

    import org.xml.sax.SAXParseException

    private val log = scala.collection.mutable.ListBuffer[String]()

    override def error(e: SAXParseException): Unit =
      log += ("ERROR: " + e.getMessage())

    def hasError: Boolean = log.nonEmpty

    def getLog: String = log.mkString(" ")
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

  def canonicalizeXml(document: org.w3c.dom.Document): String = {
    val data: Data = new NodeSetDataImpl(document, getRootNodeFilter())

    val fac: XMLSignatureFactory = XMLSignatureFactory.getInstance("DOM")

    val canonicalizationMethod: CanonicalizationMethod =
      fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, null.asInstanceOf[C14NMethodParameterSpec])

    val transformedData: OctetStreamData = canonicalizationMethod.transform(data, null).asInstanceOf[OctetStreamData]
    scala.io.Source.fromInputStream(transformedData.getOctetStream())(using Codec.UTF8).mkString
  }

  private def secureDocumentBuilderFactory(): DocumentBuilderFactory = {
    val dbf = DocumentBuilderFactory.newInstance()
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    dbf.setFeature("http://xml.org/sax/features/external-general-entities", false)
    dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    dbf.setNamespaceAware(true)
    dbf
  }

  def parseDocument(document: String): Try[Document] = Try {
    val documentBuilderFactory: DocumentBuilderFactory = secureDocumentBuilderFactory()

    documentBuilderFactory
      .newDocumentBuilder()
      .parse(new ByteArrayInputStream(document.getBytes("utf-8")))
  }

  private def getRootNodeFilter(): NodeFilter =
    new NodeFilter() {
      def acceptNode(pNode: Node): Short = NodeFilter.FILTER_ACCEPT
    }

  private class NodeSetDataImpl(node: Node, nodeFilter: NodeFilter) extends NodeSetData[Node] {

    val document: Document = node match {
      case doc: Document => doc
      case _             => node.getOwnerDocument()
    }

    val documentTraversal: DocumentTraversal = document.asInstanceOf[DocumentTraversal]

    def iterator(): Iterator[Node] = {
      val nodeIterator = documentTraversal.createNodeIterator(node, NodeFilter.SHOW_ALL, nodeFilter, false)
      new NodeSetDataIterator(nodeIterator)
    }

  }

  private class NodeSetDataIterator(var nodeIterator: NodeIterator) extends java.util.Iterator[Node] {

    var nextNode: Node = null

    def checkNextNode(): Node = {
      if (nextNode == null && nodeIterator != null) {
        nextNode = nodeIterator.nextNode()
        if (nextNode == null) {
          nodeIterator.detach()
          nodeIterator = null
        }
      }
      nextNode
    }

    def consumeNextNode(): Node = {
      val nextNode2: Node = checkNextNode()
      nextNode = null
      nextNode2
    }

    override def hasNext(): Boolean =
      checkNextNode() != null

    override def next(): Node =
      consumeNextNode()
  }

  extension (document: org.w3c.dom.Document) {

    def prettyPrint(indentation: Int = 4, omitXmlDeclaration: Boolean = false): String =
      val transformer  = TransformerFactory.newInstance().newTransformer()
      transformer.setOutputProperty(OutputKeys.METHOD, "xml");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty(OutputKeys.INDENT, "yes")
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", indentation.toString)
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
      val source       = new DOMSource(document)
      val stringWriter = new StringWriter()
      transformer.transform(source, new StreamResult(stringWriter))
      val xml          = stringWriter.toString().dropRight(1) // drop the trailing newline
      if omitXmlDeclaration
      then xml
      else """<?xml version='1.0' encoding='UTF-8'?>""" + "\n" + xml

    def compactPrint(omitXmlDeclaration: Boolean = false): String =
      val transformer  = TransformerFactory.newInstance().newTransformer()
      transformer.setOutputProperty(OutputKeys.METHOD, "xml");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty(OutputKeys.INDENT, "no")
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
      val source       = new DOMSource(document)
      val stringWriter = new StringWriter()
      transformer.transform(source, new StreamResult(stringWriter))
      val xml          = stringWriter.toString()
      if omitXmlDeclaration
      then xml
      else """<?xml version='1.0' encoding='UTF-8'?>""" + xml
  }
}
