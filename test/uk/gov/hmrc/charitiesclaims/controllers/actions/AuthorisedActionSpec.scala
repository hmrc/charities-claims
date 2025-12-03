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
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.charitiesclaims.util.BaseSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.auth.core.EnrolmentIdentifier

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.auth.core.MissingBearerToken

class AuthorisedActionSpec extends BaseSpec {
  class Harness(authorisedAction: AuthorisedAction) {
    val onPageLoad: Action[String] = authorisedAction { request =>
      Results.Ok(s"${request.affinityGroup},${request.userId},${request.enrolmentIdentifier}")
    }
  }

  "AuthorisedAction" - {
    "return 403 when user has an Individual affinity group" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .anyNumberOfTimes()
        .returning(
          Future.successful(
            `~`(
              `~`(Some(AffinityGroup.Individual), Enrolments(Set.empty)),
              Some(Credentials("providerId", "providerType"))
            )
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result)          shouldBe FORBIDDEN
      contentAsString(result) shouldBe "Unauthorised: No affinity group found"
    }

    "create AuthorisedRequest when user has an Organisation affinity group with active enrolment" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .anyNumberOfTimes()
        .returning(
          Future.successful(
            `~`(
              `~`(
                Some(AffinityGroup.Organisation),
                Enrolments(
                  Set(Enrolment("HMRC-CHAR-ORG", Seq(EnrolmentIdentifier("CHARID", "1234567890")), "Activated"))
                )
              ),
              Some(Credentials("providerId", "providerType"))
            )
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result)        shouldBe OK
      contentAsString(result) should include("Organisation")
      contentAsString(result) should include("1234567890")
      contentAsString(result) should include("providerId")
    }

    "create AuthorisedRequest when user has an Agent affinity group with active enrolment" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .anyNumberOfTimes()
        .returning(
          Future.successful(
            `~`(
              `~`(
                Some(AffinityGroup.Agent),
                Enrolments(
                  Set(Enrolment("HMRC-CHAR-AGENT", Seq(EnrolmentIdentifier("AGENTCHARID", "1234567890")), "Activated"))
                )
              ),
              Some(Credentials("providerId", "providerType"))
            )
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result)        shouldBe OK
      contentAsString(result) should include("Agent")
      contentAsString(result) should include("1234567890")
      contentAsString(result) should include("providerId")
    }

    "return 403 when user has no affinity group" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .returning(
          Future.successful(
            `~`(`~`(None, Enrolments(Set.empty)), Some(Credentials("providerId", "providerType")))
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result) shouldBe FORBIDDEN
    }

    "return 403 when user has no credentials" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .returning(
          Future.successful(
            `~`(
              `~`(Some(AffinityGroup.Agent), Enrolments(Set.empty)),
              None
            )
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result) shouldBe FORBIDDEN
    }

    "return 403 when user has no credentials and no affinity group" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .returning(
          Future.successful(
            `~`(
              `~`(None, Enrolments(Set.empty)),
              None
            )
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result) shouldBe FORBIDDEN
    }

    "return 403 when AuthorisationException is thrown" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .returns(Future.failed(new MissingBearerToken("Missing bearer token")))

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result) shouldBe FORBIDDEN
    }

    "return 403 when Agent has no enrolment" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .anyNumberOfTimes()
        .returning(
          Future.successful(
            `~`(
              `~`(Some(AffinityGroup.Agent), Enrolments(Set.empty)),
              Some(Credentials("providerId", "providerType"))
            )
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result)          shouldBe FORBIDDEN
      contentAsString(result) shouldBe "Unauthorised: Agent enrolment missing or not activated"
    }

    "return 403 when Agent has inactive enrolment" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .anyNumberOfTimes()
        .returning(
          Future.successful(
            `~`(
              `~`(
                Some(AffinityGroup.Agent),
                Enrolments(
                  Set(
                    Enrolment("HMRC-CHAR-AGENT", Seq(EnrolmentIdentifier("AGENTCHARID", "AGENT123")), "NotActivated")
                  )
                )
              ),
              Some(Credentials("providerId", "providerType"))
            )
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result)          shouldBe FORBIDDEN
      contentAsString(result) shouldBe "Unauthorised: Agent enrolment missing or not activated"
    }

    "return 403 when Agent has enrolment but missing identifier" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .anyNumberOfTimes()
        .returning(
          Future.successful(
            `~`(
              `~`(
                Some(AffinityGroup.Agent),
                Enrolments(
                  Set(Enrolment("HMRC-CHAR-AGENT", Seq.empty, "Activated"))
                )
              ),
              Some(Credentials("providerId", "providerType"))
            )
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result)          shouldBe FORBIDDEN
      contentAsString(result) shouldBe "Unauthorised: Agent enrolment missing or not activated"
    }

    "return 403 when Organisation has no enrolment" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .anyNumberOfTimes()
        .returning(
          Future.successful(
            `~`(
              `~`(Some(AffinityGroup.Organisation), Enrolments(Set.empty)),
              Some(Credentials("providerId", "providerType"))
            )
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result)          shouldBe FORBIDDEN
      contentAsString(result) shouldBe "Unauthorised: Organisation enrolment missing or not activated"
    }

    "return 403 when Organisation has inactive enrolment" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .anyNumberOfTimes()
        .returning(
          Future.successful(
            `~`(
              `~`(
                Some(AffinityGroup.Organisation),
                Enrolments(
                  Set(Enrolment("HMRC-CHAR-ORG", Seq(EnrolmentIdentifier("CHARID", "1234567890")), "NotActivated"))
                )
              ),
              Some(Credentials("providerId", "providerType"))
            )
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result)          shouldBe FORBIDDEN
      contentAsString(result) shouldBe "Unauthorised: Organisation enrolment missing or not activated"
    }

    "return 403 when Organisation has enrolment but missing identifier" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments ~ Option[Credentials]])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .anyNumberOfTimes()
        .returning(
          Future.successful(
            `~`(
              `~`(
                Some(AffinityGroup.Organisation),
                Enrolments(
                  Set(Enrolment("HMRC-CHAR-ORG", Seq.empty, "Activated"))
                )
              ),
              Some(Credentials("providerId", "providerType"))
            )
          )
        )

      val authorisedAction =
        new DefaultAuthorisedAction(mockAuthConnector)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result)          shouldBe FORBIDDEN
      contentAsString(result) shouldBe "Unauthorised: Organisation enrolment missing or not activated"
    }
  }
}
