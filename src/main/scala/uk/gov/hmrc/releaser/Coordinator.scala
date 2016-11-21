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
import uk.gov.hmrc.releaser.bintray.{BintrayRepoConnector, DefaultBintrayRepoConnector}
import uk.gov.hmrc.releaser.domain._
import uk.gov.hmrc.releaser.github.GithubTagAndRelease

import scala.util.{Failure, Success, Try}

class Coordinator(stageDir: Path,
                  findArtefactMetaData:(Path) => Try[ArtefactMetaData],
                  githubConnector: GithubTagAndRelease,
                  bintrayConnector: BintrayRepoConnector) extends Logger {

  def start(map: VersionMapping): Try[Unit] ={
    val artefacts = map.repo.artefactBuilder(map, stageDir)

    for(
      _         <- bintrayConnector.verifyTargetDoesNotExist(map.targetArtefact);
      files     <- bintrayConnector.findFiles(map.sourceArtefact);
      remotes    = artefacts.transformersForSupportedFiles(files);
      localJar   = bintrayConnector.findJar(map.sourceArtefact);
      (commitSha, commitAuthor, commitDate)
                <- getMetaData(localJar).map(x => ArtefactMetaData.unapply(x).get);
      _         <- githubConnector.verifyGithubTagExists(map.gitRepo, commitSha);
      transd    <- transformFiles(map, remotes, artefacts.filePrefix);
      _         <- uploadFiles(map.targetArtefact, transd);
      _         <- bintrayConnector.publish(map.targetArtefact);
      _         <- githubConnector.createGithubTagAndRelease(new DateTime(), commitSha, commitAuthor, commitDate, map)
    )
     yield ()
  }

  private def getMetaData(jarPath: Option[Path]): Try[ArtefactMetaData] = {
    jarPath match {
      case Some(path) => findArtefactMetaData(path)
      case None => ???
    }
  }

  private def uploadFiles(target:VersionDescriptor, files: List[Path]) : Try[Unit] = {
    val res = files.map { localFile => bintrayConnector.uploadFile(target, localFile) }
    sequence(res).map { _ => Unit }
  }

  private def transformFiles(map: VersionMapping, files: List[(String, Option[Transformer])], prefix:String): Try[List[Path]] =
    sequence {
      files.map { case(file, transO) => transformFile(map, file, transO, prefix) }
    }

  private def transformFile(map: VersionMapping, remotePath:String, transO:Option[Transformer], prefix:String) : Try[Path]={
    bintrayConnector.downloadFile(map.sourceArtefact, remotePath).flatMap { localPath =>
      val targetFileName = buildTargetFileName(map, remotePath, prefix)
      val targetPath = stageDir.resolve(targetFileName)
      log.info(s"using ${transO.map(_.getClass.getName).getOrElse("<no-op transformer>")} to transform $remotePath writing to file $targetPath")
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
