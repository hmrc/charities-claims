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

import play.api.mvc.*
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.charitiesclaims.util.BaseSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.auth.core.MissingBearerToken

class AuthorisedActionSpec extends BaseSpec {

  class Harness(authorisedAction: AuthorisedAction) {
    val onPageLoad: Action[AnyContent] = authorisedAction { request =>
      Results.Ok(request.affinityGroup.toString())
    }
  }

  val bodyParser: BodyParsers.Default = BodyParsers.Default(Helpers.stubPlayBodyParsers)

  "AuthorisedAction" - {
    "return 403 when user has an Individual affinity group" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup]])(using _: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(
          Future.successful(
            Some(AffinityGroup.Individual)
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector, bodyParser)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result)          must be(FORBIDDEN)
      contentAsString(result) must be("Unsupported affinity group: Individual")
    }

    "create AuthorisedRequest when user has an Organisation affinity group" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup]])(using _: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(
          Future.successful(
            Some(AffinityGroup.Organisation)
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector, bodyParser)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result)          must be(OK)
      contentAsString(result) must be("Organisation")
    }

    "create AuthorisedRequest when user has an Agent affinity group" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup]])(using _: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(
          Future.successful(
            Some(AffinityGroup.Agent)
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector, bodyParser)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result)          must be(OK)
      contentAsString(result) must be("Agent")
    }

    "return 403 when user has no affinity group" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup]])(using _: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(
          Future.successful(
            None
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector, bodyParser)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result) must be(FORBIDDEN)
    }

    "return 403 when AuthorisationException is thrown" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup]])(using _: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returns(Future.failed(new MissingBearerToken("Missing bearer token")))

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector, bodyParser)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result) must be(FORBIDDEN)
    }

  }
}
