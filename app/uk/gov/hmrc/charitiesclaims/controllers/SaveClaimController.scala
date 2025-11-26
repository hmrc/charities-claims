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
import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import play.api.mvc.Results.InternalServerError
import play.api.mvc.Results.Ok
import uk.gov.hmrc.charitiesclaims.controllers.actions.AuthorisedAction
import uk.gov.hmrc.charitiesclaims.models.Claim
import uk.gov.hmrc.charitiesclaims.models.ClaimData
import uk.gov.hmrc.charitiesclaims.models.RepaymentClaimDetails
import uk.gov.hmrc.charitiesclaims.models.SaveClaimRequest
import uk.gov.hmrc.charitiesclaims.models.SaveClaimResponse
import uk.gov.hmrc.charitiesclaims.services.ClaimsService

import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton()
class SaveClaimController @Inject() (
  val cc: ControllerComponents,
  val authorisedAction: AuthorisedAction,
  claimsService: ClaimsService
)(using ExecutionContext)
    extends BaseController {

  val saveClaim: Action[String] =
    whenAuthorised {
      withPayload[SaveClaimRequest] { saveClaimRequest =>
        val claimId           = UUID.randomUUID().toString
        val creationTimestamp = LocalDateTime.now().toString
        val claim             = Claim(
          claimId = claimId,
          userId = currentUserId,
          claimSubmitted = false,
          creationTimestamp = creationTimestamp,
          claimData = ClaimData(
            repaymentClaimDetails = RepaymentClaimDetails(
              claimingGiftAid = saveClaimRequest.claimingGiftAid,
              claimingTaxDeducted = saveClaimRequest.claimingTaxDeducted,
              claimingUnderGasds = saveClaimRequest.claimingUnderGasds,
              claimReferenceNumber = saveClaimRequest.claimReferenceNumber,
              claimingDonationsNotFromCommunityBuilding = saveClaimRequest.claimingDonationsNotFromCommunityBuilding,
              claimingDonationsCollectedInCommunityBuildings =
                saveClaimRequest.claimingDonationsCollectedInCommunityBuildings,
              connectedToAnyOtherCharities = saveClaimRequest.connectedToAnyOtherCharities,
              makingAdjustmentToPreviousClaim = saveClaimRequest.makingAdjustmentToPreviousClaim
            )
          )
        )

        claimsService
          .putClaim(claim)
          .map(claim => Ok(Json.toJson(SaveClaimResponse(claimId, creationTimestamp))))
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
}
