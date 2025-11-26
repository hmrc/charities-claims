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

import uk.gov.hmrc.mongo.cache.CacheItem

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.charitiesclaims.repositories.ClaimsRepository
import com.google.inject.ImplementedBy
import uk.gov.hmrc.charitiesclaims.models.Claim
import org.mongodb.scala.bson.BsonDocument
import play.api.libs.json.JsObject

@ImplementedBy(classOf[ClaimsServiceImpl])
trait ClaimsService {

  def putClaim(claim: Claim): Future[Claim]
  def getClaim(claimId: String): Future[Option[Claim]]
  def deleteClaim(claimId: String): Future[Unit]
  def listClaims(userId: String, claimSubmitted: Boolean): Future[Seq[JsObject]]

}

@Singleton
class ClaimsServiceImpl @Inject() (repository: ClaimsRepository)(using ExecutionContext) extends ClaimsService {

  def putClaim(claim: Claim): Future[Claim] =
    repository.get(claim.claimId)(ClaimsRepository.claimDataKey).flatMap {
      case Some(existingClaim) if claim == existingClaim =>
        Future.successful(existingClaim)

      case _ =>
        repository
          .put(claim.claimId)(ClaimsRepository.claimDataKey, claim)
          .map(cacheItem =>
            cacheItem.data.value
              .get("claim")
              .map(_.as[Claim])
              .get
          )

    }

  def getClaim(claimId: String): Future[Option[Claim]] =
    repository.get(claimId)(ClaimsRepository.claimDataKey)

  def deleteClaim(claimId: String): Future[Unit] =
    repository.delete(claimId)(ClaimsRepository.claimDataKey)

  def listClaims(userId: String, claimSubmitted: Boolean): Future[Seq[JsObject]] =
    repository.collection
      .find(BsonDocument(ClaimsRepository.userIdPath -> userId, ClaimsRepository.claimSubmittedPath -> claimSubmitted))
      .map(cacheItem => cacheItem.data.value.get("claim").get.asInstanceOf[JsObject])
      .collect()
      .head()
}
