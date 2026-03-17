/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.charitiesclaims.stubs

import com.github.tomakehurst.wiremock.client.WireMock.*
import uk.gov.hmrc.http.test.WireMockSupport

trait AuthStub { self: WireMockSupport =>

  private val authUrl = "/auth/authorise"

  def authorisedOrganisation(userId: String = "test-user"): Unit =
    wireMockServer.stubFor(
      post(urlEqualTo(authUrl))
        .willReturn(
          okJson(
            s"""
          {
            "affinityGroup": "Organisation",
            "optionalCredentials": {
              "providerId": "$userId",
              "providerType": "GovernmentGateway"
            },
            "allEnrolments": [
              {
                "key": "HMRC-CHAR-ORG",
                "identifiers": [
                  {
                    "key": "CHARID",
                    "value": "1234567890"
                  }
                ],
                "state": "Activated"
              }
            ]
          }
          """
          )
        )
    )

  def authorisedAgent(agentId: String = "agent-123"): Unit =
    wireMockServer.stubFor(
      post(urlEqualTo(authUrl))
        .willReturn(
          okJson(
            s"""
            {
              "affinityGroup": "Agent",
              "optionalCredentials": {
              "providerId": "$agentId",
              "providerType": "GovernmentGateway"
            },
              "allEnrolments": [
                {
                  "key": "HMRC-CHAR-AGENT",
                  "identifiers": [
                    {
                      "key": "AGENTCHARID",
                      "value": "1234567890"
                    }
                  ],
                  "state": "Activated"
                }
              ]
            }
            """
          )
        )
    )

  def unauthorised(): Unit =
    wireMockServer.stubFor(
      post(urlEqualTo(authUrl))
        .willReturn(unauthorized())
    )

}
