
# charities-claims

Backend for `charities-claim-frontend` supporting the following functions:

- retrieving data from the existing RDS DataCache
- storing and retrieving draft claim data from MongoDB cache
- submissions of a new claims to ChRIS

## REST API

| Method | Endpoint | Description | Request Body | Response | Error Response |
|--------|----------|-------------|--------------|----------|----------------|
| `POST` | `/get-claims` | Retrieve claims from MongoDB cache. Returns either submitted or unsubmitted claims based on the request parameter. | `{ "claimSubmitted": boolean }` | `200 OK`<br>`{ "claimsCount": number, "claimsList": Claim[] }` | `500 Internal Server Error`<br>`{ "errorMessage": string, "errorCode": "CLAIM_SERVICE_ERROR" }` |
| `POST` | `/claims` | Create and save a new draft claim to MongoDB cache. Generates a unique claim ID and creation timestamp. | `{ "claimingGiftAid": boolean, "claimingTaxDeducted": boolean, "claimingUnderGasds": boolean, "claimReferenceNumber": string?, "claimingDonationsNotFromCommunityBuilding": boolean?, "claimingDonationsCollectedInCommunityBuildings": boolean?, "connectedToAnyOtherCharities": boolean?, "makingAdjustmentToPreviousClaim": boolean? }` | `200 OK`<br>`{ "claimId": string, "creationTimestamp": string }` | `500 Internal Server Error`<br>`{ "errorMessage": string, "errorCode": "CLAIM_SERVICE_ERROR" }` |
| `DELETE` | `/claims/:claimId` | Delete a claim from MongoDB cache by claim ID. | N/A | `200 OK`<br>`{ "success": true }` | `500 Internal Server Error`<br>`{ "errorMessage": string, "errorCode": "CLAIM_SERVICE_ERROR" }` |

**Note:** All endpoints require authorization. The `?` suffix indicates optional fields.



### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").