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

import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.mvc.{Action, ControllerComponents}
import play.api.mvc.Results.InternalServerError
import play.api.mvc.Results.{NotFound, Ok}
import uk.gov.hmrc.charitiesclaims.controllers.actions.AuthorisedAction
import uk.gov.hmrc.charitiesclaims.services.ClaimsService

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.charitiesclaims.models.ClaimInfo

@Singleton()
class GetClaimsController @Inject() (
  val cc: ControllerComponents,
  val authorisedAction: AuthorisedAction,
  claimsService: ClaimsService
)(using ExecutionContext)
    extends BaseController {

  def getClaims(claimSubmitted: Boolean): Action[String] =
    whenAuthorised {
      claimsService
        .listClaims(currentUserId, claimSubmitted)
        .map(claims =>
          Ok(
            Json.obj(
              "claimsCount" -> claims.size,
              "claimsList"  -> JsArray(claims.map(claim => Json.toJson(claim)(using ClaimInfo.format)))
            )
          )
        )
        .recover { case e =>
          InternalServerError(
            Json.obj(
              "errorMessage" -> e.getMessage,
              "errorCode"    -> "CLAIM_SERVICE_ERROR"
            )
          )
        }
    }

  def getClaim(claimId: String): Action[String] =
    whenAuthorised {
      claimsService
        .getClaim(claimId)
        .map {
          case None =>
            NotFound(
              Json.obj(
                "errorMessage" -> s"Claim with claimId $claimId not found",
                "errorCode"    -> "CLAIM_NOT_FOUND_ERROR"
              )
            )

          case Some(claim) =>
            Ok(Json.toJson(claim))
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
