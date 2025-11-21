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

import play.api.libs.json.JsObject
import uk.gov.hmrc.charitiesclaims.models.Claim
import uk.gov.hmrc.charitiesclaims.services.ClaimsService

import scala.concurrent.Future
import scala.collection.mutable.ListBuffer
import play.api.libs.json.Json
import scala.io.Source
import play.api.libs.json.Reads

trait TestClaimsServiceHelper extends TestUsers {

  lazy val testClaimSubmitted: Claim    = readAndParseJson[Claim]("/test-claim-submitted.json")
  lazy val testClaimUnsubmitted1: Claim = readAndParseJson[Claim]("/test-claim-unsubmitted-1.json")
  lazy val testClaimUnsubmitted2: Claim = readAndParseJson[Claim]("/test-claim-unsubmitted-2.json")
  lazy val testClaimUnsubmitted3: Claim = readAndParseJson[Claim]("/test-claim-unsubmitted-3.json")

  lazy val initialTestClaimsSet: Seq[Claim] =
    Seq(
      testClaimSubmitted.copy(userId = organisation1),
      testClaimUnsubmitted1.copy(userId = organisation1),
      testClaimUnsubmitted2.copy(userId = organisation1),
      testClaimUnsubmitted3.copy(userId = organisation1),
      testClaimSubmitted.copy(claimId = "test-claim-submitted-2", userId = agent1),
      testClaimUnsubmitted1.copy(claimId = "test-claim-unsubmitted-1-2", userId = agent1),
      testClaimUnsubmitted2.copy(claimId = "test-claim-unsubmitted-2-2", userId = agent1),
      testClaimUnsubmitted3.copy(claimId = "test-claim-unsubmitted-3-2", userId = agent1)
    )

  def readAndParseJson[T](path: String)(using Reads[T]): T =
    Json.parse(Source.fromInputStream(this.getClass.getResourceAsStream(path)).getLines().mkString("\n")).as[T]

}

class TestClaimsService(initialClaims: Seq[Claim]) extends ClaimsService {

  private val buffer: ListBuffer[Claim] = ListBuffer.from(initialClaims)

  override def putClaim(claim: Claim): Future[Claim] =
    deleteClaim(claim.claimId)
    buffer.append(claim)
    Future.successful(claim)

  override def getClaim(claimId: String): Future[Option[Claim]] =
    Future.successful(buffer.find(_.claimId == claimId))

  override def deleteClaim(claimId: String): Future[Unit] =
    buffer
      .find(_.claimId == claimId)
      .foreach(existingClaim => buffer.remove(buffer.indexOf(existingClaim)))
    Future.successful(())

  override def listClaims(userId: String, claimSubmitted: Boolean): Future[Seq[JsObject]] =
    Future.successful(
      buffer
        .filter(claim => claim.userId == userId && claim.claimSubmitted == claimSubmitted)
        .map(claim => Json.toJson(claim)(using Claim.format).as[JsObject])
        .toSeq
    )
}
