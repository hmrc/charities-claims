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
import uk.gov.hmrc.charitiesclaims.models.ChRISSubmissionRequest
import uk.gov.hmrc.charitiesclaims.services.{ChRISSubmissionService, ClaimsService}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.charitiesclaims.connectors.ChRISConnector
import uk.gov.hmrc.charitiesclaims.models.ChRISSubmissionResponse
import uk.gov.hmrc.charitiesclaims.models.SubmissionDetails

@Singleton()
class ChRISSubmissionController @Inject() (
  val cc: ControllerComponents,
  val authorisedAction: AuthorisedAction,
  claimsService: ClaimsService,
  chrisSubmissionService: ChRISSubmissionService,
  chrisConnector: ChRISConnector
)(using ExecutionContext)
    extends BaseController {

  val submitClaim: Action[String] =
    whenAuthorised {
      withPayload[ChRISSubmissionRequest] { chrisSubmissionRequest =>
        claimsService
          .getClaim(chrisSubmissionRequest.claimId)
          .flatMap {
            case None        =>
              Future.successful(
                NotFound(
                  Json.obj(
                    "errorMessage" -> s"Claim with claimId ${chrisSubmissionRequest.claimId} not found",
                    "errorCode"    -> "CLAIM_NOT_FOUND_ERROR"
                  )
                )
              )
            case Some(claim) =>
              if claim.submissionDetails.isDefined || claim.claimSubmitted
              then
                Future.successful(
                  BadRequest(
                    Json.obj(
                      "errorMessage" -> s"Claim with claimId ${chrisSubmissionRequest.claimId} has already been submitted to ChRIS",
                      "errorCode"    -> "CLAIM_ALREADY_SUBMITTED_ERROR"
                    )
                  )
                )
              else if chrisSubmissionRequest.lastUpdatedReference != claim.lastUpdatedReference then {
                Future.successful(
                  BadRequest(
                    Json.obj(
                      "errorMessage" -> s"Claim with claimId ${chrisSubmissionRequest.claimId} has already been updated by another user",
                      "errorCode"    -> "UPDATED_BY_ANOTHER_USER"
                    )
                  )
                )
              } else
                chrisSubmissionService
                  .buildChRISSubmission(claim, currentUser)
                  .flatMap { govTalkMessage =>
                    chrisConnector
                      .submitClaim(govTalkMessage)
                      .flatMap { _ =>
                        val submissionTimestamp = ISODateTime.timestampNow()
                        claimsService
                          .putClaim(
                            claim.copy(
                              claimSubmitted = true,
                              submissionDetails = Some(
                                SubmissionDetails(
                                  submissionTimestamp = submissionTimestamp,
                                  submissionReference = chrisSubmissionRequest.lastUpdatedReference
                                )
                              )
                            )
                          )
                          .map { _ =>
                            Ok(
                              Json.toJson(
                                ChRISSubmissionResponse(
                                  success = true,
                                  submissionTimestamp = submissionTimestamp,
                                  submissionReference = chrisSubmissionRequest.lastUpdatedReference
                                )
                              )
                            )
                          }
                          .recover { case e =>
                            InternalServerError(
                              Json.obj(
                                "errorMessage" -> s"ChRIS submission was successful but cannot update claim with claimId ${chrisSubmissionRequest.claimId} because of ${e.getClass.getName}: ${e.getMessage}",
                                "errorCode"    -> "CLAIM_SERVICE_ERROR"
                              )
                            )
                          }
                      }
                  }
                  .recover { case e =>
                    InternalServerError(
                      Json.obj(
                        "errorMessage" -> e.getMessage,
                        "errorCode"    -> "CHRIS_SUBMISSION_ERROR"
                      )
                    )
                  }
          }
          .recover { case e =>
            InternalServerError(
              Json.obj(
                "errorMessage" -> s"Cannot get claim with claimId ${chrisSubmissionRequest.claimId} because of ${e.getClass.getName}: ${e.getMessage}",
                "errorCode"    -> "CLAIM_SERVICE_ERROR"
              )
            )
          }
      }
    }

}
