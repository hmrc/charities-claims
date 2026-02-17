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

package uk.gov.hmrc.charitiesclaims.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.io.Source

class GetClaimResponseSpec extends AnyWordSpec with Matchers {

  "GetClaimResponse" should {
    "be serialised and deserialised correctly" in {

      val json =
        Source
          .fromInputStream(this.getClass.getResourceAsStream("/get-claims-response.json"))
          .getLines()
          .mkString("\n")

      val getClaimsResponse = Json.parse(json).as[GetClaimsResponse]

      Json.parse(Json.prettyPrint(Json.toJson(getClaimsResponse))).as[GetClaimsResponse] shouldBe getClaimsResponse

      getClaimsResponse.claimsCount             shouldBe 1
      getClaimsResponse.claimsList.head.claimId shouldBe "123"
    }
  }
}
