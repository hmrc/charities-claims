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

import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.Results.BadRequest
import uk.gov.hmrc.charitiesclaims.controllers.actions.AuthorisedAction
import uk.gov.hmrc.charitiesclaims.models.requests.AuthorisedRequest

import scala.concurrent.Future
import uk.gov.hmrc.auth.core.AffinityGroup

trait BaseController {
  val authorisedAction: AuthorisedAction
  val cc: ControllerComponents

  inline def currentUserId(using request: AuthorisedRequest[?]): String =
    request.userId

  inline def currentUserGroup(using request: AuthorisedRequest[?]): AffinityGroup =
    request.affinityGroup

  final def whenAuthorised(block: AuthorisedRequest[String] ?=> Future[Result]): Action[String] =
    authorisedAction(BodyParsers.parseTolerantTextUtf8).async(implicit r => block)

  final def withPayload[A : Format](
    body: Request[String] ?=> A => Future[Result]
  )(using request: Request[String]): Future[Result] =
    Json
      .parse(request.body)
      .match {
        case obj: JsObject =>
          obj.validate[A].match {
            case JsSuccess(value, path) => body(value)

            case JsError(errors) =>
              Future.successful(
                BadRequest(
                  Json.obj(
                    "errorMessage" -> ("unmarshalling failed " + errors
                      .map((path, parseErrors) =>
                        s"at ${path.toString} because of ${parseErrors.map(_.message).mkString(", ")}"
                      )
                      .mkString(", ")),
                    "errorCode"    -> "INVALID_JSON_FORMAT"
                  )
                )
              )
          }

        case other =>
          Future.successful(
            BadRequest(
              Json.obj(
                "errorMessage" -> Json.prettyPrint(other),
                "errorCode"    -> "MALFORMED_JSON"
              )
            )
          )
      }
}
