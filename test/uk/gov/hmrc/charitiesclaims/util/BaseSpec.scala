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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Span
import org.scalatest.OptionValues
import org.scalatest.EitherValues

trait BaseSpec
    extends AnyFreeSpec
    with MockFactory
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with EitherValues {

  implicit val actorSystem: ActorSystem = ActorSystem("unit-tests")
  implicit val mat: Materializer        = Materializer.createMaterializer(actorSystem)

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(1000, Millis)), interval = scaled(Span(50, Millis)))
}
