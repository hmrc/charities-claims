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
import play.api.mvc.Results.{BadRequest, InternalServerError, Ok}
import play.api.mvc.{Action, ControllerComponents, Result}
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.charitiesclaims.config.AppConfig
import uk.gov.hmrc.charitiesclaims.controllers.actions.AuthorisedAction
import uk.gov.hmrc.charitiesclaims.models.requests.AuthorisedRequest
import uk.gov.hmrc.charitiesclaims.models.{Claim, ClaimData, RepaymentClaimDetails, SaveClaimRequest, SaveClaimResponse}
import uk.gov.hmrc.charitiesclaims.services.ClaimsService

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class SaveClaimController @Inject() (
  val cc: ControllerComponents,
  val authorisedAction: AuthorisedAction,
  claimsService: ClaimsService,
  appConfig: AppConfig
)(using ExecutionContext)
    extends BaseController {

  val saveClaim: Action[String] =
    whenAuthorised {
      withPayload[SaveClaimRequest] { saveClaimRequest =>
        currentUserGroup
          .match {
            case AffinityGroup.Agent =>
              claimsService
                .listClaims(currentUserId, claimSubmitted = false)
                .flatMap {
                  case claims if claims.size < appConfig.agentUnsubmittedClaimLimit =>
                    saveClaim(saveClaimRequest)

                  case claims =>
                    Future.successful(
                      BadRequest(
                        Json.obj(
                          "errorMessage" -> s"Agent already has ${claims.size} unsubmitted claims where the limit is set to ${appConfig.agentUnsubmittedClaimLimit}",
                          "errorCode"    -> "UNSUBMITTED_CLAIMS_LIMIT_EXCEEDED"
                        )
                      )
                    )
                }

            case _ =>
              claimsService
                .listClaims(currentUserId, claimSubmitted = false)
                .flatMap {
                  case claims if claims.size == 0 =>
                    saveClaim(saveClaimRequest)

                  case _ =>
                    Future.successful(
                      BadRequest(
                        Json.obj(
                          "errorMessage" -> "Organisation has an unsubmitted claim, no more claims can be submitted",
                          "errorCode"    -> "UNSUBMITTED_CLAIMS_LIMIT_EXCEEDED"
                        )
                      )
                    )
                }
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

  private def saveClaim(saveClaimRequest: SaveClaimRequest)(using AuthorisedRequest[?]): Future[Result] = {
    val claimId              = UUID.randomUUID().toString
    val lastUpdatedReference = UUID.randomUUID().toString

    val claim = Claim(
      claimId = claimId,
      userId = currentUserId,
      claimSubmitted = false,
      lastUpdatedReference = lastUpdatedReference,
      claimData = ClaimData(
        repaymentClaimDetails = RepaymentClaimDetails(
          claimingGiftAid = saveClaimRequest.claimingGiftAid,
          claimingTaxDeducted = saveClaimRequest.claimingTaxDeducted,
          claimingUnderGiftAidSmallDonationsScheme = saveClaimRequest.claimingUnderGiftAidSmallDonationsScheme,
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
      .map(createdAt => Ok(Json.toJson(SaveClaimResponse(claimId, createdAt, lastUpdatedReference))))
  }
}
