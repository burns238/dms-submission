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

package controllers

import models.submission.{SubmissionMetadata, SubmissionRequest}
import org.scalactic.source.Position
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Configuration

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}

class SubmissionFormProviderSpec extends AnyFreeSpec with Matchers with OptionValues {

  private val configuration = Configuration("allow-localhost-callbacks" -> false)
  private val form = new SubmissionFormProvider(configuration).form

  private val timeOfReceipt = LocalDateTime.of(2022, 2, 1, 0, 0, 0)
  private val completeRequest = SubmissionRequest(
    submissionReference = Some("submissionReference"),
    callbackUrl = "http://test-service.protected.mdtp/callback",
    metadata = SubmissionMetadata(
      store = false,
      source = "source",
      timeOfReceipt = timeOfReceipt.toInstant(ZoneOffset.UTC),
      formId = "formId",
      customerId = "customerId",
      submissionMark = "submissionMark",
      casKey = "casKey",
      classificationType = "classificationType",
      businessArea = "businessArea"
    )
  )

  private val completeData = Map(
    "submissionReference" -> "submissionReference",
    "callbackUrl" -> "http://test-service.protected.mdtp/callback",
    "metadata.store" -> "false",
    "metadata.source" -> "source",
    "metadata.timeOfReceipt" -> DateTimeFormatter.ISO_DATE_TIME.format(timeOfReceipt),
    "metadata.formId" -> "formId",
    "metadata.customerId" -> "customerId",
    "metadata.submissionMark" -> "submissionMark",
    "metadata.casKey" -> "casKey",
    "metadata.classificationType" -> "classificationType",
    "metadata.businessArea" -> "businessArea"
  )

  "must return a `SubmissionRequest` when given valid input" in {
    form.bind(completeData).value.value mustEqual completeRequest
  }

  "submissionReference" - {

    "must being `None` if there is no submissionReference" in {
      form.bind(completeData - "submissionReference").value.value.submissionReference mustBe None
    }

    "must bind `None` if submissionReference is an empty string" in {
      form.bind(completeData + ("submissionReference" -> "")).value.value.submissionReference mustBe None
    }
  }

  "callbackUrl" - {
    behave like requiredField("callbackUrl")

    "must fail if the value is not a valid url" in {
      val boundField = form.bind(completeData + ("callbackUrl" -> "foobar"))("callbackUrl")
      boundField.error.value.message mustEqual "callbackUrl.invalid"
    }

    "must fail if the domain doesn't end in .mdtp" in {
      val boundField = form.bind(completeData + ("callbackUrl" -> "http://localhost/callback"))("callbackUrl")
      boundField.error.value.message mustEqual "callbackUrl.invalidHost"
    }

    "must succeed when the domain is localhost, when `allow-localhost-callbacks` is enabled" in {
      val configuration = Configuration("allow-localhost-callbacks" -> true)
      val form = new SubmissionFormProvider(configuration).form
      val boundField = form.bind(completeData + ("callbackUrl" -> "http://localhost/callback"))("callbackUrl")
      boundField.hasErrors mustEqual false
    }
  }

  "metadata.store" - {

    behave like requiredField("metadata.store")

    "must fail if it is invalid" in {
      val boundField = form.bind(Map("metadata.store" -> "foobar"))("metadata.store")
      boundField.hasErrors mustEqual true
    }
  }

  "metadata.source" - {
    behave like requiredField("metadata.source")
  }

  "metadata.timeOfReceipt" - {

    "must bind a time with nanos" in {
      val timeOfReceipt = LocalDateTime.of(2020, 2, 1, 12, 30, 20, 1337)
      val boundField = form.bind(completeData + ("metadata.timeOfReceipt" -> DateTimeFormatter.ISO_DATE_TIME.format(timeOfReceipt)))
      boundField.hasErrors mustEqual false
    }

    behave like requiredField("metadata.timeOfReceipt")

    "must fail if it is invalid" in {
      val boundField = form.bind(Map("metadata.timeOfReceipt" -> "foobar"))("metadata.timeOfReceipt")
      boundField.hasErrors mustEqual true
    }
  }

  "metadata.formId" - {
    behave like requiredField("metadata.formId")
  }

  "metadata.customerId" - {
    behave like requiredField("metadata.customerId")
  }

  "metadata.submissionMark" - {
    behave like requiredField("metadata.submissionMark")
  }

  "metadata.casKey" - {
    behave like requiredField("metadata.casKey")
  }

  "metadata.classificationType" - {
    behave like requiredField("metadata.classificationType")
  }

  "metadata.businessArea" - {
    behave like requiredField("metadata.businessArea")
  }

  private def requiredField(key: String)(implicit pos: Position): Unit = {
    "must fail if it isn't provided" in {
      val boundField = form.bind(Map.empty[String, String])(key)
      boundField.hasErrors mustEqual true
    }
  }
}
