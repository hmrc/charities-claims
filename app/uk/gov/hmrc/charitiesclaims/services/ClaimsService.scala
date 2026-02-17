/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.charitiesclaims.services

import com.google.inject.ImplementedBy
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Projections.*
import uk.gov.hmrc.charitiesclaims.connectors.ClaimsValidationConnector
import uk.gov.hmrc.charitiesclaims.models.{Claim, ClaimInfo}
import uk.gov.hmrc.charitiesclaims.repositories.ClaimsRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ClaimsServiceImpl])
trait ClaimsService {

  def putClaim(claim: Claim): Future[Instant]
  def getClaim(claimId: String): Future[Option[(Claim, Instant)]]
  def deleteClaim(claimId: String)(using HeaderCarrier): Future[Unit]
  def listClaims(userId: String, claimSubmitted: Boolean): Future[Seq[ClaimInfo]]

}

@Singleton
class ClaimsServiceImpl @Inject() (
  repository: ClaimsRepository,
  claimsValidationConnector: ClaimsValidationConnector
)(using
  ExecutionContext
) extends ClaimsService {

  def putClaim(claim: Claim): Future[Instant] =
    repository.getWithCreatedAt(claim.claimId)(ClaimsRepository.claimDataKey).flatMap {
      case Some(existingClaim, createdAt) if existingClaim == claim =>
        Future.successful(createdAt)

      case _ =>
        repository
          .put(claim.claimId)(ClaimsRepository.claimDataKey, claim)
          .map(_.createdAt)
    }

  def getClaim(claimId: String): Future[Option[(Claim, Instant)]] =
    repository.getWithCreatedAt(claimId)(ClaimsRepository.claimDataKey)

  def deleteClaim(claimId: String)(using HeaderCarrier): Future[Unit] =
    claimsValidationConnector
      .deleteClaim(claimId)
      .flatMap(_ => repository.delete(claimId)(ClaimsRepository.claimDataKey))

  def listClaims(userId: String, claimSubmitted: Boolean): Future[Seq[ClaimInfo]] =
    repository.collection
      .withDocumentClass[ClaimsRepository.CacheItemWithClaimInfo]
      .find(BsonDocument(ClaimsRepository.userIdPath -> userId, ClaimsRepository.claimSubmittedPath -> claimSubmitted))
      .projection(
        fields(
          include(
            ClaimsRepository.claimIdPath,
            ClaimsRepository.userIdPath,
            ClaimsRepository.lastUpdatedReferencePath,
            ClaimsRepository.claimSubmittedPath,
            ClaimsRepository.hmrcCharitiesReferencePath,
            ClaimsRepository.nameOfCharityPath
          )
        )
      )
      .map(_.data.claim.flatten)
      .collect()
      .head()
}
