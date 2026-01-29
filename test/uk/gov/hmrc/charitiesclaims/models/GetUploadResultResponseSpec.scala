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

package uk.gov.hmrc.charitiesclaims.models

import play.api.libs.json.Json
import uk.gov.hmrc.charitiesclaims.models.*
import uk.gov.hmrc.charitiesclaims.util.TestScheduleData
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GetUploadResultResponseSpec extends AnyWordSpec with Matchers {

  def validateReadingAndWriting(entity: GetUploadResultResponse): Unit =
    Json.parse(Json.prettyPrint(Json.toJson(entity))).as[GetUploadResultResponse] shouldBe entity

  "GetUploadSummaryResponse" should {

    "successfully parse GetUploadResultFailure when fileStatus is AWAITING_UPLOAD" in {
      val result = Json
        .parse("""|{
                  |  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
                  |  "validationType": "GiftAid",
                  |  "fileStatus": "AWAITING_UPLOAD",
                  |  "uploadUrl": "https://xxxx/upscan-upload-proxy/bucketName"
                  |}""".stripMargin)
        .validate[GetUploadResultResponse]
      result match {
        case JsSuccess(value, _) => value should be(a[GetUploadResultFailure])
        case JsError(errors)     => fail(s"Failed to parse GetUploadResultFailure: $errors")
      }
    }

    "successfully parse GetUploadResultFailure when fileStatus is VERIFYING" in {
      val result = Json
        .parse("""|{
                  |  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
                  |  "validationType": "GiftAid",
                  |  "fileStatus": "VERIFYING"
                  |}""".stripMargin)
        .validate[GetUploadResultResponse]
      result match {
        case JsSuccess(value, _) => value should be(a[GetUploadResultFailure])
        case JsError(errors)     => fail(s"Failed to parse GetUploadResultFailure: $errors")
      }
    }

    "successfully parse GetUploadResultFailure when fileStatus is VERIFICATION_FAILED" in {
      val result = Json
        .parse("""|{
                  |  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
                  |  "validationType": "GiftAid",
                  |  "fileStatus": "VERIFICATION_FAILED",
                  |  "failureDetails": {
                  |    "failureReason": "QUARANTINE",
                  |    "message": "e.g. This file has a virus"
                  |  }
                  |}""".stripMargin)
        .validate[GetUploadResultResponse]
      result match {
        case JsSuccess(value, _) => value should be(a[GetUploadResultFailure])
        case JsError(errors)     => fail(s"Failed to parse GetUploadResultFailure: $errors")
      }
    }

    "successfully parse GetUploadResultFailure when fileStatus is VALIDATING" in {
      val result = Json
        .parse("""|{
                  |  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
                  |  "validationType": "GiftAid",
                  |  "fileStatus": "VALIDATING"
                  |}""".stripMargin)
        .validate[GetUploadResultResponse]
      result match {
        case JsSuccess(value, _) => value should be(a[GetUploadResultFailure])
        case JsError(errors)     => fail(s"Failed to parse GetUploadResultFailure: $errors")
      }
    }

    "successfully parse GetUploadResultValidatedGiftAid" in {
      val result = Json
        .parse("""|{
                  |  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
                  |  "validationType": "GiftAid",
                  |  "fileStatus": "VALIDATED",
                  |  "giftAidScheduleData": {
                  |    "earliestDonationDate": "2025-01-31",
                  |    "prevOverclaimedGiftAid": 0.00,
                  |    "totalDonations": 1450,
                  |    "donations": [
                  |      {
                  |        "donationItem": 1,
                  |        "donorTitle": "Mr",
                  |        "donorFirstName": "Henry",
                  |        "donorLastName": "House Martin",
                  |        "donorHouse": "152A",
                  |        "donorPostcode": "M99 2QD",
                  |        "sponsoredEvent": false,
                  |        "donationDate": "2025-03-24",
                  |        "donationAmount": 240
                  |      },
                  |      {
                  |        "donationItem": 2,
                  |        "donorTitle": "Mr",
                  |        "donorFirstName": "John",
                  |        "donorLastName": "Smith",
                  |        "donorHouse": "100 Champs Elysees, Paris",
                  |        "donorPostcode": "X",
                  |        "sponsoredEvent": false,
                  |        "donationDate": "2025-06-24",
                  |        "donationAmount": 250
                  |      },
                  |      {
                  |        "donationItem": 3,
                  |        "aggregatedDonations": "One off Gift Aid donations",
                  |        "sponsoredEvent": false,
                  |        "donationDate": "2025-03-31",
                  |        "donationAmount": 880
                  |      },
                  |      {
                  |        "donationItem": 4,
                  |        "donorTitle": "Miss",
                  |        "donorFirstName": "B",
                  |        "donorLastName": "Chaudry",
                  |        "donorHouse": "21",
                  |        "donorPostcode": "L43 4FB",
                  |        "sponsoredEvent": true,
                  |        "donationDate": "2025-04-26",
                  |        "donationAmount": 80
                  |      }
                  |    ]
                  |  }
                  |}""".stripMargin)
        .validate[GetUploadResultResponse]
      result match {
        case JsSuccess(value, _) => value should be(a[GetUploadResultValidatedGiftAid])
        case JsError(errors)     => fail(s"Failed to parse GetUploadResultValidatedGiftAid: $errors")
      }
    }

    "successfully parse GetUploadResultValidatedOtherIncome" in {
      val result = Json
        .parse("""|{
                  |  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
                  |  "validationType": "OtherIncome",
                  |  "fileStatus": "VALIDATED",
                  |  "otherIncomeData": {
                  |      "adjustmentForOtherIncomePreviousOverClaimed": 78.00,
                  |      "totalOfGrossPayments": 123.00,
                  |      "totalOfTaxDeducted": 39.00,
                  |      "otherIncomes": [
                  |        {
                  |          "otherIncomeItem": 1,
                  |          "payerName": "Test User",
                  |          "paymentDate": "2025-01-01",
                  |          "grossPayment": 1234,
                  |          "taxDeducted": 56
                  |        }
                  |      ]
                  |  }
                  |}""".stripMargin)
        .validate[GetUploadResultResponse]
      result match {
        case JsSuccess(value, _) => value should be(a[GetUploadResultValidatedOtherIncome])
        case JsError(errors)     => fail(s"Failed to parse GetUploadResultValidatedOtherIncome: $errors")
      }
    }

    "successfully parse GetUploadResultValidatedCommunityBuildings" in {
      val result = Json
        .parse("""|{
                  |  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
                  |  "validationType": "CommunityBuildings",
                  |  "fileStatus": "VALIDATED",
                  |  "communityBuildingsData": {
                  |    "totalOfAllAmounts": "17520.00",
                  |    "communityBuildings": [
                  |      {
                  |        "communityBuildingItem": 1,
                  |        "buildingName": "The Vault",
                  |        "firstLineOfAddress": "22 Liberty Place",
                  |        "postcode": "L20 3UD",
                  |        "taxYear1": 2023,
                  |        "amountYear1": 1500,
                  |        "taxYear2": 2024,
                  |        "amountYear2": 2500
                  |      },
                  |      {
                  |        "communityBuildingItem": 2,
                  |        "buildingName": "The Vault",
                  |        "firstLineOfAddress": "22 Liberty Place",
                  |        "postcode": "L20 3UD",
                  |        "taxYear1": 2025,
                  |        "amountYear1": 2000
                  |      },
                  |      {
                  |        "communityBuildingItem": 3,
                  |        "buildingName": "Bootle Village Hall",
                  |        "firstLineOfAddress": "11A Grange Road",
                  |        "postcode": "L20 1KL",
                  |        "taxYear1": 2025,
                  |        "amountYear1": 1750
                  |      }
                  |    ]
                  |  }
                  |}""".stripMargin)
        .validate[GetUploadResultResponse]
      result match {
        case JsSuccess(value, _) => value should be(a[GetUploadResultValidatedCommunityBuildings])
        case JsError(errors)     => fail(s"Failed to parse GetUploadResultValidatedCommunityBuildings: $errors")
      }
    }

    "successfully parse GetUploadResultValidatedConnectedCharities" in {
      val result = Json
        .parse("""|{
  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
  "validationType": "ConnectedCharities",
  "fileStatus": "VALIDATED",
  "connectedCharitiesData": {
    "charities": [
      {
        "charityItem": 1,
        "charityName": "Charity of the 501st Legion",
        "charityReference": "CW501"
      }
    ]
  }
}""".stripMargin)
        .validate[GetUploadResultResponse]
      result match {
        case JsSuccess(value, _) => value should be(a[GetUploadResultValidatedConnectedCharities])
        case JsError(errors)     => fail(s"Failed to parse GetUploadResultValidatedConnectedCharities: $errors")
      }
    }

    "successfully parse GetUploadResultFailure when fileStatus is VALIDATION_FAILED for GiftAid" in {
      val result = Json
        .parse("""|{
                  |  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
                  |  "validationType": "GiftAid",
                  |  "fileStatus": "VALIDATION_FAILED",
                  |  "giftAidScheduleData": {
                  |    "earliestDonationDate": "2025-01-31",
                  |    "prevOverclaimedGiftAid": 0.00,
                  |    "totalDonations": 1450,
                  |    "donations": [
                  |      {
                  |        "donationItem": null,
                  |        "donorTitle": "Mr",
                  |        "donorFirstName": "Henry",
                  |        "donorLastName": "House Martin",
                  |        "donorHouse": "152A",
                  |        "donorPostcode": "M99 2QD",
                  |        "sponsoredEvent": false,
                  |        "donationDate": "2025-03-24",
                  |        "donationAmount": 240
                  |      },
                  |      {
                  |        "donationItem": 2,
                  |        "donorTitle": "Mr",
                  |        "donorFirstName": "John",
                  |        "donorLastName": "Smith",
                  |        "donorHouse": "100 Champs Elysees, Paris",
                  |        "donorPostcode": "X",
                  |        "sponsoredEvent": false,
                  |        "donationDate": "2025-06-24",
                  |        "donationAmount": 250
                  |      },
                  |      {
                  |        "donationItem": 3,
                  |        "donorTitle": "Dr",
                  |        "donorFirstName": "Jane",
                  |        "donorLastName": "Doe",
                  |        "aggregatedDonations": "One off Gift Aid donations",
                  |        "sponsoredEvent": false,
                  |        "donationDate": "2025-03-31",
                  |        "donationAmount": 880
                  |      },
                  |      {
                  |        "donationItem": 4,
                  |        "donorTitle": "Miss",
                  |        "donorFirstName": "B",
                  |        "donorLastName": "Chaudry",
                  |        "donorHouse": "21",
                  |        "donorPostcode": "L43 4FB",
                  |        "sponsoredEvent": true,
                  |        "donationDate": "2025-04-26",
                  |        "donationAmount": 80
                  |      }
                  |    ]
                  |  },
                  |  "errors": [
                  |    {
                  |      "field": "earliestDonationDate",
                  |      "error": "ERROR: Earliest donation date is missing."
                  |    },
                  |    {
                  |      "field": "donations[0]",
                  |      "error": "ERROR: Item You have entered data in an invalid area of the form."
                  |    },
                  |    {
                  |      "field": "donations[2]",
                  |      "error": "ERROR: Item 3 You cannot provide both a title and an entry for an aggregated donation box. Please amend your entry"
                  |    },
                  |    {
                  |      "field": "donations[2]",
                  |      "error": "ERROR: Item 3 You cannot provide both a first name and an entry for an aggregated donation. Please amend your entry."
                  |    },
                  |    {
                  |      "field": "donations[2]",
                  |      "error": "ERROR: Item 3 You cannot provide both a last name and an entry for an aggregated donation. Please amend your entry."
                  |    }
                  |  ]
                  |}""".stripMargin)
        .validate[GetUploadResultResponse]
      result match {
        case JsSuccess(value, _) => value should be(a[GetUploadResultFailure])
        case JsError(errors)     => fail(s"Failed to parse GetUploadResultFailure: $errors")
      }
    }

    "successfully parse GetUploadResultFailure when fileStatus is VALIDATION_FAILED for OtherIncome" in {
      val result = Json
        .parse("""|{
                  |  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
                  |  "validationType": "OtherIncome",
                  |  "fileStatus": "VALIDATION_FAILED",
                  |  "otherIncomeData": {
                  |      "adjustmentForOtherIncomePreviousOverClaimed": 78.00,
                  |      "totalOfGrossPayments": 123.00,
                  |      "totalOfTaxDeducted": 39.00,
                  |      "otherIncomes": [
                  |        {
                  |          "otherIncomeItem": 1,
                  |          "payerName": "Test User",
                  |          "paymentDate": "2025-01-01",
                  |          "grossPayment": 1234,
                  |          "taxDeducted": 56
                  |        }
                  |      ]
                  |    },
                  |    "errors": [
                  |      {
                  |        "field": "adjustmentForOtherIncomePreviouslyOverClaimed",
                  |        "error": "ERROR: The amount overclaimed is in the wrong format. Please amend your entry."
                  |      },
                  |      {
                  |        "field": "payerName[24]",
                  |        "error": "ERROR: Item 1 Name of payer is missing."
                  |      },
                  |      {
                  |        "field": "paymentDate[25]",
                  |        "error": "ERROR: Item 2 Income date is missing."
                  |      },
                  |      {
                  |        "field": "grossPayment[26]",
                  |        "error": "ERROR: Item 3 Gross payment is missing."
                  |      },
                  |      {
                  |        "field": "taxDeducted[27]",
                  |        "error": "ERROR: Item 4 Tax deducted is missing."
                  |      },
                  |      {
                  |        "field": "payerName[28]",
                  |        "error": "ERROR: Item 5 Name of payer format is invalid. You can only supply a maximum of 40 valid characters."
                  |      },
                  |      {
                  |        "field": "paymentDate[29]",
                  |        "error": "ERROR: Item 6 Income date is in an invalid format. It should be DD/MM/YY."
                  |      },
                  |      {
                  |        "field": "paymentDate[30]",
                  |        "error": "ERROR: Item 7 Other income date of payment cannot be in the future."
                  |      },
                  |      {
                  |        "field": "grossPayment[31]",
                  |        "error": "ERROR: Item 8 The gross payment you have provided is in an invalid format."
                  |      },
                  |      {
                  |        "field": "taxDeducted[32]",
                  |        "error": "ERROR: Item 9 The tax deducted you have provided is in an invalid format."
                  |      },
                  |      {
                  |        "field": "item[33]",
                  |        "error": "ERROR: You have entered data in an invalid area of the form."
                  |      },
                  |      {
                  |        "field": "taxDeducted[33]",
                  |        "error": "ERROR: Item The tax deducted must be less than the gross payment."
                  |      }
                  |    ]
                  |  }""".stripMargin)
        .validate[GetUploadResultResponse]
      result match {
        case JsSuccess(value, _) => value should be(a[GetUploadResultFailure])
        case JsError(errors)     => fail(s"Failed to parse GetUploadResultFailure: $errors")
      }
    }

    "successfully parse GetUploadResultFailure when fileStatus is VALIDATION_FAILED for CommunityBuildings" in {
      val result = Json
        .parse("""|{
                  |  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
                  |  "validationType": "CommunityBuildings",
                  |  "fileStatus": "VALIDATION_FAILED",
                  |  "communityBuildingsData": {
                  |    "totalOfAllAmounts": "17520.00",
                  |    "communityBuildings": [
                  |      {
                  |        "communityBuildingItem": 1,
                  |        "buildingName": "The Vault",
                  |        "firstLineOfAddress": "22 Liberty Place",
                  |        "postcode": "L20 3UD",
                  |        "taxYear1": 2023,
                  |        "amountYear1": 1500,
                  |        "taxYear2": 2024,
                  |        "amountYear2": 2500
                  |      },
                  |      {
                  |        "communityBuildingItem": 2,
                  |        "buildingName": "The Vault",
                  |        "firstLineOfAddress": "22 Liberty Place",
                  |        "postcode": "L20 3UD",
                  |        "taxYear1": 2025,
                  |        "amountYear1": 2000
                  |      },
                  |      {
                  |        "communityBuildingItem": 3,
                  |        "buildingName": "Bootle Village Hall",
                  |        "firstLineOfAddress": "11A Grange Road",
                  |        "postcode": "L20 1KL",
                  |        "taxYear1": 2025,
                  |        "amountYear1": 1750
                  |      }
                  |    ]
                  |  },
                  |  "errors": [
                  |    {
                  |      "field": "communityBuildings",
                  |      "error": "ERROR: If donations under the Gift Aid Small Donations Scheme is being claimed for community buildings then the details of at least one community building must be supplied."
                  |    }
                  |  ]
                  |}""".stripMargin)
        .validate[GetUploadResultResponse]
      result match {
        case JsSuccess(value, _) => value should be(a[GetUploadResultFailure])
        case JsError(errors)     => fail(s"Failed to parse GetUploadResultFailure: $errors")
      }
    }

    "successfully parse GetUploadResultFailure when fileStatus is VALIDATION_FAILED for ConnectedCharities" in {
      val result = Json
        .parse("""|{
                  |  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
                  |  "validationType": "ConnectedCharities",
                  |  "fileStatus": "VALIDATION_FAILED",
                  |  "connectedCharitiesData": {
                  |    "charities": [
                  |      {
                  |        "charityItem": 1,
                  |        "charityName": "Charity of the 501st Legion",
                  |        "charityReference": "CW501"
                  |      }
                  |    ]
                  |  },
                  |  "errors": [
                  |    {
                  |      "field": "charityName[15]",
                  |      "error": "ERROR: Item 1 Charity name is missing."
                  |    },
                  |    {
                  |      "field": "charityReference[16]",
                  |      "error": "ERROR: Item 2 HMRC charities reference is missing."
                  |    },
                  |    {
                  |      "field": "charityName[17]",
                  |      "error": "ERROR: Item 3 Name of charity is in an invalid format. You can only supply a maximum of 160 characters."
                  |    },
                  |    {
                  |      "field": "charityReference[18]",
                  |      "error": "ERROR: Item 4 HMRC charities reference number is in an invalid format."
                  |    },
                  |    {
                  |      "field": "item[19]",
                  |      "error": "ERROR: You have entered data in an invalid area of the form."
                  |    }
                  |  ]
                  |}""".stripMargin)
        .validate[GetUploadResultResponse]
      result match {
        case JsSuccess(value, _) => value should be(a[GetUploadResultFailure])
        case JsError(errors)     => fail(s"Failed to parse GetUploadResultFailure: $errors")
      }
    }

    "GetUploadResultFailure" should {
      "be serialised and deserialised correctly" in {
        val result = GetUploadResultFailure(
          reference = FileUploadReference("test-ref-123"),
          validationType = ValidationType.GiftAid,
          fileStatus = FileStatus.AWAITING_UPLOAD,
          uploadUrl = Some("https://example.com/upload"),
          failureDetails =
            Some(GetUploadResultFailureDetails(failureReason = "test-failure-reason", message = "test-message"))
        )
        validateReadingAndWriting(result)
      }
    }

    "GetUploadResultValidatedGiftAid"            should {
      "be serialised and deserialised correctly" in {
        val result = GetUploadResultValidatedGiftAid(
          reference = FileUploadReference("test-ref-123"),
          giftAidScheduleData = TestScheduleData.exampleGiftAidScheduleData
        )
        validateReadingAndWriting(result)
      }
    }
    "GetUploadResultValidatedOtherIncome"        should {
      "be serialised and deserialised correctly" in {
        val result = GetUploadResultValidatedOtherIncome(
          reference = FileUploadReference("test-ref-123"),
          otherIncomeData = TestScheduleData.exampleOtherIncomeScheduleData
        )
        validateReadingAndWriting(result)
      }
    }
    "GetUploadResultValidatedCommunityBuildings" should {
      "be serialised and deserialised correctly" in {
        val result = GetUploadResultValidatedCommunityBuildings(
          reference = FileUploadReference("test-ref-123"),
          communityBuildingsData = TestScheduleData.exampleCommunityBuildingsScheduleData
        )
        validateReadingAndWriting(result)
      }
    }
    "GetUploadResultValidatedConnectedCharities" should {
      "be serialised and deserialised correctly" in {
        val result = GetUploadResultValidatedConnectedCharities(
          reference = FileUploadReference("test-ref-123"),
          connectedCharitiesData = TestScheduleData.exampleConnectedCharitiesScheduleData
        )
        validateReadingAndWriting(result)
      }
    }

    "raise and error when file status is not provided" in {
      val result = Json
        .parse("""|{
                    |  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
                    |  "validationType": "GiftAid",
                    |  "uploadUrl": "https://xxxx/upscan-upload-proxy/bucketName"
                    |}""".stripMargin)
        .validate[GetUploadResultResponse]
      result.isError shouldBe true
    }

    "raise and error when file status is invalid" in {
      val result = Json
        .parse("""|{
                    |  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
                    |  "validationType": "GiftAid",
                    |  "fileStatus": "FOO",
                    |  "uploadUrl": "https://xxxx/upscan-upload-proxy/bucketName"
                    |}""".stripMargin)
        .validate[GetUploadResultResponse]
      result.isError shouldBe true
    }

    "raise and error when validation type is invalid" in {
      val result = Json
        .parse("""|{
                  |  "reference": "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
                  |  "validationType": "Foo",
                  |  "fileStatus": "VALIDATED",
                  |  "otherIncomeData": {
                  |      "adjustmentForOtherIncomePreviousOverClaimed": 78.00,
                  |      "totalOfGrossPayments": 123.00,
                  |      "totalOfTaxDeducted": 39.00,
                  |      "otherIncomes": [
                  |        {
                  |          "otherIncomeItem": 1,
                  |          "payerName": "Test User",
                  |          "paymentDate": "2025-01-01",
                  |          "grossPayment": 1234,
                  |          "taxDeducted": 56
                  |        }
                  |      ]
                  |  }
                  |}""".stripMargin)
        .validate[GetUploadResultResponse]
      result.isError shouldBe true
    }

  }
}
