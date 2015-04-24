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
import java.nio.file.{Paths, Path}

import scala.util.{Success, Try}

object Builders {

  def buildMetaConnector() = new MetaConnector(){
    override def getRepoMetaData(repoName: String, artefactName: String): Try[Unit] = {
      Success(Unit)
    }

    override def publish(version: VersionDescriptor): Try[Unit] = {
      Success(Unit)
    }
  }

  def buildRepositories(repo:RepoFlavour): Repositories = new Repositories{
      override def findReposOfArtefact(artefactName: String): Try[RepoFlavour] = Success(repo)

      override def connector: BintrayMetaConnector = ???
      override def repos: Seq[RepoFlavour] = ???
  }

  def buildConnector(jarResoure:String, pomResource:String) = new RepoConnector(){

    var lastUploadedJar:Option[(VersionDescriptor, Path)] = None
    var lastUploadedPom:Option[(VersionDescriptor, Path)] = None
    var lastPublishDescriptor:Option[VersionDescriptor] = None

    override def downloadJar(version: VersionDescriptor): Try[Path] = {
      Success {
        Paths.get(this.getClass.getResource(jarResoure).toURI) }
    }

    override def uploadJar(version: VersionDescriptor, jarFile: Path): Try[URL] = {
      lastUploadedJar = Some(version -> jarFile)
      Success(new URL("http://the-url-we-uploaded-to.org"))
    }

    override def uploadPom(version: VersionDescriptor, file: Path): Try[URL] = {
      lastUploadedPom = Some(version -> file)
      Success(new URL("http://the-url-we-uploaded-to.org"))
    }

    override def downloadPom(version: VersionDescriptor): Try[Path] = {
      Success {
        Paths.get(this.getClass.getResource(pomResource).toURI) }
    }

    override def publish(version: VersionDescriptor): Try[Unit] = {
      lastPublishDescriptor = Some(version)
      Success(Unit)
    }
  }

}
