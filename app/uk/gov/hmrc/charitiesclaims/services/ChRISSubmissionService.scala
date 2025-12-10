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

package uk.gov.hmrc.charitiesclaims.services

import uk.gov.hmrc.charitiesclaims.models.chris.*
import uk.gov.hmrc.charitiesclaims.models as models
import com.google.inject.ImplementedBy
import scala.concurrent.Future
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier

@ImplementedBy(classOf[ChRISSubmissionServiceImpl])
trait ChRISSubmissionService {

  def buildChRISSubmission(claim: models.Claim, currentUser: models.CurrentUser)(using
    HeaderCarrier
  ): Future[GovTalkMessage]

}

@Singleton
class ChRISSubmissionServiceImpl @Inject() (
) extends ChRISSubmissionService {

  def buildChRISSubmission(
    claim: models.Claim,
    currentUser: models.CurrentUser
  )(using HeaderCarrier): Future[GovTalkMessage] = {

    val govTalkMessage = GovTalkMessage(
      GovTalkDetails = buildGovTalkDetails(currentUser),
      Body = Body(
        IRenvelope = IRenvelope(
          IRheader = buildIRheader(currentUser),
          R68 = buildR68(claim, currentUser)
        )
      )
    ).withLiteIRmark

    Future.successful(govTalkMessage)
  }

  def buildGovTalkDetails(currentUser: models.CurrentUser)(using hc: HeaderCarrier): GovTalkDetails =
    GovTalkDetails(
      Keys = List(
        Key(Type = "CredentialID", Value = currentUser.userId),
        if (currentUser.isAgent )
        then ???
        else Key(Type = currentUser.enrolmentIdentifierKey, Value = currentUser.enrolmentIdentifierValue),
        Key(Type = "SessionID", Value = hc.sessionId.map(_.value).getOrElse("unknown"))
      )
    )

  def buildIRheader(currentUser: models.CurrentUser): IRheader =
    IRheader(
      Keys = List(
        if (currentUser.isAgent)
        then ???
        else Key(Type = currentUser.enrolmentIdentifierKey, Value = currentUser.enrolmentIdentifierValue)
      ),
      PeriodEnd = "2012-01-01",
      Sender = "Other" // constant value
    )

  def buildR68(claim: models.Claim, currentUser: models.CurrentUser): R68 =
    R68(
      AuthOfficial = buildAuthOfficial(claim),
      Declaration = true,
      Claim = buildClaim(claim, currentUser)
    )

  def buildAuthOfficial(claim: models.Claim): Option[AuthOfficial] =
    None

  def buildClaim(claim: models.Claim, currentUser: models.CurrentUser): Claim =
    Claim(
      // If user has an affinity group of "Agent", then set to the value of "Name of Charity or CASC"
      // Else set to the Organisation name returned from I3 - RDS DataCache Proxy Microservice - GetOrganisationNamebyCharityReference
      OrgName = ???,
      // If user has an affinity group of "Agent", then set to the value of "HMRC Charities Reference"
      // Else set to the Charities Reference (derived from their HMRC-CHAR-ORG enrolment and CHARID identifier)
      HMRCref = ???,
      Regulator = buildRegulator(claim)
    )

  def buildRegulator(claim: models.Claim): Option[Regulator] =
    None

}
