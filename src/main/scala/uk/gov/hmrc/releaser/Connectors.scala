/*
 * Copyright 2015 HM Revenue & Customs
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

package uk.gov.hmrc.releaser

import java.net.URL
import java.nio.file.Path

import uk.gov.hmrc.releaser.domain.VersionDescriptor

import scala.sys.process._
import scala.util.Try

trait RepoConnector{
  def uploadJar(version: VersionDescriptor, jarFile:Path):Try[Unit]
  def downloadJar(version:VersionDescriptor):Try[Path]
  def uploadPom(version: VersionDescriptor, pomPath:Path):Try[Unit]
  def downloadPom(version:VersionDescriptor):Try[Path]
  def publish(version: VersionDescriptor):Try[Unit]
}


trait MetaConnector{

  def getRepoMetaData(repoName:String, artefactName: String):Try[Unit]

  def publish(version: VersionDescriptor):Try[Unit]

}


object Http{

  val log = new Logger()

  def url2File(url: String, targetFile: Path): Try[Unit] = Try {
    log.info(s"downloading $url to $targetFile")
    new URL(url) #> targetFile.toFile !!
  }
}
