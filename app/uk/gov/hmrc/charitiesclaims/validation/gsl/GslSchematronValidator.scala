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

package uk.gov.hmrc.charitiesclaims.validation.gsl

import com.gsl.schematron.validator.api.{SchematronValidatorFactory, SchematronValidatorParams, ValidationType}
import com.gsl.schematron.validator.{RuleGeneratorMode, SchematronValidatorFactoryImpl}
import uk.gov.hmrc.charitiesclaims.validation.{SchematronResult, SchematronValidator, ValidationError}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import scala.util.Try

/** Approach C: GSL Schematron Validator using precompiled HMRC-Charities rules.
  *
  * Wraps the legacy GSLSchematronValidator JAR to validate XML against Schematron business rules.
  */
object GslSchematronValidator extends SchematronValidator[String] {

  private lazy val factory: SchematronValidatorFactory = {
    val params = new SchematronValidatorParams()
    params.setValidationType(ValidationType.COMPLETE)
    params.setMaxErrors(8000)
    params.setTemporaryDirpath(System.getProperty("java.io.tmpdir"))
    new SchematronValidatorFactoryImpl(params, RuleGeneratorMode.LOAD_FROM_CLASSPATH)
  }

  override def validate(xml: String): SchematronResult =
    Try {
      val validator = factory.createValidator()
      val input     = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
      val output    = new ByteArrayOutputStream()

      val result = validator.validateSubmission(input, output)

      val errors = parseErrors(result)
      if (errors.isEmpty) {
        Right(())
      } else {
        Left(errors)
      }
    }.recover { case e: Exception =>
      e.printStackTrace()
      Left(List(ValidationError("GSL_ERROR", s"GSL validation failed: ${e.getClass.getName}: ${e.getMessage}")))
    }.get

  private def parseErrors(result: validationResponse.output.ValidationResult): List[ValidationError] =
    import scala.jdk.CollectionConverters.*
    val processingErrors = Option(result.getProcessingErrors).map(_.asScala.toList).getOrElse(Nil)
    processingErrors.map { err =>
      ValidationError(
        code = Option(err.getErrorCode).getOrElse("UNKNOWN"),
        message = Option(err.getMessage).getOrElse("Unknown error")
      )
    }
}
