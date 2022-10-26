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

import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import better.files.File
import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class FileService @Inject() (
                              objectStoreClient: PlayObjectStoreClient,
                              configuration: Configuration
                            ) {

  private val tmpDir: File = File(configuration.get[String]("play.temporaryFile.dir"))

  def workDir(): File = File.newTemporaryDirectory(parent = Some(tmpDir))

  def createZip(workDir: File, pdf: File): File = {
    val tmpDir = File.newTemporaryDirectory(parent = Some(workDir))
    pdf.copyTo(tmpDir / "iform.pdf")
    val zip = File.newTemporaryFile(parent = Some(workDir))
    tmpDir.zipTo(zip)
  }
}
