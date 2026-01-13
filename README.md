
# charities-claims

Backend for `charities-claim-frontend` supporting the following functions:

- retrieving data from the existing RDS DataCache
- storing and retrieving draft claim data from MongoDB cache
- submissions of a new claims to ChRIS

## REST API

| Method   | Endpoint       | Description                                                                                                                            | Request Body                                                                                                                                                                                                                                                                                                                               | Response                                                                                          | Error Response                                                                                                         |
|----------|----------------|----------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `GET`    | `/claims?claimSubmitted={boolean}` | Retrieve claims info from MongoDB cache. Returns either submitted or unsubmitted claims based on query parameter. **Parameter is Required** | N/A                                                                                                                                                                                                                                                                                                                                        | `200 OK`<br>`{ "claimsCount": number, "claimsList": Claim[] }`                                   | `500 Internal Server Error`<br>`{ "errorMessage": string, "errorCode": "CLAIM_SERVICE_ERROR" }`                        |
| `POST`   | `/claims`      | Create and save a new draft claim to MongoDB cache. Generates a unique claim ID and creation timestamp.                                | `{ "claimingGiftAid": boolean, "claimingTaxDeducted": boolean, "claimingUnderGasds": boolean, "claimReferenceNumber": string?, "claimingDonationsNotFromCommunityBuilding": boolean?, "claimingDonationsCollectedInCommunityBuildings": boolean?, "connectedToAnyOtherCharities": boolean?, "makingAdjustmentToPreviousClaim": boolean? }` | `200 OK`<br>`{ "claimId": string, "creationTimestamp": string }`                                 | `500 Internal Server Error`<br>`{ "errorMessage": string, "errorCode": "CLAIM_SERVICE_ERROR" }`                        |
| `GET`    | `/claims/:claimId`      | Retrieve an existing claim from MongoDB cache.                     | N/A | `200 OK`<br>`{ ... }`<br> returns full claim object                                                             | `404 Not Found`<br>`{ "errorMessage": string, "errorCode": "CLAIM_NOT_FOUND_ERROR" }`<br>`500 Internal Server Error`<br>`{ "errorMessage": string, "errorCode": "CLAIM_SERVICE_ERROR" }` |
| `PUT`    | `/claims/:claimId`      | Update an existing claim in MongoDB cache.                     | `{"repaymentClaimDetails": {...}, "organisationDetails": {...}?, "giftAidSmallDonationsSchemeDonationDetails": {...}?, "declarationDetails": {...}?}`                                                                                                                                                                                                                                                               | `200 OK`<br>`{ "success": true }`                                                               | `404 Not Found`<br>`{ "errorMessage": string, "errorCode": "CLAIM_NOT_FOUND_ERROR" }`<br>`500 Internal Server Error`<br>`{ "errorMessage": string, "errorCode": "CLAIM_SERVICE_ERROR" }` |
| `DELETE` | `/claims/:claimId` | Delete a claim from MongoDB cache by claim ID.                                                                                         | N/A                                                                                                                                                                                                                                                                                                                                        | `200 OK`<br>`{ "success": true }`                                                               | `500 Internal Server Error`<br>`{ "errorMessage": string, "errorCode": "CLAIM_SERVICE_ERROR" }`                        |
| `POST`   | `/chris`      | Submit an existing claim to ChRIS for processing. Fails if already submitted or if updated by another user. | `{ "claimId": string, "lastUpdatedReference": string }` | `200 OK`<br>`{ "success": true, "submissionTimestamp": string, "submissionReference": string }` | `404 Not Found`<br>`{ "errorMessage": string, "errorCode": "CLAIM_NOT_FOUND_ERROR" }`<br>`400 Bad Request`<br>`{ "errorMessage": string, "errorCode": "CLAIM_ALREADY_SUBMITTED_ERROR" \| "UPDATED_BY_ANOTHER_USER" }`<br>`500 Internal Server Error`<br>`{ "errorMessage": string, "errorCode": "CLAIM_SERVICE_ERROR" \| "CHRIS_SUBMISSION_ERROR" }` |

**Note:** All endpoints require authorization. The `?` suffix indicates optional fields in request body.


## Test scripts

### test unregulated donations
requires running locally [`prh-oracle-xe/databases/charities-db`](https://github.com/hmrc/prh-oracle-xe.git) on port 1521
```
./scripts/testUnregulatedDonations.sc
```

### test ChRIS submission
provide input claim json path using `-i` option
```
./scripts/testChRISSubmission.sc -i test/resources/test-claim-organisation-authorised-official-england-uk-address.json
sm2 --logs CHARITIES_CLAIMS
```

login as an agent adding `--agent`
```
./scripts/testChRISSubmission.sc -i test/resources/test-claim-organisation-corporate-trustee-england-non-uk-address.json --agent
sm2 --logs CHARITIES_CLAIMS
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").