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

import scala.deriving.Mirror
import scala.compiletime.{erasedValue, error, summonInline}
import scala.collection.AbstractIterable
import scala.compiletime.constValue
import scala.compiletime.constValueTuple
import scala.collection.View
import scala.annotation.nowarn
import javax.xml.parsers.DocumentBuilderFactory
import XmlUtils.*
import java.math.RoundingMode

trait XmlWriter[A] {
  def label: String
  def isAttribute: Boolean = false
  def isPrimitive: Boolean = false
  def write(name: String, value: A)(using XmlOutputBuilder): Unit
}

@nowarn
case class XmlAttribute[T : XmlWriter](attribute: T) {
  override def toString: String = attribute.toString
}

object XmlAttribute {
  given [A : XmlWriter] => Conversion[A, XmlAttribute[A]] = XmlAttribute[A](_)
}

@nowarn
case class XmlContent[T : XmlWriter](content: T) {
  override def toString: String = content.toString
}

object XmlContent {
  given [A : XmlWriter] => Conversion[A, XmlContent[A]] = XmlContent[A](_)
}

object XmlWriter {

  def writeDocument[T : XmlWriter](value: T): org.w3c.dom.Document = {
    val builder   = XmlOutputBuilder.document()
    val xmlWriter = summon[XmlWriter[T]]
    xmlWriter.write(xmlWriter.label, value)(using builder)
    builder.result
  }

  def writeIndented[T : XmlWriter](value: T, addXmlDeclaration: Boolean = true): String = {
    val builder   = XmlOutputBuilder.document()
    val xmlWriter = summon[XmlWriter[T]]
    xmlWriter.write(xmlWriter.label, value)(using builder)
    builder.result.prettyPrint(indentation = 4, omitXmlDeclaration = !addXmlDeclaration)
  }

  def writeCompact[T : XmlWriter](value: T, addXmlDeclaration: Boolean = true): String = {
    val builder   = XmlOutputBuilder.document()
    val xmlWriter = summon[XmlWriter[T]]
    xmlWriter.write(xmlWriter.label, value)(using builder)
    builder.result.compactPrint(omitXmlDeclaration = !addXmlDeclaration)
  }

  given XmlWriter[String] = new XmlWriter[String] {
    def label: String                                                             = "String"
    override def isPrimitive: Boolean                                             = true
    def write(name: String, value: String)(using builder: XmlOutputBuilder): Unit =
      builder.appendText(value)
  }

  given XmlWriter[Int] = new XmlWriter[Int] {
    def label: String                                                          = "Number"
    override def isPrimitive: Boolean                                          = true
    def write(name: String, value: Int)(using builder: XmlOutputBuilder): Unit =
      builder.appendText(value.toString)
  }

  given XmlWriter[BigDecimal] = new XmlWriter[BigDecimal] {
    def label: String                                                                 = "Number"
    override def isPrimitive: Boolean                                                 = true
    def write(name: String, value: BigDecimal)(using builder: XmlOutputBuilder): Unit =
      builder.appendText(value.underlying().setScale(2, RoundingMode.HALF_UP).toPlainString())
  }

  given XmlWriter[Boolean] = new XmlWriter[Boolean] {
    def label: String                                                              = "Boolean"
    override def isPrimitive: Boolean                                              = true
    def write(name: String, value: Boolean)(using builder: XmlOutputBuilder): Unit =
      builder.appendText(value.toString())
  }

  given [A : XmlWriter] => XmlWriter[Option[A]] = new XmlWriter[Option[A]] {
    def label: String                                                                = summon[XmlWriter[A]].label
    def write(name: String, value: Option[A])(using builder: XmlOutputBuilder): Unit =
      value.foreach { v =>
        val writer = summon[XmlWriter[A]]
        if (writer.isPrimitive) {
          builder.appendElementStart(name, View.empty)
        }
        writer.write(name, v)
        if (writer.isPrimitive) {
          builder.appendElementEnd(name)
        }
      }
  }

  given [A : XmlWriter] => XmlWriter[List[A]] = new XmlWriter[List[A]] {
    def label: String                                                              = summon[XmlWriter[A]].label
    override def isPrimitive: Boolean                                              = summon[XmlWriter[A]].isPrimitive
    def write(name: String, value: List[A])(using builder: XmlOutputBuilder): Unit =
      val elementWriter = summon[XmlWriter[A]]
      if (label == name && !elementWriter.isPrimitive) {
        value.map(elementWriter.write(label, _))
      } else {
        builder.appendElementStart(name, View.empty)
        value.map(elementWriter.write(label, _))
        builder.appendElementEnd(name)
      }
  }

  given [A : XmlWriter] => XmlWriter[XmlAttribute[A]] = new XmlWriter[XmlAttribute[A]] {
    def label: String                                                                      = summon[XmlWriter[A]].label
    override def isAttribute: Boolean                                                      = true
    override def isPrimitive: Boolean                                                      = summon[XmlWriter[A]].isPrimitive
    def write(name: String, value: XmlAttribute[A])(using builder: XmlOutputBuilder): Unit =
      summon[XmlWriter[A]].write(name, value.attribute)
  }

  given [A : XmlWriter] => XmlWriter[XmlContent[A]] = new XmlWriter[XmlContent[A]] {
    def label: String                                                                    = summon[XmlWriter[A]].label
    def write(name: String, value: XmlContent[A])(using builder: XmlOutputBuilder): Unit =
      summon[XmlWriter[A]].write(name, value.content)
  }

  inline def derived[T](using mirror: Mirror.Of[T]): XmlWriter[T] = {
    lazy val label = constValue[mirror.MirroredLabel].toString
    inline mirror match {
      case _: Mirror.SumOf[T]     => xmlWriterSum(label)
      case _: Mirror.ProductOf[T] =>
        lazy val elemInstances = summonInstances[T, mirror.MirroredElemTypes]
        lazy val elemNames     = constValueTuple[mirror.MirroredElemLabels].toList.map(_.toString)
        xmlWriterProduct(label, elemNames, elemInstances)
    }
  }

  private def xmlWriterSum[T](l: String): XmlWriter[T] =
    new XmlWriter[T] {
      def label: String                                                        = l
      def write(name: String, value: T)(using builder: XmlOutputBuilder): Unit =
        builder.appendElementStart(name, View.empty)
        builder.appendText(value.toString)
        builder.appendElementEnd(name)
    }

  private def iterable[T](p: T): Iterable[Any] = new AbstractIterable[Any]:
    def iterator: Iterator[Any] = p.asInstanceOf[Product].productIterator

  private def xmlWriterProduct[T](l: String, names: List[String], xmlWriters: => List[XmlWriter[?]]): XmlWriter[T] =
    val items = names.lazyZip(xmlWriters)
    new XmlWriter[T] {
      def label: String                                                        = l
      def write(name: String, value: T)(using builder: XmlOutputBuilder): Unit =
        val values     = items.lazyZip(iterable(value))
        val elements   = values.filterNot(_._2.isAttribute)
        val attributes = values.filter(_._2.isAttribute)
        builder.appendElementStart(name, attributes.map { case (n, _, v) => (n, v.toString) })
        elements.foreach { (n, xmlWriter, v) =>
          if (xmlWriter.isPrimitive) {
            builder.appendElementStart(n, View.empty)
          }
          xmlWriter.asInstanceOf[XmlWriter[Any]].write(n, v)(using builder)
          if (xmlWriter.isPrimitive) {
            builder.appendElementEnd(n)
          }
        }
        builder.appendElementEnd(name)
    }

  private inline def summonInstances[T, Elems <: Tuple]: List[XmlWriter[?]] =
    inline erasedValue[Elems] match
      case _: (elem *: elems) => deriveOrSummon[T, elem] :: summonInstances[T, elems]
      case _: EmptyTuple      => Nil

  private inline def deriveOrSummon[T, Elem]: XmlWriter[Elem] =
    inline erasedValue[Elem] match
      case _: T => deriveRec[T, Elem]
      case _    => summonInline[XmlWriter[Elem]]

  inline def deriveRec[T, Elem]: XmlWriter[Elem] =
    inline erasedValue[T] match
      case _: Elem => error("infinite recursive derivation")
      case _       => XmlWriter.derived[Elem](using summonInline[Mirror.Of[Elem]])
}

trait XmlOutputBuilder {

  type Result
  def result: Result

  def appendElementStart(name: String): Unit
  def appendElementStart(name: String, attributes: Iterable[(String, String)]): Unit
  def appendElementEnd(name: String): Unit
  def appendText(text: String): Unit
}

object XmlOutputBuilder {

  def document(): DocumentOutputBuilder =
    new DocumentOutputBuilder()

  class DocumentOutputBuilder extends XmlOutputBuilder {

    type Result = org.w3c.dom.Document

    private val factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setNamespaceAware(true)

    private val document = {
      val builder = factory.newDocumentBuilder()
      builder.newDocument()
    }

    val stack = scala.collection.mutable.Stack.empty[org.w3c.dom.Node]
    stack.push(document)

    final override def appendElementStart(name: String): Unit = {
      val node = document.createElement(name)
      stack.head.appendChild(node)
      stack.push(node)
    }

    final override def appendElementStart(name: String, attributes: Iterable[(String, String)]): Unit = {
      val namespace = attributes.find((name, _) => name == "xmlns").map((_, ns) => ns)
      val node      =
        namespace match {
          case Some(ns) => document.createElementNS(ns, name)
          case None     =>
            Option(stack.head.getNamespaceURI()) match {
              case Some(ns) => document.createElementNS(ns, name)
              case None     => document.createElement(name)
            }
        }

      attributes.foreach { case (key, value) =>
        val attribute = document.createAttribute(key)
        attribute.setValue(value)
        node.setAttributeNode(attribute)
      }

      stack.head.appendChild(node)
      stack.push(node)
    }

    final override def appendElementEnd(name: String): Unit =
      stack.pop()

    final override def appendText(text: String): Unit = {
      val node = document.createTextNode(text)
      stack.head.appendChild(node)
    }

    final override def result: org.w3c.dom.Document = document
  }
}
