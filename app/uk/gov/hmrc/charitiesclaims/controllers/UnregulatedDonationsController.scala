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

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import play.api.mvc.Results.InternalServerError
import play.api.mvc.Results.{NotFound, Ok}
import uk.gov.hmrc.charitiesclaims.controllers.actions.AuthorisedAction
import uk.gov.hmrc.charitiesclaims.connectors.FormpProxyConnector
import play.api.libs.json.Json

@Singleton()
class UnregulatedDonationsController @Inject() (
  val cc: ControllerComponents,
  val authorisedAction: AuthorisedAction,
  formpProxyConnector: FormpProxyConnector
)(using ExecutionContext)
    extends BaseController {

  final def getTotalUnregulatedDonations(charityReference: String): Action[String] =
    whenAuthorised {
      formpProxyConnector
        .getTotalUnregulatedDonations(charityReference)
        .map {
          case Some(total) => Ok(Json.obj("unregulatedDonationsTotal" -> total))
          case None        =>
            NotFound(
              Json.obj(
                "errorMessage" -> s"No unregulated donations found for the given charity reference $charityReference",
                "errorCode"    -> "NO_UNREGULATED_DONATIONS_FOUND"
              )
            )
        }
        .recover { case e =>
          InternalServerError(
            Json.obj(
              "errorMessage" -> e.getMessage,
              "errorCode"    -> "FORMP_PROXY_ERROR"
            )
          )
        }
    }
}
