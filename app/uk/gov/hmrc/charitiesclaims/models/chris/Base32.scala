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

package uk.gov.hmrc.charitiesclaims.models.chris

/** Base32 is a transfer encoding using a 32-character set, which can be beneficial when dealing with case-insensitive
  * filesystems, spoken language or human memory.
  */
object Base32 {

  def encodeToBase32(bytes: Array[Byte]): String = {
    val sb     = new StringBuffer(bytes.length * 8 / 5 + 1)
    var buffer = 0L
    var i      = 0

    while (i < bytes.length) {
      buffer <<= 8
      buffer |= (bytes(i) & 255)
      i = i + 1
      if (i % 5 == 0) {
        sb.append(base32Digit((buffer >>> 35) & 31))
        sb.append(base32Digit((buffer >>> 30) & 31))
        sb.append(base32Digit((buffer >>> 25) & 31))
        sb.append(base32Digit((buffer >>> 20) & 31))
        sb.append(base32Digit((buffer >>> 15) & 31))
        sb.append(base32Digit((buffer >>> 10) & 31))
        sb.append(base32Digit((buffer >>> 5) & 31))
        sb.append(base32Digit(buffer & 31))
        buffer = 0L
      }
    }

    (bytes.length % 5) match {

      case 1 =>
        buffer <<= 2
        sb.append(base32Digit((buffer >>> 5) & 31))
        sb.append(base32Digit(buffer & 31))
      case 2 =>
        buffer <<= 4
        sb.append(base32Digit((buffer >>> 15) & 31))
        sb.append(base32Digit((buffer >>> 10) & 31))
        sb.append(base32Digit((buffer >>> 5) & 31))
        sb.append(base32Digit(buffer & 31))

      case 3 =>
        buffer <<= 1
        sb.append(base32Digit((buffer >>> 20) & 31))
        sb.append(base32Digit((buffer >>> 15) & 31))
        sb.append(base32Digit((buffer >>> 10) & 31))
        sb.append(base32Digit((buffer >>> 5) & 31))
        sb.append(base32Digit(buffer & 31))

      case 4 =>
        buffer <<= 3
        sb.append(base32Digit((buffer >>> 30) & 31))
        sb.append(base32Digit((buffer >>> 25) & 31))
        sb.append(base32Digit((buffer >>> 20) & 31))
        sb.append(base32Digit((buffer >>> 15) & 31))
        sb.append(base32Digit((buffer >>> 10) & 31))
        sb.append(base32Digit((buffer >>> 5) & 31))
        sb.append(base32Digit(buffer & 31))
      case _ =>
        ()
    }

    return sb.toString()
  }

  inline def base32Digit(v: Long): Char =
    (if v < 26 then v + 65 else v - 26 + 50).toChar
}
