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

package uk.gov.hmrc.charitiesclaims.controllers

import play.api.libs.json.Json
import play.api.mvc.Results.{BadRequest, InternalServerError, NotFound, Ok}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.charitiesclaims.controllers.actions.AuthorisedAction
import uk.gov.hmrc.charitiesclaims.models.*
import uk.gov.hmrc.charitiesclaims.services.{ClaimsService, SubmissionSummaryService}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class GetSubmissionSummaryController @Inject() (
  val cc: ControllerComponents,
  val authorisedAction: AuthorisedAction,
  claimsService: ClaimsService,
  submissionSummaryService: SubmissionSummaryService
)(using ExecutionContext)
    extends BaseController {

  def getSummary(claimId: String): Action[String] =
    whenAuthorised {
      claimsService
        .getClaim(claimId)
        .flatMap {
          case None =>
            Future.successful(
              NotFound(
                Json.obj(
                  "errorMessage" -> s"Claim with claimId $claimId not found",
                  "errorCode"    -> "CLAIM_NOT_FOUND_ERROR"
                )
              )
            )

          case Some((claim, _)) if claim.submissionDetails.isEmpty =>
            Future.successful(
              BadRequest(
                Json.obj(
                  "errorMessage" -> s"Claim with claimId $claimId is not yet submitted",
                  "errorCode"    -> "CLAIM_NOT_SUBMITTED_ERROR"
                )
              )
            )

          case Some((claim, _)) =>
            submissionSummaryService
              .getSummary(claim, currentUser)
              .map(summary => Ok(Json.toJson(summary)))
        }
        .recover { case e: Exception =>
          InternalServerError(
            Json.obj(
              "errorMessage" -> e.getMessage,
              "errorCode"    -> "INTERNAL_SERVER_ERROR"
            )
          )
        }
    }
}
