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

import java.io.File
import java.nio.file.{Files, Path, Paths}

import org.joda.time.DateTime
import uk.gov.hmrc.releaser.ArtifactType.ArtifactType
import uk.gov.hmrc.releaser.domain._

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object Builders {

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
    (a) => Success(RepoFlavours.mavenRepository)
  }

  val successfulConnectorBuilder:(RepoFlavour) => RepoConnector = (r) => Builders.buildConnector(
    "/time/time_2.11-1.3.0-1-g21312cc.jar",
    "/time/time_2.11-1.3.0-1-g21312cc.pom"
  )

  val aReleaseCandidateVersion = ReleaseCandidateVersion("1.3.0-1-g21312cc")
  val aReleaseVersion = ReleaseVersion("1.3.1")
  val anArtefactMetaData = new ArtefactMetaData("803749", "ck", DateTime.now())

  val artifactClassifiers = Artifacts.buildSupportedArtifacts()

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
    val classifiersBuilder = Artifacts.buildSupportedArtifacts _
    new Releaser(stageDir, repositoryFinder, connectorBuilder, classifiersBuilder, coord)
  }

  def resource(path:String):Path={
    new File(this.getClass.getClassLoader.getResource(path).toURI).toPath
  }

  def tempDir() = Files.createTempDirectory("tmp")

  def buildDefaultCoordinator(
                               stageDir:Path,
                               artefactMetaData:ArtefactMetaData = ArtefactMetaData("sha", "project", DateTime.now()),
                               githubRepoGetter:(Repo, CommitSha) => Try[Unit] = successfulGithubVerifier,
                               githubReleasePublisher:(ArtefactMetaData, VersionMapping) => Try[Unit] = successfulGithubReleasePublisher,
                               githubTagObjectPublisher:(Repo, ReleaseVersion, CommitSha) => Try[CommitSha] = successfulGithubTagObjectPublisher,
                               githubTagRefPublisher:(Repo, ReleaseVersion, CommitSha) => Try[Unit] = successfulGithubTagRefPublisher
                               )={

    val taggerAndReleaser = Releaser.createGitHubTagAndRelease(githubTagObjectPublisher, githubTagRefPublisher, githubReleasePublisher) _
    val artefactBuilder = successfulArtefactBulider(artefactMetaData)
    new Coordinator(stageDir, artefactBuilder, githubRepoGetter, taggerAndReleaser)
  }

  def buildConnector(jarResource:String, pomResource:String, srcResource: Option[String] = None,
                     docsResource: Option[String] = None, tgzResource: Option[String] = None) = new RepoConnector(){

    val lastUploadedArtifacts: mutable.Map[ArtifactType, (VersionDescriptor, Path)] = mutable.Map()
    var lastPublishDescriptor:Option[VersionDescriptor] = None

    override def publish(version: VersionDescriptor): Try[Unit] = {
      lastPublishDescriptor = Some(version)
      Success(Unit)
    }

    override def uploadArtifacts(versions: Seq[VersionDescriptor], localFiles: Map[ArtifactClassifier, Path]): Try[Unit] = {
      versions.foreach { v =>
        localFiles.get(v.classifier).map { localFile =>
          lastUploadedArtifacts += v.classifier.artifactType -> (v -> localFile)
        }
      }
      Success(Unit)
    }

    override def downloadArtifacts(versions: Seq[VersionDescriptor]): Try[Map[ArtifactClassifier, Path]] = {
      Success {
        versions.map { v =>
          val filename = v.classifier.artifactType match {
            case ArtifactType.JAR => Some(jarResource)
            case ArtifactType.POM => Some(pomResource)
            case ArtifactType.SOURCE_JAR => srcResource
            case ArtifactType.DOC_JAR => docsResource
            case ArtifactType.TGZ => tgzResource
            case _ => throw new Exception("Unsupported artifact type")
          }
          filename.map( fn => v.classifier -> Paths.get(this.getClass.getResource(fn).toURI))
        }.flatten.toMap
      }
    }
    
  }

}
