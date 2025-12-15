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
import play.api.Configuration
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import javax.inject.Inject
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.charitiesclaims.models.SaveUnregulatedDonationRequest
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.*

@ImplementedBy(classOf[FormpProxyConnectorImpl])
trait FormpProxyConnector {
  type CharityReference = String

  /** Sum of all the unregulated donations for the given charity reference
    *
    * @param charityReference
    * @return
    *   The total of all the unregulated donations for the given charity reference
    */
  def getTotalUnregulatedDonations(charityReference: CharityReference)(using
    hc: HeaderCarrier
  ): Future[Option[BigDecimal]]

  /** Save a unregulated donation for the given charity reference
    *
    * @param charityReference
    * @param claimId
    * @param amount
    */
  def saveUnregulatedDonation(charityReference: CharityReference, claimId: Int, amount: BigDecimal)(using
    hc: HeaderCarrier
  ): Future[Unit]
}

class FormpProxyConnectorImpl @Inject() (
  http: HttpClientV2,
  configuration: Configuration,
  servicesConfig: ServicesConfig,
  val actorSystem: ActorSystem
)(using
  ExecutionContext
) extends FormpProxyConnector
    with Retries {

  val baseUrl: String = servicesConfig.baseUrl("formp-proxy")

  val retryIntervals: Seq[FiniteDuration] = Retries.getConfIntervals("formp-proxy", configuration)

  val contextPath: String = servicesConfig
    .getConfString("formp-proxy.context-path", "formp-proxy")

  final def getTotalUnregulatedDonations(charityReference: CharityReference)(using
    hc: HeaderCarrier
  ): Future[Option[BigDecimal]] =
    retry(retryIntervals*)(shouldRetry, retryReason)(
      http
        .get(URL(s"$baseUrl$contextPath/charities/$charityReference/unregulated-donations"))
        .execute[HttpResponse]
    ).flatMap(response =>
      if response.status == 200
      then
        Future {
          response.json
            .asOpt[JsObject]
            .flatMap(_.value("unregulatedDonationsTotal").asOpt[BigDecimal])
        }
      else if response.status == 404
      then Future.successful(None)
      else
        Future.failed(
          Exception(
            s"Request to GET $baseUrl$contextPath/charities/$charityReference/unregulated-donations failed because of $response ${response.body}"
          )
        )
    )

  final def saveUnregulatedDonation(charityReference: CharityReference, claimId: Int, amount: BigDecimal)(using
    hc: HeaderCarrier
  ): Future[Unit] =
    retry(retryIntervals*)(shouldRetry, retryReason)(
      http
        .post(URL(s"$baseUrl$contextPath/charities/$charityReference/unregulated-donations"))
        .withBody(Json.toJson(SaveUnregulatedDonationRequest(claimId, amount)))
        .execute[HttpResponse]
    ).flatMap(response =>
      if response.status == 200
      then Future.successful(())
      else
        Future.failed(
          Exception(
            s"Request to POST $baseUrl$contextPath/charities/$charityReference/unregulated-donations failed because of $response ${response.body}"
          )
        )
    )

}
