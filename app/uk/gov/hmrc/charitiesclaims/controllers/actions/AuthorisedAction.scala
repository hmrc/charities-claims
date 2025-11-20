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

package uk.gov.hmrc.charitiesclaims.controllers.actions

import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.mvc.*
import play.api.mvc.Results.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.charitiesclaims.models.requests.AuthorisedRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.auth.core.retrieve.~

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[DefaultAuthorisedAction])
trait AuthorisedAction
    extends ActionBuilder[AuthorisedRequest, AnyContent]
    with ActionFunction[Request, AuthorisedRequest]

@Singleton
class DefaultAuthorisedAction @Inject() (
  override val authConnector: AuthConnector,
  val parser: BodyParsers.Default
)(implicit val executionContext: ExecutionContext)
    extends AuthorisedAction
    with AuthorisedFunctions {

  val logger: Logger = Logger(this.getClass)

  override def invokeBlock[A](request: Request[A], block: AuthorisedRequest[A] => Future[Result]): Future[Result] = {

    given HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    authorised()
      .retrieve(Retrievals.affinityGroup.and(Retrievals.credentials)) {
        case Some(affinityGroup) ~ Some(credentials)
            if affinityGroup == AffinityGroup.Organisation
              || affinityGroup == AffinityGroup.Agent =>
          block(AuthorisedRequest(request, affinityGroup, credentials.providerId))

        case _ ~ None =>
          Future.successful(Forbidden(s"No credentials providerId found for user"))

        case Some(affinityGroup) ~ _ =>
          Future.successful(Forbidden(s"Unsupported affinity group: $affinityGroup"))

        case None ~ _ =>
          Future.successful(Forbidden(s"Unauthorized: No affinity group found"))
      }
      .recover { case e: AuthorisationException =>
        Forbidden(s"Unauthorized: ${e.reason}")
      }
  }
}
