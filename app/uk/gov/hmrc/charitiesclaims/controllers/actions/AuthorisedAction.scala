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
import uk.gov.hmrc.charitiesclaims.controllers.BodyParsers

@ImplementedBy(classOf[DefaultAuthorisedAction])
trait AuthorisedAction extends ActionBuilder[AuthorisedRequest, String] with ActionFunction[Request, AuthorisedRequest]

@Singleton
class DefaultAuthorisedAction @Inject() (
  override val authConnector: AuthConnector
)(implicit val executionContext: ExecutionContext)
    extends AuthorisedAction
    with AuthorisedFunctions {

  val parser: BodyParser[String] = BodyParsers.parseTolerantTextUtf8
  val logger: Logger             = Logger(this.getClass)

  override def invokeBlock[A](request: Request[A], block: AuthorisedRequest[A] => Future[Result]): Future[Result] = {

    given HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    authorised()
      .retrieve(Retrievals.affinityGroup.and(Retrievals.allEnrolments).and(Retrievals.credentials)) {
        case Some(affinityGroup @ AffinityGroup.Agent) ~ AuthorisedAction.HasActiveAgentEnrolment(
              enrolmentIdentifier
            ) ~ Some(
              credentials
            ) =>
          block(AuthorisedRequest(request, affinityGroup, credentials.providerId, enrolmentIdentifier))

        case Some(AffinityGroup.Agent) ~ _ ~ _ =>
          Future.failed(UnsupportedAffinityGroup("Agent enrolment missing or not activated"))

        case Some(affinityGroup @ AffinityGroup.Organisation) ~ AuthorisedAction
              .HasActiveOrganisationEnrolment(enrolmentIdentifier) ~ Some(credentials) =>
          block(AuthorisedRequest(request, affinityGroup, credentials.providerId, enrolmentIdentifier))

        case Some(AffinityGroup.Organisation) ~ _ ~ _ =>
          Future.failed(UnsupportedAffinityGroup("Organisation enrolment missing or not activated"))

        case _ ~ _ ~ None =>
          Future.failed(UnsupportedAuthProvider("No credentials providerId found for user"))

        case _ =>
          Future.failed(UnsupportedAffinityGroup("No affinity group found"))
      }
      .recover { case e: AuthorisationException =>
        Forbidden(s"Unauthorised: ${e.reason}")
      }
  }
}

object AuthorisedAction {
  val organisationEnrolmentKey   = "HMRC-CHAR-ORG"
  val organisationIdentifierName = "CHARID"
  val agentEnrolmentKey          = "HMRC-CHAR-AGENT"
  val agentIdentifierName        = "AGENTCHARID"

  def hasActiveEnrolment(
    enrolments: Enrolments,
    enrolmentKey: String,
    identifierName: String
  ): Option[String] =
    enrolments.getEnrolment(enrolmentKey) match {
      case Some(enrolment) if enrolment.isActivated =>
        enrolment.getIdentifier(identifierName).map(_.value)

      case _ => None
    }

  object HasActiveAgentEnrolment {
    def unapply(enrolments: Enrolments): Option[String] =
      hasActiveEnrolment(enrolments, agentEnrolmentKey, agentIdentifierName)
  }

  object HasActiveOrganisationEnrolment {
    def unapply(enrolments: Enrolments): Option[String] =
      hasActiveEnrolment(enrolments, organisationEnrolmentKey, organisationIdentifierName)
  }
}
