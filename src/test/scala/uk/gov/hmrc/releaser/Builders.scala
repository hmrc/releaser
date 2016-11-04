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
import uk.gov.hmrc.releaser.domain._

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object Builders extends GitTagAndRelease {

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

  def successfulArtefactBulider(artefactMetaData:ArtefactMetaData):(Path) => Try[ArtefactMetaData] = {
    (x) => Success(artefactMetaData)
  }
  
  val successfulGithubVerifier:(Repo, CommitSha) => Try[Unit] ={
    (a, b) => Success(Unit)
  }

  val successfulGithubReleasePublisher:(ArtefactMetaData, VersionMapping) => Try[Unit] = {
    (a, b) => Success()
  }

  val successfulGithubTagObjectPublisher:(Repo, ReleaseVersion, CommitSha) => Try[CommitSha] ={
    (a, b, c) => Success("some-tag-sha")
  }

  val successfulGithubTagRefPublisher:(Repo, ReleaseVersion, CommitSha) => Try[Unit] ={
    (a, b, c) => Success(Unit)
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

  val successfulConnectorBuilder:(RepoFlavour) => RepoConnector = (r) => Builders.buildConnector(
    filesuffix = "",
    "/time/time_2.11-1.3.0-1-g21312cc.jar",
    Set("/time/time_2.11-1.3.0-1-g21312cc.pom")
  )

  val aReleaseCandidateVersion = ReleaseCandidateVersion("1.3.0-1-g21312cc")
  val aReleaseVersion = ReleaseVersion("1.3.1")
  val anArtefactMetaData = new ArtefactMetaData("803749", "ck", DateTime.now())

  def buildDefaultReleaser(
                        stageDir:Path = tempDir(),
                        repositoryFinder:(String) => Try[RepoFlavour] = successfulRepoFinder,
                        connectorBuilder:(RepoFlavour) => RepoConnector = successfulConnectorBuilder,
                        artefactMetaData:ArtefactMetaData = ArtefactMetaData("sha", "project", DateTime.now()),
                        githubRepoGetter:(Repo, CommitSha) => Try[Unit] = successfulGithubVerifier,
                        githubReleasePublisher:(ArtefactMetaData, VersionMapping) => Try[Unit] = successfulGithubReleasePublisher,
                        githubTagObjPublisher:(Repo, ReleaseVersion, CommitSha) => Try[CommitSha] = successfulGithubTagObjectPublisher,
                        githubTagRefPublisher:(Repo, ReleaseVersion, CommitSha) => Try[Unit] = successfulGithubTagRefPublisher
                          ): Releaser ={
    val coord = buildDefaultCoordinator(stageDir, artefactMetaData, githubRepoGetter, githubReleasePublisher, githubTagObjPublisher, githubTagRefPublisher)
    new Releaser(stageDir, repositoryFinder, connectorBuilder, coord)
  }

  def resource(path:String):Path={
    new File(this.getClass.getClassLoader.getResource(path).toURI).toPath
  }

  def tempDir() = Files.createTempDirectory("tmp")


  def buildDefaultCoordinator(
                               stageDir:Path = tempDir(),
                               artefactMetaData:ArtefactMetaData = ArtefactMetaData("sha", "project", DateTime.now()),
                               githubRepoGetter:(Repo, CommitSha) => Try[Unit] = successfulGithubVerifier,
                               githubReleasePublisher:(ArtefactMetaData, VersionMapping) => Try[Unit] = successfulGithubReleasePublisher,
                               githubTagObjectPublisher:(Repo, ReleaseVersion, CommitSha) => Try[CommitSha] = successfulGithubTagObjectPublisher,
                               githubTagRefPublisher:(Repo, ReleaseVersion, CommitSha) => Try[Unit] = successfulGithubTagRefPublisher
                               )={

    val taggerAndReleaser = createGitHubTagAndRelease(githubTagObjectPublisher, githubTagRefPublisher, githubReleasePublisher) _
    val artefactBuilder = successfulArtefactBulider(artefactMetaData)
    new Coordinator(stageDir, artefactBuilder, githubRepoGetter, taggerAndReleaser)
  }

  def buildConnector(filesuffix:String, jarResoure:String, bintrayFiles:Set[String], targetExists:Boolean = false) = new RepoConnector(){

    val uploadedFiles = mutable.Set[(VersionDescriptor, Path)]()

    var lastPublishDescriptor:Option[VersionDescriptor] = None

    override def downloadJar(version: VersionDescriptor): Try[Path] = {
      Success {
        Paths.get(this.getClass.getResource(filesuffix + jarResoure).toURI) }
    }

    override def publish(version: VersionDescriptor): Try[Unit] = {
      lastPublishDescriptor = Some(version)
      Success(Unit)
    }

    override def findFiles(version: VersionDescriptor): Try[List[String]] = Success(bintrayFiles.toList :+ jarResoure)

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
