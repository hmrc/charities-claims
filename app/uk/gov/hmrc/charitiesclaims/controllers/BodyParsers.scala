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

import play.api.mvc.BodyParser
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.util.ByteString
import java.nio.charset.StandardCharsets
import play.api.libs.streams.Accumulator
import scala.concurrent.Future
import play.api.mvc.Result

object BodyParsers {

  val parseTolerantTextUtf8: BodyParser[String] =
    BodyParser("parseTolerantTextUtf8") { _ =>
      val decodeAsUtf8: Sink[ByteString, Future[Either[Result, String]]] =
        Sink
          .fold[Either[Result, String], ByteString](Right("")) { case (a, b) =>
            a.map(_ + (b.decodeString(StandardCharsets.UTF_8)))
          }
      Accumulator(decodeAsUtf8)
    }
}
