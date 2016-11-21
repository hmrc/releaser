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
import java.nio.file.{Files, Path}

import org.apache.commons.io.FileUtils
import uk.gov.hmrc.releaser.bintray.{BintrayHttp, BintrayMetaConnector, BintrayRepoConnector}
import uk.gov.hmrc.releaser.domain.RepoFlavours._
import uk.gov.hmrc.releaser.domain._
import uk.gov.hmrc.releaser.github.GithubConnector

import scala.collection.immutable.SortedSet
import scala.util.{Failure, Success, Try}

object ReleaserMain {
  def main(args: Array[String]):Unit= {
    val result = Releaser(args)
    System.exit(result)
  }
}

object ReleaseType extends Enumeration {
  type ReleaseType = Value
  val MAJOR, MINOR, HOTFIX = Value

  val stringValues: SortedSet[String] = this.values.map(_.toString)
}

object Releaser extends Logger {

  import uk.gov.hmrc.releaser.ArgParser._

  def apply(args: Array[String]):Int= {
    parser.parse(args, Config()) match {
      case Some(config) =>
        val githubName = config.githubNameOverride.getOrElse(config.artefactName)
        start(config.artefactName, ReleaseCandidateVersion(config.rcVersion), config.releaseType, githubName, config.dryRun)
      case None => -1
    }
  }

  def start(artefactName: String,
             rcVersion: ReleaseCandidateVersion,
             releaseType: ReleaseType.Value,
             gitHubName: String,
             dryRun: Boolean = false): Int = {

    val githubCredsFile  = System.getProperty("user.home") + "/.github/.credentials"
    val bintrayCredsFile = System.getProperty("user.home") + "/.bintray/.credentials"

    val githubCredsOpt  = CredentialsFinder.findGithubCredsInFile(new File(githubCredsFile).toPath)
    val bintrayCredsOpt = CredentialsFinder.findBintrayCredsInFile(new File(bintrayCredsFile).toPath)

    val directories = ReleaseDirectories()

    if(githubCredsOpt.isEmpty){
      log.info(s"Didn't find github credentials in $githubCredsFile")
      -1
    } else if(bintrayCredsOpt.isEmpty){
      log.info(s"Didn't find Bintray credentials in $bintrayCredsFile")
      -1
    } else {

      val releaserVersion = getClass.getPackage.getImplementationVersion

      val gitHubDetails = if (dryRun) GithubConnector.dryRun(githubCredsOpt.get, releaserVersion) else GithubConnector(githubCredsOpt.get, releaserVersion)
      val bintrayDetails = if (dryRun) BintrayDetails.dryRun(bintrayCredsOpt.get, directories.workDir) else BintrayDetails(bintrayCredsOpt.get, directories.workDir)
      val repositories = new Repositories(bintrayDetails.metaDataGetter)(bintrayDetails.repositoryFlavors)

      val result = for {
        repo <- repositories.findReposOfArtefact(artefactName)
        targetVersion <- VersionNumberCalculator.calculateTarget(rcVersion, releaseType)
        bintrayRepoConnector = new BintrayRepoConnector(directories.workDir, new BintrayHttp(bintrayCredsOpt.get), repo, new FileDownloader)
        coordinator = new Coordinator(directories.stageDir, ArtefactMetaData.fromFile, gitHubDetails, bintrayRepoConnector)
        result = coordinator.start(VersionMapping(repo, artefactName, Repo(gitHubName), rcVersion, targetVersion))
      } yield {
        log.info(s"Releaser successfully released $artefactName $targetVersion")
        0
      }

      result match {
        case Success(value) => value
        case Failure(e) =>
          e.printStackTrace()
          log.info(s"Releaser failed to release $artefactName $rcVersion with error '${e.getMessage}'")
          1
      }
    }
  }
}

case class ReleaseDirectories(tmpDirectory : () => Path = () => Files.createTempDirectory("releaser")){
  private lazy val tmpDir = tmpDirectory()

  lazy val workDir = Files.createDirectories(tmpDir.resolve("work"))
  lazy val stageDir = Files.createDirectories(tmpDir.resolve("stage"))

  def deleteTmpDir = Try {
    FileUtils.forceDelete(tmpDir.toFile)
  }
}

class BintrayDetails(bintrayConnector : BintrayHttp, workDir : Path){
  lazy val metaDataGetter = new BintrayMetaConnector(bintrayConnector).getRepoMetaData _
  val repositoryFlavors = Seq(mavenRepository, ivyRepository)
}

object BintrayDetails extends Logger {
  def apply(bintrayCreds: ServiceCredentials, workDir : Path): BintrayDetails = new BintrayDetails(new BintrayHttp(bintrayCreds), workDir)

  def dryRun(bintrayCreds: ServiceCredentials, workDir : Path) = {
    log.info("Bintray : running in dry-run mode")
    val dryRunHttp = new BintrayHttp(bintrayCreds){
      override def emptyPost(url:String): Try[Unit] = { println("BintrayHttp emptyPost DRY_RUN");Success(Unit)}
      override def putFile(version: VersionDescriptor, file: Path, url: String): Try[Unit] = { println("BintrayHttp putFile DRY_RUN");Success(Unit) }
    }
    new BintrayDetails(dryRunHttp, workDir)
  }
}