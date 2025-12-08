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
import play.api.mvc.Results.{InternalServerError, Ok}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.charitiesclaims.controllers.actions.AuthorisedAction
import uk.gov.hmrc.charitiesclaims.services.ClaimsService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton()
class DeleteClaimController @Inject() (
  val cc: ControllerComponents,
  val authorisedAction: AuthorisedAction,
  claimsService: ClaimsService
)(using ExecutionContext)
    extends BaseController {

  final def deleteClaim(claimId: String): Action[String] =
    whenAuthorised {
      claimsService
        .deleteClaim(claimId)
        .map(_ => Ok(Json.obj("success" -> true)))
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
