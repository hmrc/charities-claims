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
import uk.gov.hmrc.charitiesclaims.models.{Claim, ClaimData, UpdateClaimRequest, UpdateClaimResponse}
import uk.gov.hmrc.charitiesclaims.services.ClaimsService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID
import uk.gov.hmrc.charitiesclaims.connectors.ClaimsValidationConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.charitiesclaims.models.FileUploadReference

@Singleton()
class UpdateClaimController @Inject() (
  val cc: ControllerComponents,
  val authorisedAction: AuthorisedAction,
  claimsService: ClaimsService,
  claimsValidationConnector: ClaimsValidationConnector
)(using ExecutionContext)
    extends BaseController {

  def updateClaim(claimId: String): Action[String] =
    whenAuthorised {
      withPayload[UpdateClaimRequest] { updateClaimsRequest =>
        claimsService
          .getClaim(claimId)
          .flatMap {
            case None                   =>
              Future.successful(
                NotFound(
                  Json.obj(
                    "errorMessage" -> s"Claim with claimId $claimId not found",
                    "errorCode"    -> "CLAIM_NOT_FOUND_ERROR"
                  )
                )
              )
            case Some(existingClaim, _) =>
              if existingClaim.submissionDetails.isDefined || existingClaim.claimSubmitted
              then {
                Future.successful(
                  BadRequest(
                    Json.obj(
                      "errorMessage" -> s"Claim with claimId $claimId has already been submitted and cannot be updated",
                      "errorCode"    -> "CLAIM_ALREADY_SUBMITTED_ERROR"
                    )
                  )
                )
              } else if existingClaim.lastUpdatedReference != updateClaimsRequest.lastUpdatedReference
              then {
                Future.successful(
                  BadRequest(
                    Json.obj(
                      "errorMessage" -> s"Claim with claimId $claimId has already been updated by another user",
                      "errorCode"    -> "UPDATED_BY_ANOTHER_USER"
                    )
                  )
                )
              } else {
                val updatedClaim = update(existingClaim, updateClaimsRequest)
                for {
                  _ <- claimsService.putClaim(updatedClaim)
                  _ <- deleteUploadResults(existingClaim, updatedClaim)
                } yield Ok(
                  Json.toJson(
                    UpdateClaimResponse(
                      success = true,
                      lastUpdatedReference = updatedClaim.lastUpdatedReference
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

  private def deleteUploadResults(claim: Claim, updatedClaim: Claim)(using
    HeaderCarrier,
    ExecutionContext
  ): Future[Seq[Boolean]] =
    Future.sequence(
      findUploadsToDelete(claim, updatedClaim)
        .map { uploadReference =>
          claimsValidationConnector.deleteUpload(claim.claimId, uploadReference)
        }
    )

  private def findUploadsToDelete(existingClaim: Claim, updatedClaim: Claim): Seq[FileUploadReference] =
    (existingClaim.uploadReferences -- updatedClaim.uploadReferences).toSeq

  private def update(claim: Claim, update: UpdateClaimRequest): Claim = {
    val newClaimData = ClaimData(
      repaymentClaimDetails = update.repaymentClaimDetails,
      organisationDetails = update.organisationDetails,
      giftAidSmallDonationsSchemeDonationDetails = update.giftAidSmallDonationsSchemeDonationDetails,
      understandFalseStatements = update.understandFalseStatements,
      includedAnyAdjustmentsInClaimPrompt = update.includedAnyAdjustmentsInClaimPrompt,
      giftAidScheduleFileUploadReference = update.giftAidScheduleFileUploadReference,
      otherIncomeScheduleFileUploadReference = update.otherIncomeScheduleFileUploadReference,
      communityBuildingsScheduleFileUploadReference = update.communityBuildingsScheduleFileUploadReference,
      connectedCharitiesScheduleFileUploadReference = update.connectedCharitiesScheduleFileUploadReference
    )

    claim.copy(claimData = newClaimData, lastUpdatedReference = UUID.randomUUID().toString)
  }
}
