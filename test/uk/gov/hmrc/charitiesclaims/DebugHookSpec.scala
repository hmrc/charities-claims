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

package uk.gov.hmrc.charitiesclaims

import uk.gov.hmrc.http.hooks.*
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.charitiesclaims.util.ControllerSpec

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

import java.net.URL

class DebugHookSpec extends ControllerSpec {

  given HeaderCarrier = HeaderCarrier()

  "DebugHook" - {

    class DummyDebugHook(config: Configuration) extends DebuggingHook(config)

    "should respect debugOutboundRequests=false and do nothing" in {
      val conf = Configuration("debugOutboundRequests" -> false)
      val hook = new DummyDebugHook(conf)

      val responseData = ResponseData(Data("{}", false, false), 200, Map("foo" -> Seq("bar")))
      val request      = RequestData(Seq.empty, None)

      noException should be thrownBy
        hook.apply("GET", new URL("http://localhost/path"), request, Future.successful(responseData))
    }

    "should respect debugOutboundRequests=true and log the request and response when hook data is a string" in {
      val conf = Configuration("debugOutboundRequests" -> true)
      val hook = new DummyDebugHook(conf)

      val responseData = ResponseData(Data("{}", false, false), 200, Map("foo" -> Seq("bar")))
      val request      = RequestData(Seq("foo" -> "bar"), Some(Data(HookData.FromString("{}"), false, false)))

      noException should be thrownBy
        hook.apply("GET", new URL("http://localhost/path"), request, Future.successful(responseData))

    }

    "should respect debugOutboundRequests=true and log the request and response when hook data is a map" in {
      val conf = Configuration("debugOutboundRequests" -> true)
      val hook = new DummyDebugHook(conf)

      val responseData = ResponseData(Data("{}", false, false), 200, Map("foo" -> Seq("bar")))
      val request      =
        RequestData(Seq("foo" -> "bar"), Some(Data(HookData.FromMap(Map("foo" -> Seq("bar"))), false, false)))

      noException should be thrownBy
        hook.apply("GET", new URL("http://localhost/path"), request, Future.successful(responseData))

    }

    "should log failed response" in {
      val conf    = Configuration("debugOutboundRequests" -> true)
      val hook    = new DummyDebugHook(conf)
      val request = RequestData(Seq.empty, None)

      noException should be thrownBy
        hook.apply("GET", new URL("http://localhost/path"), request, Future.failed(new Exception("An example failure")))
    }
  }

}
