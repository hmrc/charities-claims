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

package uk.gov.hmrc.charitiesclaims.repositories

import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.charitiesclaims.config.AppConfig
import uk.gov.hmrc.charitiesclaims.models.{Claim, ClaimInfo}
import uk.gov.hmrc.mongo.cache.{CacheIdType, DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ClaimsRepository @Inject() (
  mongoComponent: MongoComponent,
  timestampSupport: TimestampSupport,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends MongoCacheRepository(
      mongoComponent = mongoComponent,
      collectionName = "claims",
      ttl = appConfig.mongoDbTTL,
      timestampSupport = timestampSupport,
      replaceIndexes = true,
      cacheIdType = CacheIdType.SimpleCacheId,
      extraIndexes = Seq(
        IndexModel(
          keys = BsonDocument(ClaimsRepository.userIdPath -> 1),
          indexOptions = IndexOptions().unique(false)
        ),
        IndexModel(
          keys = BsonDocument(ClaimsRepository.userIdPath -> 1, ClaimsRepository.claimSubmittedPath -> 1),
          indexOptions = IndexOptions().unique(false)
        )
      ),
      extraCodecs = Seq(
        Codecs.playFormatCodec(ClaimsRepository.CacheItemClaim.format),
        Codecs.playFormatCodec(ClaimsRepository.CacheItemWithClaimInfo.format)
      )
    )

object ClaimsRepository {

  val claimDataKey: DataKey[Claim]       = DataKey[Claim]("claim")
  val userIdPath: String                 = "data.claim.userId"
  val claimSubmittedPath: String         = "data.claim.claimSubmitted"
  val claimIdPath: String                = "data.claim.claimId"
  val lastUpdatedReferencePath: String   = "data.claim.lastUpdatedReference"
  val creationTimestampPath: String      = "data.claim.creationTimestamp"
  val hmrcCharitiesReferencePath: String = "data.claim.claimData.repaymentClaimDetails.hmrcCharitiesReference"
  val nameOfCharityPath: String          = "data.claim.claimData.repaymentClaimDetails.nameOfCharity"

  case class CacheItemClaim(claim: ClaimInfo)

  object CacheItemClaim {
    given format: Format[CacheItemClaim] = Json.format[CacheItemClaim]
  }

  case class CacheItemWithClaimInfo(data: CacheItemClaim)

  object CacheItemWithClaimInfo {
    given format: Format[CacheItemWithClaimInfo] = Json.format[CacheItemWithClaimInfo]
  }

}
