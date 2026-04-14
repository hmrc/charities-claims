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

package uk.gov.hmrc.charitiesclaims.models.chris

import uk.gov.hmrc.charitiesclaims.util.BaseSpec

class Base32Spec extends BaseSpec {

  val testCases = List(
    ("", ""),
    (" ", "EA"),
    ("12345", "GEZDGNBV"),
    ("67890", "GY3TQOJQ"),
    ("@#$", "IARSI"),
    ("!@#$%^&*()", "EFACGJBFLYTCUKBJ"),
    ("f", "MY"),
    ("fo", "MZXQ"),
    ("foo", "MZXW6"),
    ("foob", "MZXW6YQ"),
    ("fooba", "MZXW6YTB"),
    ("foobar", "MZXW6YTBOI"),
    ("Hello, world!", "JBSWY3DPFQQHO33SNRSCC"),
    (
      "Base32 is a transfer encoding using a 32-character set, which can be beneficial when dealing with case-insensitive filesystems, spoken language or human memory.",
      "IJQXGZJTGIQGS4ZAMEQHI4TBNZZWMZLSEBSW4Y3PMRUW4ZZAOVZWS3THEBQSAMZSFVRWQYLSMFRXIZLSEBZWK5BMEB3WQ2LDNAQGGYLOEBRGKIDCMVXGKZTJMNUWC3BAO5UGK3RAMRSWC3DJNZTSA53JORUCAY3BONSS22LOONSW443JORUXMZJAMZUWYZLTPFZXIZLNOMWCA43QN5VWK3RANRQW4Z3VMFTWKIDPOIQGQ5LNMFXCA3LFNVXXE6JO"
    )
  )

  "Base32" - {
    "encode to base32" in {
      for ((input, expected) <- testCases) {
        val bytes = input.getBytes("UTF-8")
        Base32.encodeToBase32(bytes) should be(expected)
      }
    }
  }
}
