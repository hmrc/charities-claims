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

import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Results.*
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.charitiesclaims.controllers.actions.AuthorisedAction
import uk.gov.hmrc.charitiesclaims.models.{ChRISSubmissionRequest, Claim}
import uk.gov.hmrc.charitiesclaims.services.{AuditService, ChRISSubmissionService, ClaimsService, MissingCharityReferenceException, UnregulatedDonationException, UnregulatedDonationsService}
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import play.api.mvc.Result

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.charitiesclaims.connectors.ChRISConnector
import uk.gov.hmrc.charitiesclaims.models.ChRISSubmissionResponse
import uk.gov.hmrc.charitiesclaims.models.SubmissionDetails
import uk.gov.hmrc.charitiesclaims.validation.SchematronValidationException

@Singleton()
class ChRISSubmissionController @Inject() (
  val cc: ControllerComponents,
  val authorisedAction: AuthorisedAction,
  claimsService: ClaimsService,
  chrisSubmissionService: ChRISSubmissionService,
  chrisConnector: ChRISConnector,
  unregulatedDonationsService: UnregulatedDonationsService,
  auditService: AuditService
)(using ExecutionContext)
    extends BaseController {

  private val logger = Logger(getClass)

  private def logAndFail(message: String, code: String, e: Throwable): Result = {
    logger.error(message, e)
    InternalServerError(
      Json.obj(
        "errorMessage" -> s"$message because of ${e.getClass.getName}: ${e.getMessage}",
        "errorCode"    -> code
      )
    )
  }

  private def handleAuditResult(result: Future[AuditResult], claimId: String, userId: String): Future[Unit] =
    result
      .flatMap {
        case AuditResult.Success => Future.unit
        case _                   =>
          logger.warn(s"Chris submission audit failed: claimId=$claimId, userId=$userId")
          Future.unit
      }
      .recover { case e =>
        logger.warn(s"Audit call failed: claimId=$claimId, userId=$userId", e)
      }

  private def updateClaim(
    claim: Claim,
    chrisSubmissionRequest: ChRISSubmissionRequest,
    submissionTimestamp: String
  ): Future[Result] =
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
        logger.info(
          s"ChRIS submission complete: claimId=${claim.claimId} submissionTimestamp=$submissionTimestamp"
        )
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
        logAndFail(
          s"ChRIS submission was successful but cannot update claim with claimId ${claim.claimId}",
          "CLAIM_SERVICE_ERROR",
          e
        )
      }

  val submitClaim: Action[String] =
    whenAuthorised {
      withPayload[ChRISSubmissionRequest] { chrisSubmissionRequest =>
        claimsService
          .getClaim(chrisSubmissionRequest.claimId)
          .flatMap {
            case None           =>
              logger.warn(s"Claim not found: claimId=${chrisSubmissionRequest.claimId}")
              Future.successful(
                NotFound(
                  Json.obj(
                    "errorMessage" -> s"Claim with claimId ${chrisSubmissionRequest.claimId} not found",
                    "errorCode"    -> "CLAIM_NOT_FOUND_ERROR"
                  )
                )
              )
            case Some(claim, _) =>
              if claim.submissionDetails.isDefined || claim.claimSubmitted
              then {
                logger.warn(s"Claim already submitted: claimId=${chrisSubmissionRequest.claimId}")
                Future.successful(
                  BadRequest(
                    Json.obj(
                      "errorMessage" -> s"Claim with claimId ${chrisSubmissionRequest.claimId} has already been submitted to ChRIS",
                      "errorCode"    -> "CLAIM_ALREADY_SUBMITTED_ERROR"
                    )
                  )
                )
              } else if chrisSubmissionRequest.lastUpdatedReference != claim.lastUpdatedReference then {
                logger.warn(s"Conflict on submission: claimId=${chrisSubmissionRequest.claimId}")
                Future.successful(
                  BadRequest(
                    Json.obj(
                      "errorMessage" -> s"Claim with claimId ${chrisSubmissionRequest.claimId} has already been updated by another user",
                      "errorCode"    -> "UPDATED_BY_ANOTHER_USER"
                    )
                  )
                )
              } else {
                val claimId = chrisSubmissionRequest.claimId

                logger.info(s"Submitting claim to ChRIS: claimId=$claimId")

                val chrisSubmissionFlow = for {
                  govTalkMessage <- chrisSubmissionService.buildChRISSubmission(
                                      claim,
                                      currentUser,
                                      chrisSubmissionRequest.declarationLanguage
                                    )

                  _ <- chrisConnector.submitClaim(govTalkMessage)

                  _ <- handleAuditResult(auditService.sendEvent(claim), claimId, claim.userId)

                  _ <- unregulatedDonationsService.recordUnregulatedDonation(claim, currentUser)

                  result <- updateClaim(claim, chrisSubmissionRequest, ISODateTime.timestampNow())

                } yield result

                chrisSubmissionFlow.recover {
                  case SchematronValidationException(errors) =>
                    logger.warn(
                      s"Schematron validation failed: claimId=$claimId errors=${errors.size}"
                    )
                    BadRequest(
                      Json.obj(
                        "errorMessage" -> s"Schematron validation failed with ${errors.size} error(s)",
                        "errorCode"    -> "SCHEMATRON_VALIDATION_ERROR",
                        "errors"       -> Json.toJson(errors)
                      )
                    )

                  case e: MissingCharityReferenceException =>
                    logAndFail(
                      s"Cannot record unregulated donation: no charity reference available for claimId=$claimId",
                      "UNREGULATED_DONATION_ERROR",
                      e
                    )

                  case e: UnregulatedDonationException =>
                    logAndFail(
                      s"ChRIS submission was successful but cannot record unregulated donation for claimId $claimId",
                      "UNREGULATED_DONATION_ERROR",
                      e
                    )

                  case e =>
                    logger.error(s"ChRIS submission failed: claimId=$claimId", e)
                    InternalServerError(
                      Json.obj(
                        "errorMessage" -> e.getMessage,
                        "errorCode"    -> "CHRIS_SUBMISSION_ERROR"
                      )
                    )
                }
              }
          }
          .recover { case e =>
            logger.error(s"Failed to retrieve claim: claimId=${chrisSubmissionRequest.claimId}", e)
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
