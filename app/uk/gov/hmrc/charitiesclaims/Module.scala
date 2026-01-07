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

package uk.gov.hmrc.charitiesclaims

import org.apache.pekko.actor.ActorSystem
import play.api.inject.{Binding, Module as AppModule}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.{HttpClientV2, HttpClientV2Impl}
import uk.gov.hmrc.http.hooks.*
import uk.gov.hmrc.http.hooks.HookData.{FromMap, FromString}
import uk.gov.hmrc.play.audit.http.HttpAuditing

import java.net.URL
import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.AnsiColor.*
import scala.util.{Failure, Success, Try}

class Module extends AppModule {

  override def bindings(
    environment: Environment,
    configuration: Configuration
  ): Seq[Binding[?]] =
    Seq(
      bind[Clock].toInstance(Clock.systemDefaultZone), // inject if current time needs to be controlled in unit tests
      bind[HttpClientV2].to(classOf[DebuggingHttpClientV2])
    )

}

class DebuggingHook(config: Configuration) extends HttpHook {

  val shouldDebug: Boolean =
    config.underlying.getBoolean("debugOutboundRequests")

  override def apply(
    verb: String,
    url: URL,
    request: RequestData,
    responseF: Future[ResponseData]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit = {
    if shouldDebug && !url.getPath().contains("/auth/authorise") then {
      responseF.andThen {
        case Success(response) =>
          Logger("OutboundRequest").debug(s"""$printRequest  
          |$YELLOW Response: $BOLD${response.status}$RESET
          |   ${response.headers.toSeq
                                              .flatMap { case (k, vs) => vs.map(v => s"$BLUE$k: $MAGENTA$v$RESET") }
                                              .mkString("\n   ")}
          |      
          |$GREEN${Try(Json.prettyPrint(Json.parse(response.body.value)))
                                              .getOrElse(response.body.value)}$RESET\n""".stripMargin)

        case Failure(exception) =>
          Logger("OutboundRequest").debug(
            s"""$printRequest
            |$RED_B$WHITE Failure: $BOLD${exception.toString()} $RESET
            |""".stripMargin
          )
      }
    }

    def printRequest =
      s"""
        | $BOLD$YELLOW$verb $CYAN$url$RESET 
        |   ${request.headers.map { case (k, v) => s"$BLUE$k: $MAGENTA$v$RESET" }.mkString("\n   ")}
        |
        |${request.body
          .map { case Data(value, _, _) =>
            value match {
              case FromMap(m) =>
                m.toSeq
                  .flatMap { case (k, vs) => vs.map(v => (k, v)) }
                  .map { case (k, v) => s"$k = $v" }
                  .mkString("\n   ")

              case FromString(s) =>
                s"$GREEN${Try(Json.prettyPrint(Json.parse(s))).getOrElse(s)}$RESET"
            }
          }
          .getOrElse("")}""".stripMargin
  }

}

@Singleton
class DebuggingHttpClientV2 @Inject() (
  config: Configuration,
  httpAuditing: HttpAuditing,
  wsClient: WSClient,
  actorSystem: ActorSystem
) extends HttpClientV2Impl(
      wsClient = wsClient,
      actorSystem = actorSystem,
      config = config,
      hooks = Seq(httpAuditing.AuditingHook, new DebuggingHook(config))
    )
