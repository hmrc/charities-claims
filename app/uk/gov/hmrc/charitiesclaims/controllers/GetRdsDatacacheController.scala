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
import uk.gov.hmrc.charitiesclaims.connectors.RdsDatacacheProxyConnector
import play.api.libs.json.Json

@Singleton()
class GetRdsDatacacheController @Inject() (
  val cc: ControllerComponents,
  val authorisedAction: AuthorisedAction,
  rdsDatacacheProxyConnector: RdsDatacacheProxyConnector
)(using ExecutionContext)
    extends BaseController {

  final def getAgentName(agentReference: String): Action[String]          =
    whenAuthorised {
      rdsDatacacheProxyConnector
        .getAgentName(agentReference)
        .map {
          case Some(agentName) => Ok(Json.obj("agentName" -> agentName))
          case None            =>
            NotFound(
              Json.obj(
                "errorMessage" -> s"No agent name found for the given agent reference $agentReference",
                "errorCode"    -> "NO_AGENT_NAME_FOUND"
              )
            )
        }
        .recover { case e =>
          InternalServerError(
            Json.obj(
              "errorMessage" -> e.getMessage,
              "errorCode"    -> "RDSDATACACHE_PROXY_ERROR"
            )
          )
        }
    }
  final def getOrganisationName(charityReference: String): Action[String] =
    whenAuthorised {
      rdsDatacacheProxyConnector
        .getOrganisationName(charityReference)
        .map {
          case Some(organisationName) => Ok(Json.obj("organisationName" -> organisationName))
          case None                   =>
            NotFound(
              Json.obj(
                "errorMessage" -> s"No organisation name found for the given charity reference $charityReference",
                "errorCode"    -> "NO_ORGANISATION_NAME_FOUND"
              )
            )
        }
        .recover { case e =>
          InternalServerError(
            Json.obj(
              "errorMessage" -> e.getMessage,
              "errorCode"    -> "RDSDATACACHE_PROXY_ERROR"
            )
          )
        }
    }
}
