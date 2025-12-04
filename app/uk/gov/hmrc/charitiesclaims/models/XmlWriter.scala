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

import scala.deriving.Mirror
import scala.compiletime.{erasedValue, error, summonInline}
import scala.collection.AbstractIterable
import scala.compiletime.constValue
import scala.compiletime.constValueTuple
import scala.collection.View
import scala.annotation.nowarn

trait XmlWriter[A] {
  def label: String
  def isAttribute: Boolean = false
  def isPrimitive: Boolean = false
  def write(name: String, value: A)(using XmlStringBuilder): Unit
}

@nowarn
case class XmlAttribute[T : XmlWriter](attribute: T)

object XmlAttribute {
  given [A : XmlWriter] => Conversion[A, XmlAttribute[A]] = XmlAttribute[A](_)
}

@nowarn
case class XmlContent[T : XmlWriter](attribute: T)

object XmlContent {
  given [A : XmlWriter] => Conversion[A, XmlContent[A]] = XmlContent[A](_)
}

object XmlWriter {

  def write[T : XmlWriter](value: T, addXmlDeclaration: Boolean = true): String = {
    val builder: XmlStringBuilder = XmlStringBuilder(
      indentation = 4,
      initialString =
        if addXmlDeclaration
        then "<?xml version='1.0' encoding='UTF-8'?>"
        else "" // No XML declaration if not requested
    )
    val xmlWriter                 = summon[XmlWriter[T]]
    xmlWriter.write(xmlWriter.label, value)(using builder)
    builder.xmlStringResult
  }

  given XmlWriter[String] = new XmlWriter[String] {
    def label: String                                                             = "String"
    override def isPrimitive: Boolean                                             = true
    def write(name: String, value: String)(using builder: XmlStringBuilder): Unit =
      builder.appendText(value)
  }

  @nowarn
  given [A : Numeric] => XmlWriter[A] = new XmlWriter[A] {
    def label: String                                                        = "Number"
    override def isPrimitive: Boolean                                        = true
    def write(name: String, value: A)(using builder: XmlStringBuilder): Unit =
      builder.appendText(value.toString)
  }

  given XmlWriter[Boolean] = new XmlWriter[Boolean] {
    def label: String                                                              = "Boolean"
    override def isPrimitive: Boolean                                              = true
    def write(name: String, value: Boolean)(using builder: XmlStringBuilder): Unit =
      builder.appendText(value.toString())
  }

  given [A : XmlWriter] => XmlWriter[Option[A]] = new XmlWriter[Option[A]] {
    def label: String                                                                = summon[XmlWriter[A]].label
    def write(name: String, value: Option[A])(using builder: XmlStringBuilder): Unit =
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
    def write(name: String, value: List[A])(using builder: XmlStringBuilder): Unit =
      builder.appendElementStart(name, View.empty)
      value.map(summon[XmlWriter[A]].write(label, _))
      builder.appendElementEnd(name)
  }

  given [A : XmlWriter] => XmlWriter[XmlAttribute[A]] = new XmlWriter[XmlAttribute[A]] {
    def label: String                                                                      = summon[XmlWriter[A]].label
    override def isAttribute: Boolean                                                      = true
    override def isPrimitive: Boolean                                                      = summon[XmlWriter[A]].isPrimitive
    def write(name: String, value: XmlAttribute[A])(using builder: XmlStringBuilder): Unit =
      summon[XmlWriter[A]].write(name, value.attribute)
  }

  given [A : XmlWriter] => XmlWriter[XmlContent[A]] = new XmlWriter[XmlContent[A]] {
    def label: String                                                                    = summon[XmlWriter[A]].label
    def write(name: String, value: XmlContent[A])(using builder: XmlStringBuilder): Unit =
      summon[XmlWriter[A]].write(name, value.attribute)
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
      def write(name: String, value: T)(using builder: XmlStringBuilder): Unit =
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
      def write(name: String, value: T)(using builder: XmlStringBuilder): Unit =
        val values     = items.lazyZip(iterable(value))
        val elements   = values.filterNot(_._2.isAttribute)
        val attributes = values.filter(_._2.isAttribute)
        builder.appendElementStart(name, attributes)
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

trait XmlStringBuilder {
  def appendElementStart(name: String, attributes: View[(String, XmlWriter[?], Any)]): Unit
  def appendElementEnd(name: String): Unit
  def appendText(text: String): Unit

  def xmlStringResult: String
}

object XmlStringBuilder {
  def apply(indentation: Int, initialString: String = ""): XmlStringBuilder = new XmlStringBuilder {

    private val sb = new StringBuilder(initialString)

    private val indentationString = " " * indentation
    private var indentationLevel  = 0
    private var previous          = '-'
    private var context           = 'e'

    def indent(): Unit =
      sb.append(indentationString * indentationLevel)

    def newline(): Unit =
      sb.append("\n")

    def appendElementStart(name: String, attributes: View[(String, XmlWriter[?], Any)]): Unit = {
      if !sb.isEmpty then {
        newline()
        indent()
      }
      sb.append(s"<$name")
      attributes.foreach { case (k, w, v) =>
        sb.append(s" $k=")
        context = 'a'
        sb.append(s"\"")
        w.asInstanceOf[XmlWriter[Any]].write(k, v)(using this)
        sb.append(s"\"")
        context = 'e'
      }
      sb.append(">")
      indentationLevel = indentationLevel + 1
      previous = 's'
    }

    def appendElementEnd(name: String): Unit = {
      indentationLevel = indentationLevel - 1
      if (previous == 'e') {
        newline()
        indent()
      }
      sb.append(s"</$name>")
      previous = 'e'
    }

    def appendText(text: String): Unit = {
      sb.append(
        context match {
          case 'a' => escapeForAttribute(text)
          case 'e' => escapeForElement(text)
        }
      )
      previous = 't'
    }

    def escapeForAttribute(text: String): String = text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")

    def escapeForElement(text: String): String = text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")

    def xmlStringResult: String = sb.toString()
  }
}
