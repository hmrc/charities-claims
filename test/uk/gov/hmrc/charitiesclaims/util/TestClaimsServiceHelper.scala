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

import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.charitiesclaims.models.{Claim, ClaimInfo}
import uk.gov.hmrc.charitiesclaims.services.ClaimsService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

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

class TestClaimsService(initialClaims: Seq[Claim])(using ec: ExecutionContext) extends ClaimsService {

  private val claims: ListBuffer[(Claim, Instant)] =
    ListBuffer.from(initialClaims.map(_ -> Instant.now()))

  override def putClaim(claim: Claim): Future[Instant] = {
    claims.filterInPlace((c, _) => c.claimId != claim.claimId)
    val createdAt = Instant.now()
    claims.append((claim, createdAt))
    Future.successful(createdAt)
  }

  override def getClaim(claimId: String): Future[Option[(Claim, Instant)]] =
    Future.successful(claims.find((c, _) => c.claimId == claimId))

  override def deleteClaim(claimId: String)(using HeaderCarrier): Future[Unit] = {
    claims.filterInPlace((c, _) => c.claimId != claimId)
    Future.successful(())
  }

  override def listClaims(userId: String, claimSubmitted: Boolean): Future[Seq[ClaimInfo]] =
    Future.successful(
      claims.collect {
        case (claim, _) if claim.userId == userId && claim.claimSubmitted == claimSubmitted =>
          ClaimInfo(
            claim.claimId,
            claim.userId,
            claim.claimSubmitted,
            claim.lastUpdatedReference,
            claim.claimData.repaymentClaimDetails.hmrcCharitiesReference,
            claim.claimData.repaymentClaimDetails.nameOfCharity
          )
      }.toSeq
    )
}
