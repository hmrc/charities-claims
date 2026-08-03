/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.charitiesclaims.jobs

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier
import play.api.Logger
import uk.gov.hmrc.charitiesclaims.config.AppConfig
import uk.gov.hmrc.charitiesclaims.repositories.ClaimsRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeleteClaimsJob @Inject() (
  repository: ClaimsRepository,
  appConfig: AppConfig
)(using ec: ExecutionContext) {

  private val logger = Logger(getClass)

  private given hc: HeaderCarrier = HeaderCarrier()

  logger.info("DeleteClaimsJob initialised")

  private val started: Future[Unit] =
    runOnStartup()

  private def runOnStartup(): Future[Unit] = {
    logger.info("Running delete claims startup job")

    Future
      .traverse(appConfig.claimIdsToBeDeleted) { claimId =>
        repository.findById(claimId).map {
          case Some(value) =>
            repository
              .deleteEntity(claimId)
              .map { _ =>
                logger.info(s"Deletion of claim $claimId completed successfully")
              }
              .recover { case ex =>
                logger.error(s"Deletion of claim $claimId failed", ex)
              }
          case None        =>
            logger.info(s"Claim $claimId not found")
        }
      }
      .map(_ => ())
  }

}
