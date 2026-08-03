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

package uk.gov.hmrc.charitiesclaims.connectors

import com.google.inject.ImplementedBy
import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.util.ByteString
import play.api.libs.ws.{BodyWritable, InMemoryBody}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.charitiesclaims.models.chris.GovTalkMessage
import uk.gov.hmrc.charitiesclaims.validation.{SchematronValidationException, SchematronValidator}
import uk.gov.hmrc.charitiesclaims.xml.XmlUtils.*
import uk.gov.hmrc.charitiesclaims.xml.{XmlUtils, XmlWriter}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, Retries}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.{ConnectException, URL, UnknownHostException}
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.net.ssl.SSLHandshakeException
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class ChRISSubmissionRejected(status: Int, body: String) extends Exception(s"ChRIS returned $status: $body")

@ImplementedBy(classOf[ChRISConnectorImpl])
trait ChRISConnector {

  def submitClaim(govTalkMessage: GovTalkMessage)(using
    hc: HeaderCarrier
  ): Future[Unit]
}

class ChRISConnectorImpl @Inject() (
  http: HttpClientV2,
  config: Configuration,
  servicesConfig: ServicesConfig,
  val actorSystem: ActorSystem
)(using
  ExecutionContext
) extends ChRISConnector
    with Retries {

  private val logger = Logger(getClass)

  def configuration: Config = config.underlying

  val baseUrl: String = servicesConfig.getString("microservice.services.chris.baseUrl")
  val path: String    = servicesConfig.getString("microservice.services.chris.path")

  given BodyWritable[String] =
    BodyWritable(str => InMemoryBody(ByteString.fromString(str)), "text/xml;charset=UTF-8")

  final def submitClaim(govTalkMessage: GovTalkMessage)(using
    hc: HeaderCarrier
  ): Future[Unit] =
    val document = XmlWriter.writeDocument(govTalkMessage)
    Future
      .fromTry(XmlUtils.validateChRISSubmission(document))
      .flatMap { _ =>
        SchematronValidator.validate(govTalkMessage) match
          case Left(errors) => Future.failed(SchematronValidationException(errors))
          case Right(_)     => Future.unit
      }
      .flatMap { _ =>
        val requestBody = document.compactPrint()

        logger.info(s"Submitting claim to ChRIS at POST $baseUrl$path with a ChRISXML $requestBody")

        val attempt = AtomicInteger(0)

        retryFor("submitClaim") { case e => neverReachedChRIS(e) } {
          val attemptNumber = attempt.incrementAndGet()
          if attemptNumber > 1 then
            logger.warn(s"Retrying ChRIS submission (attempt $attemptNumber): POST $baseUrl$path")

          http
            .post(URL(s"$baseUrl$path"))
            .withBody(requestBody)
            .execute[HttpResponse]
            .flatMap(response =>
              if response.status == 200
              then {
                logger.info(s"Successfully submitted claim to ChRIS")
                Future.successful(())
              } else {
                logger.error(s"ChRIS submission failed: POST $baseUrl$path returned ${response.status}")
                Future.failed(ChRISSubmissionRejected(response.status, response.body))
              }
            )
        }
      }
      .transform {
        case Success(value)     => Success(value)
        case Failure(exception) =>
          logger.error(s"ChRIS submission failed: ${exception.getMessage}")
          logger.error(document.prettyPrint())
          Failure(exception)
      }

  private[connectors] def neverReachedChRIS(t: Throwable, depth: Int = 0): Boolean =
    if t == null || depth > 10 then false
    else
      t match {
        case _: ConnectException      => true
        case _: UnknownHostException  => true
        case _: SSLHandshakeException => true
        case other                    => neverReachedChRIS(other.getCause, depth + 1)
      }

}
