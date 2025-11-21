/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.charitiesclaims.handlers

import com.fasterxml.jackson.core.JsonParseException
import play.api.http.HttpErrorHandler
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results.BadRequest
import play.api.mvc.Results.InternalServerError

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class ErrorHandler @Inject() (
)(using ExecutionContext)
    extends HttpErrorHandler {

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = ???

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] =
    exception match {
      case e: JsonParseException =>
        Future.successful(
          BadRequest(
            Json.obj(
              "errorMessage" -> s"Invalid json format: ${e.getMessage()}",
              "errorCode"    -> "INVALID_JSON_FORMAT"
            )
          )
        )

      case e =>
        Future.successful(
          InternalServerError(
            Json.obj(
              "errorMessage" -> e.getMessage(),
              "errorCode"    -> e.getClass.getSimpleName
            )
          )
        )
    }

}
