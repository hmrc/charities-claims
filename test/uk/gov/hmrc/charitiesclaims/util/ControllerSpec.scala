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

package uk.gov.hmrc.charitiesclaims.util

import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.EnrolmentIdentifier
import uk.gov.hmrc.charitiesclaims.controllers.actions.DefaultAuthorisedAction
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.test.FakeRequest
import play.api.mvc.Headers
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.libs.json.Writes
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty

trait ControllerSpec extends BaseSpec with TestUsers {

  def testRequest[A : Writes](method: String, url: String, body: A): FakeRequest[String] =
    FakeRequest[String](
      method = method,
      uri = url,
      headers = Headers(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON),
      body = Json.prettyPrint(Json.toJson(body))
    )

  def testRequest(method: String, url: String): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(method, url)

  trait AuthorisedOrganisationFixture {
    val mockAuthConnector: AuthConnector = mock[AuthConnector]

    val orgEnrolment = Enrolment(
      key = "HMRC-CHAR-ORG",
      identifiers = Seq(EnrolmentIdentifier("CHARID", "ORG123")),
      state = "Activated"
    )

    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .expects(*, *, *, *)
      .anyNumberOfTimes()
      .returning(
        Future.successful(
          new ~(
            new ~(
              Some(AffinityGroup.Organisation),
              Enrolments(Set(orgEnrolment))
            ),
            Some(Credentials(organisation1, "GovernmentGateway"))
          )
        )
      )

    val authorisedAction =
      new DefaultAuthorisedAction(mockAuthConnector)
  }

  trait AuthorisedAgentFixture {
    val mockAuthConnector: AuthConnector = mock[AuthConnector]

    val agentEnrolment = Enrolment(
      key = "HMRC-CHAR-AGENT",
      identifiers = Seq(EnrolmentIdentifier("AGENTCHARID", "AGENT123")),
      state = "Activated"
    )

    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .expects(*, *, *, *)
      .anyNumberOfTimes()
      .returning(
        Future.successful(
          new ~(
            new ~(
              Some(AffinityGroup.Agent),
              Enrolments(Set(agentEnrolment))
            ),
            Some(Credentials(agent1, "GovernmentGateway"))
          )
        )
      )

    val authorisedAction =
      new DefaultAuthorisedAction(mockAuthConnector)
  }

}
