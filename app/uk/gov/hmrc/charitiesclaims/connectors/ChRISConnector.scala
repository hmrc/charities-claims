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
import org.apache.pekko.actor.ActorSystem
import play.api.{Configuration, Logger}
import uk.gov.hmrc.charitiesclaims.models.chris.GovTalkMessage
import uk.gov.hmrc.charitiesclaims.xml.XmlWriter
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import javax.inject.Inject
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.BodyWritable
import play.api.libs.ws.InMemoryBody
import org.apache.pekko.util.ByteString
import uk.gov.hmrc.charitiesclaims.xml.XmlUtils
import uk.gov.hmrc.charitiesclaims.validation.{SchematronValidationException, SchematronValidator}
import uk.gov.hmrc.charitiesclaims.xml.XmlUtils.*

@ImplementedBy(classOf[ChRISConnectorImpl])
trait ChRISConnector {

  def submitClaim(govTalkMessage: GovTalkMessage)(using
    hc: HeaderCarrier
  ): Future[Unit]
}

class ChRISConnectorImpl @Inject() (
  http: HttpClientV2,
  configuration: Configuration,
  servicesConfig: ServicesConfig,
  val actorSystem: ActorSystem
)(using
  ExecutionContext
) extends ChRISConnector
    with Retries {

  private val logger = Logger(getClass)

  val baseUrl: String = servicesConfig.getString("microservice.services.chris.baseUrl")
  val path: String    = servicesConfig.getString("microservice.services.chris.path")

  val retryIntervals: Seq[FiniteDuration] = Retries.getConfIntervals("chris", configuration)

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
        logger.info(s"Submitting claim to ChRIS at POST $baseUrl$path")
        retry(retryIntervals*)(shouldRetry, retryReason)(
          http
            .post(URL(s"$baseUrl$path"))
            .withBody(document.compactPrint())
            .execute[HttpResponse]
        ).flatMap(response =>
          if response.status == 200
          then {
            logger.info(s"Successfully submitted claim to ChRIS")
            Future.successful(())
          } else {
            logger.error(s"ChRIS submission failed: POST $baseUrl$path returned ${response.status}")
            Future.failed(
              Exception(
                s"Request to POST $baseUrl$path failed because of $response ${response.body}"
              )
            )
          }
        )
      }

}
