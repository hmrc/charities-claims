/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.charitiesclaims.util

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.JsValue
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.client.RequestBuilder

import java.net.URL
import scala.concurrent.Future

trait HttpV2Support { this: MockitoSugar & Matchers =>

  implicit val hc: HeaderCarrier                            = HeaderCarrier()
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  val mockHttp: HttpClientV2             = mock[HttpClientV2]
  val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

  def mockHttpPostSuccess[A](url: String, requestBody: JsValue, hasHeaders: Boolean = false)(response: A) = {
    mockHttpPost(URL(url))
    mockRequestBuilderWithBody(requestBody)
    if hasHeaders then mockRequestBuilderTransform()
    mockRequestBuilderExecuteWithoutException(response)
  }

  def mockHttpPutSuccess[A](url: String, requestBody: JsValue, hasHeaders: Boolean = false)(response: A) = {
    mockHttpPut(URL(url))
    mockRequestBuilderWithBody(requestBody)
    if hasHeaders then mockRequestBuilderTransform()
    mockRequestBuilderExecuteWithoutException(response)
  }

  def mockHttpDeleteSuccess[A](url: String, hasHeaders: Boolean = false)(response: A) = {
    mockHttpDelete(URL(url))
    if hasHeaders then mockRequestBuilderTransform()
    mockRequestBuilderExecuteWithoutException(response)
  }

  def mockHttpDeleteSuccesses[A](url: String, hasHeaders: Boolean = false)(responses: Seq[A]) = {
    mockHttpDelete(URL(url))
    if hasHeaders then mockRequestBuilderTransform()
    mockRequestBuilderExecuteWithoutException(responses)
  }

  def mockHttpPostWithException(
    url: String,
    requestBody: JsValue,
    hasHeaders: Boolean = false
  )(exception: Exception) = {
    mockHttpPost(URL(url))
    mockRequestBuilderWithBody(requestBody)
    if hasHeaders then mockRequestBuilderTransform()
    mockRequestBuilderExecuteWithException(exception)
  }

  def mockHttpPostStringSuccess[A](url: String, requestBody: String)(response: A) = {
    mockHttpPost(URL(url))
    mockRequestBuilderWithString(requestBody)
    mockRequestBuilderExecuteWithoutException(response)
  }

  def mockHttpPostStringWithException(url: String, requestBody: String, exception: Exception) = {
    mockHttpPost(URL(url))
    mockRequestBuilderWithString(requestBody)
    mockRequestBuilderTransform()
    mockRequestBuilderExecuteWithException(exception)
  }

  def mockHttpGetSuccess[A](
    url: URL
  )(response: A) = {
    mockHttpGet[A](url)
    mockRequestBuilderExecuteWithoutException[A](response)
  }

  def mockHttpGetSuccesses[A](
    url: URL
  )(responses: Seq[A]) = {
    mockHttpGet[A](url)
    mockRequestBuilderExecuteWithoutException[A](responses)
  }

  def mockHttpGetFailure(
    url: URL
  )(exception: Exception) = {
    mockHttpGet(url)
    mockRequestBuilderExecuteWithException(exception)
  }

  def mockHttpPatchSuccess[A](url: URL, hasHeaders: Boolean = false)(response: A) = {
    mockHttpPatch(url)
    if hasHeaders then mockRequestBuilderTransform()
    mockRequestBuilderExecuteWithoutException(response)
  }

  def mockHttpPatchSuccesses[A](url: URL, hasHeaders: Boolean = false)(responses: Seq[A]) = {
    mockHttpPatch(url)
    if hasHeaders then mockRequestBuilderTransform()
    mockRequestBuilderExecuteWithoutException(responses)
  }

  private def mockHttpGet[A](url: URL) = when(mockHttp.get(eqTo(url))(using any())).thenReturn(mockRequestBuilder)

  def mockHttpPost[A](url: URL) = when(mockHttp.post(eqTo(url))(using any())).thenReturn(mockRequestBuilder)

  def mockHttpPut[A](url: URL) = when(mockHttp.put(eqTo(url))(using any())).thenReturn(mockRequestBuilder)

  def mockHttpDelete[A](url: URL) = when(mockHttp.delete(eqTo(url))(using any())).thenReturn(mockRequestBuilder)

  def mockHttpPatch[A](url: URL) = when(mockHttp.patch(eqTo(url))(using any())).thenReturn(mockRequestBuilder)

  def mockRequestBuilderWithBody[JsValue](
    body: JsValue
  ) =
    when(mockRequestBuilder.withBody(eqTo(body))(using any(), any(), any())).thenReturn(mockRequestBuilder)

  def mockRequestBuilderWithString(body: String) =
    when(mockRequestBuilder.withBody(eqTo(body))(using any(), any(), any())).thenReturn(mockRequestBuilder)

  def mockRequestBuilderExecuteWithoutException[A](value: A): Unit = {
    when(mockRequestBuilder.execute(using any(), any())).thenReturn(Future.successful(value))
    ()
  }

  def mockRequestBuilderExecuteWithoutException[A](values: Seq[A]): Unit =
    values match {
      case h +: Nil => mockRequestBuilderExecuteWithoutException(h)
      case h +: t   =>
        when(mockRequestBuilder.execute(using any(), any())).thenReturn(Future.successful(h), t.map(Future.successful)*)
        ()
    }

  def mockRequestBuilderTransform() = when(mockRequestBuilder.transform(any())).thenReturn(mockRequestBuilder)

  def mockRequestBuilderExecuteWithException[A](ex: Exception) =
    when(mockRequestBuilder.execute(using any(), any())).thenReturn(Future.failed(ex))

}
