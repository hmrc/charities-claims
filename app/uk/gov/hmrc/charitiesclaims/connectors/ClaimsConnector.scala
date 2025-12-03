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

@ImplementedBy(classOf[ClaimsValidationConnectorImpl])
trait ClaimsValidationConnector {
  type UserId = String

  def deleteClaim(claimId: UserId)(using hc: HeaderCarrier): Future[Unit]
}

class ClaimsValidationConnectorImpl @Inject() (
  http: HttpClientV2,
  configuration: Configuration,
  servicesConfig: ServicesConfig,
  val actorSystem: ActorSystem
)(using
  ExecutionContext
) extends ClaimsValidationConnector
    with Retries {

  val baseUrl: String = servicesConfig.baseUrl("charities-claims-validation")

  val retryIntervals: Seq[FiniteDuration] = Retries.getConfIntervals("charities-claims-validation", configuration)

  val contextPath: String = servicesConfig
    .getConfString("charities-claims-validation.context-path", "charities-claims-validation")

  final def deleteClaim(claimId: UserId)(using
    hc: HeaderCarrier
  ): Future[Unit] =
    retry(retryIntervals*)(shouldRetry, retryReason)(
      http
        .delete(URL(s"$baseUrl$contextPath/claims/$claimId"))
        .execute[HttpResponse]
    ).flatMap(response =>
      if response.status == 200
      then Future.successful(())
      else
        Future.failed(
          Exception(
            s"Request to DELETE $baseUrl$contextPath/claims/$claimId failed because of $response ${response.body}"
          )
        )
    )

}
