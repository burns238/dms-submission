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

package services

import akka.stream.scaladsl.Source
import akka.util.ByteString
import better.files.File
import models.{Done, SubmissionRequest}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Md5Hash, ObjectSummaryWithMd5, Path}

import java.time.Instant
import scala.concurrent.Future

class SubmissionServiceSpec extends AnyFreeSpec with Matchers
  with ScalaFutures with MockitoSugar with OptionValues with BeforeAndAfterEach
  with IntegrationPatience {

  private val mockObjectStoreClient = mock[PlayObjectStoreClient]
  private val mockFileService = mock[FileService]
  private val mockSdesService = mock[SdesService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      mockObjectStoreClient,
      mockFileService,
      mockSdesService
    )
  }

  "submit" - {

    val app = new GuiceApplicationBuilder()
      .overrides(
        bind[PlayObjectStoreClient].toInstance(mockObjectStoreClient),
        bind[FileService].toInstance(mockFileService),
        bind[SdesService].toInstance(mockSdesService)
      )
      .build()

    val service = app.injector.instanceOf[SubmissionService]

    val hc: HeaderCarrier = HeaderCarrier()
    val request = SubmissionRequest("")

    val pdf = File.newTemporaryFile()
      .deleteOnExit()
      .write("Hello, World!")
    val zip = File.newTemporaryFile()
      .deleteOnExit()
      .write("Some bytes")

    "must create a zip file of the contents of the request along with a metadata xml for routing, upload to object-store and notify SDES" in {
      val workDir = File.newTemporaryDirectory()
      val objectSummary = ObjectSummaryWithMd5(
        location = Path.File("file"),
        contentLength = 0L,
        contentMd5 = Md5Hash("hash"),
        lastModified = Instant.now()
      )

      when(mockFileService.workDir()).thenReturn(workDir)
      when(mockFileService.createZip(workDir, pdf)).thenReturn(zip)
      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(objectSummary))
      when(mockSdesService.notify(any(), any())(any())).thenReturn(Future.successful(Done))

      service.submit(request, pdf)(hc).futureValue

      verify(mockObjectStoreClient).putObject(eqTo(Path.File("file")), eqTo(zip.path.toFile), any(), any(), any(), any())(any(), any())
      verify(mockSdesService).notify(eqTo(objectSummary), any())(any())

      eventually {
        workDir.exists() mustEqual false
      }
    }

    "must fail when the file service fails to create a work directory" in {
      when(mockFileService.workDir()).thenThrow(new RuntimeException())
      service.submit(request, pdf)(hc).failed.futureValue
    }

    "must fail when the fail service fails to create a zip file" in {
      val workDir = File.newTemporaryDirectory()
      when(mockFileService.workDir()).thenReturn(workDir)
      when(mockFileService.createZip(workDir, pdf)).thenThrow(new RuntimeException())
      service.submit(request, pdf)(hc).failed.futureValue
      eventually {
        workDir.exists() mustEqual false
      }
    }

    "must fail when object store fails" in {
      val workDir = File.newTemporaryDirectory()
      when(mockFileService.workDir()).thenReturn(workDir)
      when(mockFileService.createZip(workDir, pdf)).thenReturn(zip)
      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenThrow(new RuntimeException())
      service.submit(request, pdf)(hc).failed.futureValue
      eventually {
        workDir.exists() mustEqual false
      }
    }

    "must fail when sdes fails" in {
      val workDir = File.newTemporaryDirectory()
      val objectSummary = ObjectSummaryWithMd5(
        location = Path.File("file"),
        contentLength = 0L,
        contentMd5 = Md5Hash("hash"),
        lastModified = Instant.now()
      )
      when(mockFileService.workDir()).thenReturn(workDir)
      when(mockFileService.createZip(workDir, pdf)).thenReturn(zip)
      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(objectSummary))
      when(mockSdesService.notify(any(), any())(any())).thenReturn(Future.failed(new RuntimeException()))
      service.submit(request, pdf)(hc).failed.futureValue
      eventually {
        workDir.exists() mustEqual false
      }
    }
  }
}