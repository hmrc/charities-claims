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
import play.api.mvc.Results.*
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.charitiesclaims.controllers.actions.AuthorisedAction
import uk.gov.hmrc.charitiesclaims.models.{Claim, UpdateClaimRequest, UpdateClaimResponse}
import uk.gov.hmrc.charitiesclaims.services.ClaimsService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class UpdateClaimController @Inject() (
  val cc: ControllerComponents,
  val authorisedAction: AuthorisedAction,
  claimsService: ClaimsService
)(using ExecutionContext)
    extends BaseController {

  val updateClaim: Action[String] =
    whenAuthorised {
      withPayload[UpdateClaimRequest] { updateClaimsRequest =>
        claimsService
          .getClaim(updateClaimsRequest.claimId)
          .flatMap {
            case None        =>
              Future.successful(
                NotFound(
                  Json.obj(
                    "errorMessage" -> s"Claim with claimId ${updateClaimsRequest.claimId} not found",
                    "errorCode"    -> "CLAIM_NOT_FOUND_ERROR"
                  )
                )
              )
            case Some(claim) =>
              val updatedClaim = update(claim, updateClaimsRequest)
              claimsService.putClaim(updatedClaim).map(_ => Ok(Json.toJson(UpdateClaimResponse(success = true))))
          }
          .recover { case e =>
            InternalServerError(
              Json.obj(
                "errorMessage" -> e.getMessage,
                "errorCode"    -> "CLAIM_SERVICE_ERROR"
              )
            )
          }
      }
    }

  private def update(claim: Claim, update: UpdateClaimRequest): Claim = {
    val existing = claim.claimData

    val newClaimData = existing.copy(
      repaymentClaimDetails = update.repaymentClaimDetails.getOrElse(existing.repaymentClaimDetails),
      organisationDetails = update.organisationDetails.orElse(existing.organisationDetails),
      declarationDetails = update.declarationDetails.orElse(existing.declarationDetails)
    )

    claim.copy(claimData = newClaimData)
  }
}
