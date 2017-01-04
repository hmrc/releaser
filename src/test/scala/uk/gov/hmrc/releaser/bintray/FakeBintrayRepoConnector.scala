/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.releaser.bintray

import java.nio.file.{Path, Paths}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

class FakeBintrayRepoConnector(filesuffix:String  = "",
                               jarResource:Option[String],
                               bintrayFiles:Set[String],
                               targetExists:Boolean = false) extends BintrayRepoConnector {

  val downloadedFiles = mutable.Set[String]()
  val uploadedFiles = mutable.Set[(VersionDescriptor, Path, String)]()
  var lastPublishDescriptor: Option[VersionDescriptor] = None

  override def findJar(jarFileName: String, jarUrl: String, version: VersionDescriptor): Option[Path] =
    jarResource.map { x => Paths.get(this.getClass.getResource(filesuffix + x).toURI) }

  override def publish(version: VersionDescriptor): Try[Unit] = {
    lastPublishDescriptor = Some(version)
    Success(Unit)
  }

  override def findFiles(version: VersionDescriptor): Try[List[String]] = Success(bintrayFiles.toList ++ jarResource)

  override def downloadFile(url: String, fileName: String): Try[Path] = {
    downloadedFiles.add(url)
    Success {
      Paths.get(this.getClass.getResource(filesuffix + fileName).toURI)
    }
  }

  override def uploadFile(version: VersionDescriptor, filePath: Path, url: String): Try[Unit] = {
    uploadedFiles.add((version, filePath, url))
    Success(Unit)
  }

  override def verifyTargetDoesNotExist(version: VersionDescriptor): Try[Unit] = targetExists match {
    case true => Failure(new IllegalArgumentException("Failed in test"))
    case false => Success(Unit)
  }

  override def getRepoMetaData(repoName: String, artefactName: String): Try[Unit] = Success(Unit)
}
