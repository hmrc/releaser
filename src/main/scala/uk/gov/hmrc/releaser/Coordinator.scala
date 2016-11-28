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

import java.nio.file.{Files, Path}

import org.joda.time.DateTime
import uk.gov.hmrc.Logger
import uk.gov.hmrc.releaser.RepoFlavours._
import uk.gov.hmrc.releaser.bintray.{BintrayRepoConnector, VersionDescriptor}
import uk.gov.hmrc.releaser.github.{GithubTagAndRelease, Repo}

import scala.util.{Failure, Success, Try}

class Coordinator(stageDir: Path,
                  metaDataProvider: MetaDataProvider,
                  githubConnector: GithubTagAndRelease,
                  bintrayConnector: BintrayRepoConnector) extends Logger {

  val repositoryFlavors = Seq(mavenRepository, ivyRepository)

  def start(artefactName:String,
            gitRepo:Repo,
            releaseCandidateVersion: ReleaseCandidateVersion,
            releaseType: ReleaseType.Value): Try[ReleaseVersion] = {

    for {
      targetVersion <- VersionNumberCalculator.calculateTarget(releaseCandidateVersion, releaseType)
      repo <- findReposOfArtefact(artefactName)
      map = VersionMapping(repo, artefactName, gitRepo, releaseCandidateVersion, targetVersion)
      jarUrl = repo.jarDownloadFor(map.targetArtefact)
      _ <- bintrayConnector.verifyTargetDoesNotExist(jarUrl, map.targetArtefact)
      artefacts = map.repo.artefactBuilder(map, stageDir)
      files <- bintrayConnector.findFiles(map.sourceArtefact)
      remotes = artefacts.transformersForSupportedFiles(files)
      (commitSha, commitAuthor, commitDate) <- getMetaData(repo, map, files).map(x => ArtefactMetaData.unapply(x).get)
      _ <- githubConnector.verifyGithubTagExists(map.gitRepo, commitSha)
      transd <- transformFiles(repo, map, remotes, artefacts.filePrefix)
      _ <- uploadFiles(repo, map.targetArtefact, transd)
      _ <- bintrayConnector.publish(map.targetArtefact)
      _ <- githubConnector.createGithubTagAndRelease(new DateTime(), commitSha, commitAuthor, commitDate, artefactName, gitRepo, releaseCandidateVersion.value, targetVersion.value)
    }
     yield targetVersion
  }

  private def findReposOfArtefact(artefactName: ArtefactName): Try[RepoFlavour] = {
    repositoryFlavors.find { repo =>
      bintrayConnector.getRepoMetaData(repo.releaseCandidateRepo, artefactName).isSuccess
    } match {
      case Some(r) => Success(r)
      case None => Failure(new Exception(s"Didn't find a release candidate repository for '$artefactName' in repos ${repositoryFlavors.map(_.releaseCandidateRepo)}"))
    }
  }

  private def getMetaData(repo: RepoFlavour, map: VersionMapping, files: List[String]): Try[ArtefactMetaData] = {
    val jarUrl = repo.jarDownloadFor(map.sourceArtefact)
    val jarFileName = repo.jarFilenameFor(map.sourceArtefact)

    bintrayConnector.findJar(jarFileName, jarUrl, map.sourceArtefact) match {
      case Some(path) => metaDataProvider.fromJarFile(path)
      case None =>
        val commitManifestUrl = repo.fileDownloadFor(map.sourceArtefact, "commit.mf")

        for {
          commitManifest <- bintrayConnector.downloadFile(commitManifestUrl, s"/${map.artefactName}/commit.mf")
          metaData <- metaDataProvider.fromCommitManifest(commitManifest)
        }
        yield metaData
    }
  }

  private def uploadFiles(repo: RepoFlavour, target: VersionDescriptor, files: List[Path]) : Try[Unit] = {
    val res = files.map { localFile =>
      val url = repo.fileUploadFor(target, localFile.getFileName.toString)
      bintrayConnector.uploadFile(target, localFile, url)
    }

    sequence(res).map { _ => Unit }
  }

  private def transformFiles(repo: RepoFlavour, map: VersionMapping, files: List[(String, Option[Transformer])], prefix:String): Try[List[Path]] =
    sequence {
      files
        .filter(f => f._2.isDefined )
        .map { case(file, trans) => transformFile(repo, map, file, trans.get, prefix) }
    }

  private def transformFile(repo:RepoFlavour, map: VersionMapping, fileName:String, trans: Transformer, prefix:String) : Try[Path]={
    val artefactUrl = repo.fileDownloadFor(map.sourceArtefact, fileName)

    bintrayConnector.downloadFile(artefactUrl, fileName).flatMap { localPath =>
      val targetFileName = buildTargetFileName(map, fileName, prefix)
      val targetPath = stageDir.resolve(targetFileName)

      if(targetPath.toFile.exists()){
        log.info(s"already have $targetPath, not updating")
        Success(targetPath)
      } else {
        log.info(s"using ${trans.getClass.getName} to transform $fileName writing to file $targetPath")
        trans.apply(localPath, map.sourceArtefact.artefactName, map.sourceVersion, map.targetVersion, targetPath)
      }
    }
  }

  private def buildTargetFileName(map: VersionMapping, remotePath: String, prefix: String): String = {
    val fileName = remotePath.split("/").last
    fileName.replace(map.sourceVersion.value, map.targetArtefact.version)
  }

  private def sequence[A](l:Iterable[Try[A]]):Try[List[A]]={
    l.find(_.isFailure) match {
      case None => Success(l.map(_.get).toList)
      case Some(f) => Failure[List[A]](f.failed.get)
    }
  }
}
