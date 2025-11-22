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

import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.retrieve.~
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

trait ControllerSpec extends BaseSpec with TestUsers {

  def testRequest[A : Writes](method: String, url: String, body: A): FakeRequest[String] =
    FakeRequest[String](
      method = method,
      uri = url,
      headers = Headers(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON),
      body = Json.prettyPrint(Json.toJson(body))
    )

  trait AuthorisedOrganisationFixture {
    val mockAuthConnector: AuthConnector = mock[AuthConnector]

    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Option[Credentials]])(using
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .expects(*, *, *, *)
      .anyNumberOfTimes()
      .returning(
        Future.successful(
          `~`(Some(AffinityGroup.Organisation), Some(Credentials(organisation1, "GovernmentGateway")))
        )
      )

    val authorisedAction =
      new DefaultAuthorisedAction(mockAuthConnector)
  }

  trait AuthorisedAgentFixture {
    val mockAuthConnector: AuthConnector = mock[AuthConnector]

    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Option[Credentials]])(using
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .expects(*, *, *, *)
      .anyNumberOfTimes()
      .returning(
        Future.successful(
          `~`(Some(AffinityGroup.Agent), Some(Credentials(agent1, "GovernmentGateway")))
        )
      )

    val authorisedAction =
      new DefaultAuthorisedAction(mockAuthConnector)
  }

}
