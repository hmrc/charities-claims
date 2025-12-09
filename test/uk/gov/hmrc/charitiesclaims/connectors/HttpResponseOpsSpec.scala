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

import play.api.libs.json.{JsError, JsObject, JsString, Json, Reads}
import uk.gov.hmrc.charitiesclaims.connectors.HttpResponseOps.*
import uk.gov.hmrc.charitiesclaims.util.BaseSpec
import uk.gov.hmrc.http.HttpResponse

class HttpResponseOpsSpec extends BaseSpec {

  case class TestResponse(foo: String)
  given Reads[TestResponse] = Json.reads[TestResponse]

  "HttpResponseOps" - {
    "parseJSON" - {
      "should return a Right if the JSON is valid" in {
        val response = HttpResponse(200, "{\"foo\":\"bar\"}")
        response.parseJSON[JsObject](None) shouldBe Right(JsObject(Seq("foo" -> JsString("bar"))))
      }
      "should return deserialised entity if the JSON is valid" in {
        val response = HttpResponse(200, "{\"foo\":\"bar\"}")
        response.parseJSON[TestResponse]() shouldBe Right(TestResponse("bar"))
      }
      "should return a Right if the JSON is valid and the path is reachable" in {
        val response = HttpResponse(200, "{\"foo\":\"bar\"}")
        response.parseJSON[JsString](Some("foo")) shouldBe Right(JsString("bar"))
      }
      "should return a Left if the JSON is invalid" in {
        val response = HttpResponse(200, "{\"foo\":\"bar\"")
        response.parseJSON[JsObject](None).left.value should include("could not read http response as JSON")
      }
      "should return a Left if the path is not reachable" in {
        val response = HttpResponse(200, "{\"foo\":\"bar\"}")
        response.parseJSON[JsString](Some("bar")).left.value should include("no JSON found in body of http response")
      }
      "should return a Left if the entity is invalid" in {
        val response = HttpResponse(200, "{\"bar\":\"foo\"}")
        response.parseJSON[TestResponse]().left.value should include("could not parse http response JSON")
      }
    }
  }
}
