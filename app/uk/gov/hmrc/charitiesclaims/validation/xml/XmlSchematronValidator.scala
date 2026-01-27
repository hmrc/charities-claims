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

package uk.gov.hmrc.charitiesclaims.validation.xml

import uk.gov.hmrc.charitiesclaims.validation.*
import org.w3c.dom.{Document, Node, NodeList}
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.{XPath, XPathConstants, XPathFactory}
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Success, Try}

object XmlSchematronValidator extends SchematronValidator[String] {

  extension (nodeList: NodeList) def toSeq: Seq[Node] = (0 until nodeList.getLength).map(nodeList.item)

  private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  private val namespaceContext = new javax.xml.namespace.NamespaceContext {
    override def getNamespaceURI(prefix: String): String              = prefix match {
      case "env" => "http://www.govtalk.gov.uk/CM/envelope"
      case "r68" => "http://www.govtalk.gov.uk/taxation/charities/r68/2"
      case _     => null
    }
    override def getPrefix(uri: String): String                       = null
    override def getPrefixes(uri: String): java.util.Iterator[String] = null
  }

  override def validate(xml: String): SchematronResult =
    Try(parseDocument(xml)).toEither match
      case Left(_)         => Left(List(ValidationError("PARSE_ERROR", "Failed to parse XML document")))
      case Right(document) =>
        combineResults(
          List(
            validateClaimRule(document),
            validateAuthOfficialRule(document),
            validateDateRule(document),
            validateKeyRule(document),
            validateAggDonationRule(document)
          )
        )

  private def parseDocument(xml: String): Document =
    val factory = DocumentBuilderFactory.newInstance()
    factory.setNamespaceAware(true)
    factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes("UTF-8")))

  private def createXPath(): XPath =
    val xpath = XPathFactory.newInstance().newXPath()
    xpath.setNamespaceContext(namespaceContext)
    xpath

  private def evaluateXPath(document: Document, expression: String): NodeList =
    val xpath = createXPath()
    xpath.evaluate(expression, document, XPathConstants.NODESET).asInstanceOf[NodeList]

  private def evaluateXPathString(document: Document, expression: String): String =
    val xpath = createXPath()
    xpath.evaluate(expression, document)

  private def nodeExists(document: Document, expression: String): Boolean =
    evaluateXPath(document, expression).getLength > 0

  /** Rule 7028: Must have Repayment OR GASDS (or both)
    */
  def validateClaimRule(document: Document): ValidationResult =
    val hasRepayment = nodeExists(document, "//r68:Claim/r68:Repayment")
    val hasGasds     = nodeExists(document, "//r68:Claim/r68:GiftAidSmallDonationsScheme")

    if hasRepayment || hasGasds then ValidationResult.Success
    else ValidationResult.Error(ValidationError.ClaimRule)

  /** Rule 7026: AuthOfficial must have either OffName or Trustee
    */
  def validateAuthOfficialRule(document: Document): ValidationResult =
    val hasAuthOfficial = nodeExists(document, "//r68:R68/r68:AuthOfficial")

    if !hasAuthOfficial then ValidationResult.Success
    else
      val hasTrustee     = nodeExists(document, "//r68:AuthOfficial/r68:Trustee")
      val hasOffNameFore = nodeExists(document, "//r68:AuthOfficial/r68:OffName/r68:Fore")
      val hasOffNameSur  = nodeExists(document, "//r68:AuthOfficial/r68:OffName/r68:Sur")
      val hasOffName     = hasOffNameFore || hasOffNameSur

      if hasOffName || hasTrustee then ValidationResult.Success
      else ValidationResult.Error(ValidationError.AuthOfficialRule)

  /** Rule 7040: EarliestGAdate must not be in the future
    */
  def validateDateRule(document: Document): ValidationResult =
    val hasRepayment = nodeExists(document, "//r68:Claim/r68:Repayment")

    if !hasRepayment then ValidationResult.Success
    else
      val earliestGAdate = evaluateXPathString(document, "//r68:Repayment/r68:EarliestGAdate/text()")

      if earliestGAdate.isEmpty then ValidationResult.Success
      else
        Try(LocalDate.parse(earliestGAdate, dateFormatter)) match
          case Success(date) =>
            if !date.isAfter(LocalDate.now()) then ValidationResult.Success
            else ValidationResult.Error(ValidationError.DateRule)
          case Failure(_)    =>
            ValidationResult.Success

  /** Rule 5005: IRheader keys must match GovTalkDetails keys (for CHARID key)
    */
  def validateKeyRule(document: Document): ValidationResult =
    val govTalkCharId  =
      evaluateXPathString(document, "//env:GovTalkDetails/env:Keys/env:Key[@Type='CHARID']/text()")
    val irHeaderCharId = evaluateXPathString(document, "//r68:IRheader/r68:Keys/r68:Key[@Type='CHARID']/text()")

    if govTalkCharId.nonEmpty && irHeaderCharId.nonEmpty && govTalkCharId != irHeaderCharId
    then ValidationResult.Error(ValidationError.KeyRule)
    else ValidationResult.Success

  /** Rule 7038: Aggregated donations total must not exceed 1000
    */
  def validateAggDonationRule(document: Document): ValidationResult =
    val hasRepayment = nodeExists(document, "//r68:Claim/r68:Repayment")

    if !hasRepayment then ValidationResult.Success
    else
      val gadNodes = evaluateXPath(document, "//r68:Repayment/r68:GAD")
      val xpath    = createXPath()

      val exceedsLimit = gadNodes.toSeq.exists { gadNode =>
        val hasAggDonation =
          xpath.evaluate("r68:AggDonation", gadNode, XPathConstants.NODESET).asInstanceOf[NodeList].getLength > 0

        hasAggDonation && {
          val total = xpath.evaluate("r68:Total/text()", gadNode)
          total.nonEmpty && total.toDoubleOption.exists(_ > 1000)
        }
      }

      if exceedsLimit then ValidationResult.Error(ValidationError.AggDonationRule)
      else ValidationResult.Success
}
