/*
 * Copyright 2016 HM Revenue & Customs
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

import java.io.File
import java.nio.file.{Files, Path, Paths}

import org.joda.time.DateTime
import org.scalatest.Failed
import uk.gov.hmrc.releaser.RepoConnector.RepoConnectorBuilder
import uk.gov.hmrc.releaser.domain._
import uk.gov.hmrc.releaser.github.GithubTagAndRelease

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object Builders {

  import RepoFlavours._

  def mavenVersionMapping(
                           artefactName:String = "a",
                           repoName:String = "a",
                           rcVersion:String = aReleaseCandidateVersion.value,
                           releaseVersion:String = aReleaseVersion.value) ={
    VersionMapping(
      RepoFlavours.mavenRepository,
      artefactName,
      Repo(repoName),
      ReleaseCandidateVersion(rcVersion),
      ReleaseVersion(releaseVersion))
  }

  def buildMetaConnector() = new MetaConnector(){
    override def getRepoMetaData(repoName: String, artefactName: String): Try[Unit] = {
      Success(Unit)
    }

    override def publish(version: VersionDescriptor): Try[Unit] = {
      Success(Unit)
    }
  }

  def successfulArtefactMetaDataFinder(artefactMetaData:ArtefactMetaData):(Path) => Try[ArtefactMetaData] = {
    (x) => Success(artefactMetaData)
  }

  def failingRepoFinder(e:Exception):((String) => Try[RepoFlavour])={
    (a) => Failure(e)
  }

  def successfulRepoFinder(repo: RepoFlavour):((String) => Try[RepoFlavour])={
    (a) => Success(repo)
  }

  val successfulRepoFinder:((String) => Try[RepoFlavour])={
    (a) => Success(mavenRepository)
  }

  val successfulConnectorBuilder: RepoConnectorBuilder = (r) => Builders.buildConnector(
    filesuffix = "",
    Some("/time/time_2.11-1.3.0-1-g21312cc.jar"),
    Set("/time/time_2.11-1.3.0-1-g21312cc.pom")
  )

  val aReleaseCandidateVersion = ReleaseCandidateVersion("1.3.0-1-g21312cc")
  val aReleaseVersion = ReleaseVersion("1.3.1")
  val anArtefactMetaData = new ArtefactMetaData("803749", "ck", DateTime.now())

  class DummyTagAndRelease extends GithubTagAndRelease {
    override def createGithubTagAndRelease(tagDate: DateTime, commitSha: CommitSha, commitAuthor: String, commitDate: DateTime, map: VersionMapping): Try[Unit] = Success(Unit)
    override def verifyGithubTagExists(repo: Repo, sha: CommitSha): Try[Unit] = Success(Unit)
  }

  def buildDefaultReleaser(stageDir:Path = tempDir(),
                        repositoryFinder:(String) => Try[RepoFlavour] = successfulRepoFinder,
                        connectorBuilder:(RepoFlavour) => RepoConnector = successfulConnectorBuilder,
                        artefactMetaData:ArtefactMetaData = ArtefactMetaData("sha", "project", DateTime.now()),
                        taggerAndReleaser: GithubTagAndRelease = new DummyTagAndRelease): Releaser = {

    val coord = new Coordinator(stageDir, (x) => Success(artefactMetaData), taggerAndReleaser)
    new Releaser(stageDir, repositoryFinder, connectorBuilder, coord)
  }

  def resource(path:String):Path={
    new File(this.getClass.getClassLoader.getResource(path).toURI).toPath
  }

  def tempDir() = Files.createTempDirectory("tmp")

  def buildConnector(filesuffix:String, jarResoure:Option[String], bintrayFiles:Set[String], targetExists:Boolean = false) = new RepoConnector(){

    val uploadedFiles = mutable.Set[(VersionDescriptor, Path)]()
    var lastPublishDescriptor:Option[VersionDescriptor] = None

    override def findJar(version: VersionDescriptor): Option[Path] =
      jarResoure.map { x =>  Paths.get(this.getClass.getResource(filesuffix + x).toURI) }

    override def publish(version: VersionDescriptor): Try[Unit] = {
      lastPublishDescriptor = Some(version)
      Success(Unit)
    }

    override def findFiles(version: VersionDescriptor): Try[List[String]] = Success(bintrayFiles.toList ++ jarResoure)

    override def downloadFile(version: VersionDescriptor, fileName: String): Try[Path] = {
      Success {
        Paths.get(this.getClass.getResource(filesuffix + fileName).toURI)
      }
    }

    override def uploadFile(version: VersionDescriptor, filePath: Path): Try[Unit] = {
      uploadedFiles.add((version, filePath))
      Success(Unit)
    }

    override def verifyTargetDoesNotExist(version: VersionDescriptor): Try[Unit] = targetExists match {
      case true => Failure(new IllegalArgumentException("Failed in test"))
      case false => Success(Unit)
    }
  }

}
