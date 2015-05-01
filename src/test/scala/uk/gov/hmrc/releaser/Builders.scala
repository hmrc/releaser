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
import java.nio.file.{Files, Paths, Path}

import org.joda.time.DateTime
import uk.gov.hmrc.releaser.domain._

import scala.util.{Success, Try}

object Builders {

  import RepoFlavours._

  def buildMetaConnector() = new MetaConnector(){
    override def getRepoMetaData(repoName: String, artefactName: String): Try[Unit] = {
      Success(Unit)
    }

    override def publish(version: VersionDescriptor): Try[Unit] = {
      Success(Unit)
    }
  }

  def successfulArtefactBulider(artefactMetaData:ArtefactMetaData):(Path) => Try[ArtefactMetaData] = {
    (x) => Success(artefactMetaData)
  }
  
  val successfulGithubVerifier:(String, String) => Try[Unit] ={
    (a, b) => Success()
  }

  val successfulGithubTagPublisher:(ArtefactMetaData, VersionMapping) => Try[Unit] ={
    (a, b) => Success()
  }

  def successfulRepoFinder(repo: RepoFlavour):((String) => Try[RepoFlavour])={
    (a) => Success(repo)
  }

  val successfulRepoFinder:((String) => Try[RepoFlavour])={
    (a) => Success(mavenRepository)
  }

  val successfulConnectorBuilder:(RepoFlavour) => RepoConnector = (r) => Builders.buildConnector(
    "/time/time_2.11-1.3.0-1-g21312cc.jar",
    "/time/time_2.11-1.3.0-1-g21312cc.pom"
  )

  def buildDefaultReleaser(
                        stageDir:Path = tempDir(),
                        repositoryFinder:(String) => Try[RepoFlavour] = successfulRepoFinder,
                        connectorBuilder:(RepoFlavour) => RepoConnector = successfulConnectorBuilder,
                        artefactMetaData:ArtefactMetaData = ArtefactMetaData("sha", "project", DateTime.now()),
                        githubRepoGetter:(String, String) => Try[Unit] = successfulGithubVerifier,
                        githubTagPublisher:(ArtefactMetaData, VersionMapping) => Try[Unit] = successfulGithubTagPublisher
                          ): Releaser ={
    val coord = buildDefaultCoordinator(stageDir, artefactMetaData, githubRepoGetter, githubTagPublisher)
    new Releaser(stageDir, repositoryFinder, connectorBuilder, coord)
  }


  def tempDir() = Files.createTempDirectory("tmp")


  def buildDefaultCoordinator(
                               stageDir:Path,
                               artefactMetaData:ArtefactMetaData = ArtefactMetaData("sha", "project", DateTime.now()),
                               githubRepoGetter:(String, String) => Try[Unit] = successfulGithubVerifier,
                               githubTagPublisher:(ArtefactMetaData, VersionMapping) => Try[Unit] = successfulGithubTagPublisher
                               )={

    val artefactBuilder = successfulArtefactBulider(artefactMetaData)
    new Coordinator(stageDir, artefactBuilder, githubRepoGetter, githubTagPublisher)
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
