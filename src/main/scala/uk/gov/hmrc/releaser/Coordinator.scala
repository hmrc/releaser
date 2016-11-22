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
import uk.gov.hmrc.releaser.bintray.{BintrayPaths, BintrayRepoConnector, DefaultBintrayRepoConnector}
import uk.gov.hmrc.releaser.domain.RepoFlavours._
import uk.gov.hmrc.releaser.domain._
import uk.gov.hmrc.releaser.github.GithubTagAndRelease

import scala.util.{Failure, Success, Try}

class Coordinator(stageDir: Path,
                  findArtefactMetaData:(Path) => Try[ArtefactMetaData],
                  githubConnector: GithubTagAndRelease,
                  bintrayConnector: BintrayRepoConnector) extends Logger {

  val repositoryFlavors = Seq(mavenRepository, ivyRepository)

  def start(artefactName:String,
            gitRepo:Repo,
            sourceVersion:ReleaseCandidateVersion,
            releaseType: ReleaseType.Value): Try[ReleaseVersion] = {

    for {
      targetVersion <- VersionNumberCalculator.calculateTarget(sourceVersion, releaseType)
      repo <- findReposOfArtefact(artefactName)
      map = VersionMapping(repo, artefactName, gitRepo, sourceVersion, targetVersion)
      artefacts = map.repo.artefactBuilder(map, stageDir)
      bintrayJarUrl = repo.jarDownloadFor(map.targetArtefact)
      bintrayJarFilename = repo.jarFilenameFor(map.targetArtefact)
      _ <- bintrayConnector.verifyTargetDoesNotExist(bintrayJarUrl, map.targetArtefact)
      files <- bintrayConnector.findFiles(map.sourceArtefact)
      remotes = artefacts.transformersForSupportedFiles(files)
      localJar = bintrayConnector.findJar(bintrayJarFilename, bintrayJarUrl, map.sourceArtefact)
      (commitSha, commitAuthor, commitDate) <- getMetaData(localJar).map(x => ArtefactMetaData.unapply(x).get)
      _ <- githubConnector.verifyGithubTagExists(map.gitRepo, commitSha)
      transd <- transformFiles(repo, map, remotes, artefacts.filePrefix)
      _ <- uploadFiles(repo, map.targetArtefact, transd)
      _ <- bintrayConnector.publish(map.targetArtefact)
      _ <- githubConnector.createGithubTagAndRelease(new DateTime(), commitSha, commitAuthor, commitDate, map)
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

  private def getMetaData(jarPath: Option[Path]): Try[ArtefactMetaData] = {
    jarPath match {
      case Some(path) => findArtefactMetaData(path)
      case None => ???
    }
  }

  private def uploadFiles(repo: RepoFlavour, target:VersionDescriptor, files: List[Path]) : Try[Unit] = {
    val res = files.map { localFile =>
      val url = repo.fileUploadFor(target, localFile.getFileName.toString)
      bintrayConnector.uploadFile(target, localFile, url)
    }

    sequence(res).map { _ => Unit }
  }

  private def transformFiles(repo: RepoFlavour, map: VersionMapping, files: List[(String, Option[Transformer])], prefix:String): Try[List[Path]] =
    sequence {
      files.map { case(file, transO) => transformFile(repo, map, file, transO, prefix) }
    }

  private def transformFile(repo:RepoFlavour, map: VersionMapping, fileName:String, transO:Option[Transformer], prefix:String) : Try[Path]={
    val artefactUrl = repo.fileDownloadFor(map.sourceArtefact, fileName)

    bintrayConnector.downloadFile(artefactUrl, fileName).flatMap { localPath =>
      val targetFileName = buildTargetFileName(map, fileName, prefix)
      val targetPath = stageDir.resolve(targetFileName)
      log.info(s"using ${transO.map(_.getClass.getName).getOrElse("<no-op transformer>")} to transform $fileName writing to file $targetPath")
      if(targetPath.toFile.exists()){
        log.info(s"already have $targetPath, not updating")
        Success(targetPath)
      } else {
        transO.map { trans =>
          trans.apply(localPath, map.sourceArtefact.artefactName, map.sourceVersion, map.targetVersion, targetPath)
        }.getOrElse {
          Try {
            Files.copy(localPath, targetPath)
          }
        }
      }
    }
  }

  private def buildTargetFileName(map: VersionMapping, remotePath: String, prefix: String): String = {
    val fileName = remotePath.split("/").last.stripPrefix(prefix)
    map.repo.filenameFor(map.targetArtefact, fileName)
  }

  private def sequence[A](l:Iterable[Try[A]]):Try[List[A]]={
    l.find(_.isFailure) match {
      case None => Success(l.map(_.get).toList)
      case Some(f) => Failure[List[A]](f.failed.get)
    }
  }
}
