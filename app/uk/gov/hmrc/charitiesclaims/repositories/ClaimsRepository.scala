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

import uk.gov.hmrc.mongo.cache.{CacheIdType, DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}
import uk.gov.hmrc.charitiesclaims.config.AppConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.charitiesclaims.models.Claim
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.IndexOptions

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
        )
      )
    )

object ClaimsRepository {
  val claimDataKey: DataKey[Claim] = DataKey[Claim]("claim")
  val userIdPath: String           = "data.claim.userId"
}
