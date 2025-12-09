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

import java.io.ByteArrayInputStream
import java.net.URL
import java.util.Iterator
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec
import javax.xml.crypto.dsig.{CanonicalizationMethod, XMLSignatureFactory}
import javax.xml.crypto.{Data, NodeSetData, OctetStreamData}
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.Source
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.{Schema, Validator}
import scala.io.Codec
import scala.util.Try

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

  def canonicalizeXml(xml: String) = {
    val dbf: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
    dbf.setNamespaceAware(true)

    val doc: Document = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes("utf-8")))

    val data: Data = new NodeSetDataImpl(doc, getRootNodeFilter())

    val fac: XMLSignatureFactory = XMLSignatureFactory.getInstance("DOM")

    val canonicalizationMethod: CanonicalizationMethod =
      fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, null.asInstanceOf[C14NMethodParameterSpec])

    val transformedData: OctetStreamData = canonicalizationMethod.transform(data, null).asInstanceOf[OctetStreamData]
    scala.io.Source.fromInputStream(transformedData.getOctetStream())(using Codec.UTF8).mkString
  }

  private def getRootNodeFilter(): NodeFilter =
    new NodeFilter() {
      def acceptNode(pNode: Node): Short = NodeFilter.FILTER_ACCEPT
    }

  private class NodeSetDataImpl(node: Node, nodeFilter: NodeFilter) extends NodeSetData[Node] {

    val document: Document =
      if node.isInstanceOf[Document]
      then node.asInstanceOf[Document]
      else node.getOwnerDocument()

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
}
