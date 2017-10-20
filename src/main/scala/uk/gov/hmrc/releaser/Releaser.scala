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

package uk.gov.hmrc.releaser

import java.io.File
import java.nio.file.{Files, Path}

import org.apache.commons.io.FileUtils
import uk.gov.hmrc.releaser.bintray.{BintrayHttp, BintrayRepoConnector, DefaultBintrayRepoConnector}
import uk.gov.hmrc.releaser.github.{GithubConnector, Repo}
import uk.gov.hmrc.{CredentialsFinder, FileDownloader, Logger}

import scala.util.{Failure, Success, Try}

object ReleaserMain {
  def main(args: Array[String]): Unit = {
    val result = Releaser(args)
    System.exit(result)
  }
}

object Releaser extends Logger {

  import ArgParser._

  def apply(args: Array[String]): Int = {
    parser.parse(args, Config()) match {
      case Some(config) =>
        val githubName = config.githubNameOverride.getOrElse(config.artefactName)
        run(config.artefactName, ReleaseCandidateVersion(config.rcVersion), config.releaseType, githubName, config.releaseNotes, config.dryRun)
      case None => -1
    }
  }

  def run(artefactName: String, rcVersion: ReleaseCandidateVersion, releaseType: ReleaseType.Value, gitHubName: String, releaseNotes: Option[String], dryRun: Boolean = false): Int = {
    val githubCredsFile = System.getProperty("user.home") + "/.github/.credentials"
    val bintrayCredsFile = System.getProperty("user.home") + "/.bintray/.credentials"

    val githubCredsOpt = CredentialsFinder.findGithubCredsInFile(new File(githubCredsFile).toPath)
    val bintrayCredsOpt = CredentialsFinder.findBintrayCredsInFile(new File(bintrayCredsFile).toPath)

    doReleaseWithCleanup { directories =>
      if (githubCredsOpt.isEmpty) {
        log.info(s"Didn't find github credentials in $githubCredsFile")
        -1
      } else if (bintrayCredsOpt.isEmpty) {
        log.info(s"Didn't find Bintray credentials in $bintrayCredsFile")
        -1
      } else {

        val releaserVersion = getClass.getPackage.getImplementationVersion
        val metaDataProvider = new ArtefactMetaDataProvider()
        val gitHubDetails = if (dryRun) GithubConnector.dryRun(githubCredsOpt.get, releaserVersion) else GithubConnector(githubCredsOpt.get, releaserVersion)
        val bintrayDetails = if (dryRun) BintrayRepoConnector.dryRun(bintrayCredsOpt.get, directories.workDir) else BintrayRepoConnector(bintrayCredsOpt.get, directories.workDir)
        val bintrayRepoConnector = new DefaultBintrayRepoConnector(directories.workDir, new BintrayHttp(bintrayCredsOpt.get), new FileDownloader)

        val coordinator = new Coordinator(directories.stageDir, metaDataProvider, gitHubDetails, bintrayRepoConnector)
        val result = coordinator.start(artefactName, Repo(gitHubName), rcVersion, releaseType, releaseNotes)

        result match {
          case Success(targetVersion) =>
            log.info(s"Releaser successfully released $artefactName $targetVersion")
            0
          case Failure(e) =>
            e.printStackTrace()
            log.info(s"Releaser failed to release $artefactName $rcVersion with error '${e.getMessage}'")
            1
        }
      }
    }
  }

  def doReleaseWithCleanup[T](f: ReleaseDirectories => T): T = {
    val directories = ReleaseDirectories()
    try {
      f(directories)
    } finally {
      log.info("cleaning releaser work directory")
      directories.delete().recover{case  t => log.warn(s"failed to delete releaser work directory ${t.getMessage}")}
    }

  }
}

case class ReleaseDirectories(tmpDirectory: Path = Files.createTempDirectory("releaser")) {

  lazy val workDir = Files.createDirectories(tmpDirectory.resolve("work"))
  lazy val stageDir = Files.createDirectories(tmpDirectory.resolve("stage"))

  def delete() = Try {
    FileUtils.forceDelete(tmpDirectory.toFile)
  }
}
