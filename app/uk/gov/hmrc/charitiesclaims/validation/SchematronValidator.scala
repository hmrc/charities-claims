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

package uk.gov.hmrc.charitiesclaims.validation

enum ValidationResult:
  case Success
  case Error(error: ValidationError)

type SchematronResult = Either[List[ValidationError], Unit]

trait SchematronRule[A] {
  def validate(value: A): ValidationResult
}

object SchematronRule {
  def apply[A](f: A => ValidationResult): SchematronRule[A] = (value: A) => f(value)
}

trait SchematronValidator[A] {
  def validate(value: A): SchematronResult

  protected def combineResults(results: List[ValidationResult]): SchematronResult =
    val errors = results.collect { case ValidationResult.Error(err) => err }
    if errors.isEmpty then Right(()) else Left(errors)
}
