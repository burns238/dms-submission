/*
 * Copyright 2022 HM Revenue & Customs
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

package connectors

import models.Done
import models.submission.{NotificationRequest, SubmissionItem}
import play.api.http.Status.OK
import play.api.libs.json.Json
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

@Singleton
class CallbackConnector @Inject() (
                                    httpClient: HttpClientV2
                                  )(implicit ec: ExecutionContext) {

  def notify(submissionItem: SubmissionItem): Future[Done] = {
    val hc = HeaderCarrier()
    httpClient.post(url"${submissionItem.callbackUrl}")(hc)
      .withBody(Json.toJson(createRequest(submissionItem)))
      .execute
      .flatMap { response =>
        if (response.status == OK) {
          Future.successful(Done)
        } else {
          Future.failed(CallbackConnector.UnexpectedResponseException(response.status, response.body))
        }
      }
  }

  private def createRequest(item: SubmissionItem): NotificationRequest =
    NotificationRequest(
      correlationId = item.id,
      status = item.status,
      objectSummary = item.objectSummary,
      failureReason = item.failureReason
    )
}

object CallbackConnector {

  final case class UnexpectedResponseException(status: Int, body: String) extends Exception with NoStackTrace {
    override def getMessage: String = s"Unexpected response from callback, status: $status, body: $body"
  }
}