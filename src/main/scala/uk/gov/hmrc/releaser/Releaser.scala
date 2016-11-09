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
import play.api.libs.json.JsValue
import uk.gov.hmrc.releaser.GithubDetails.{GitPost, GitPostAndGet}
import uk.gov.hmrc.releaser.RepoConnector.RepoConnectorBuilder
import uk.gov.hmrc.releaser.domain.RepoFlavours._
import uk.gov.hmrc.releaser.domain._

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
  import uk.gov.hmrc.releaser.domain.RepoFlavours._

  def apply(args: Array[String]):Int= {
    parser.parse(args, Config()) match {
      case Some(config) => {
        val githubName = config.githubNameOverride.getOrElse(config.artefactName)
        start(config.artefactName, ReleaseCandidateVersion(config.rcVersion), config.releaseType, githubName, config.dryRun)
      }
      case None => -1
    }
  }

  def start(artefactName: String,
             rcVersion: ReleaseCandidateVersion,
             releaseType: ReleaseType.Value,
             gitHubName: String,
             dryRun: Boolean = false
           ): Int = {

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

      val releaser = ReleaserBuilder(releaserVersion, directories, githubCredsOpt.get, bintrayCredsOpt.get, dryRun)

      val targetVersion = VersionNumberCalculator.calculateTarget(rcVersion, releaseType)

      targetVersion.flatMap { tv =>
        val result: Try[Unit] = releaser.start(artefactName, Repo(gitHubName), rcVersion, tv)
        directories.deleteTmpDir
        result
      } match {
        case Failure(e) => {e.printStackTrace(); log.info(s"Releaser failed to release $artefactName $rcVersion with error '${e.getMessage}'")}; 1
        case Success(_) => log.info(s"Releaser successfully released $artefactName ${targetVersion.getOrElse("")}"); 0;
      }
    }
  }
}

class Releaser(stageDir: Path,
               repositoryFinder: (ArtefactName) => Try[RepoFlavour],
               connectorBuilder: RepoConnectorBuilder,
               coordinator: Coordinator) {

  def start(artefactName: String, gitRepo: Repo, rcVersion: ReleaseCandidateVersion, targetVersionString: ReleaseVersion): Try[Unit] = {

    repositoryFinder(artefactName) flatMap { repo =>
      val ver = VersionMapping(repo, artefactName, gitRepo, rcVersion, targetVersionString)
      coordinator.start(ver, connectorBuilder(repo))
    }
  }
}


class Coordinator(
                   stageDir:Path,
                   artefactBuilder:(Path) => Try[ArtefactMetaData],
                   verifyGithubTagExists:(Repo, CommitSha) => Try[Unit],
                   createGithubTagAndRelease:(ArtefactMetaData, VersionMapping) => Try[Unit]) extends Logger {

  def start(map: VersionMapping, connector:RepoConnector): Try[Unit] ={
    val artefacts = map.repo.artefactBuilder(map, stageDir)

    for(
      _         <- connector.verifyTargetDoesNotExist(map.targetArtefact);
      files     <- connector.findFiles(map.sourceArtefact);
      remotes    = artefacts.transformersForSupportedFiles(files);
      localJar  <- connector.downloadJar(map.sourceArtefact);
      metaData  <- artefactBuilder(localJar);
      _         <- verifyGithubTagExists(map.gitRepo, metaData.sha);
      transd    <- transformFiles(map, remotes, connector, artefacts.filePrefix);
      _         <- uploadFiles(map.targetArtefact, transd, connector);
      _         <- connector.publish(map.targetArtefact);
      _         <- createGithubTagAndRelease(metaData, map))
     yield ()
  }

  def verifyTargetDoesNotExist():Try[Unit] = {
    Failure(new Exception("target exists"))
  }

  def uploadFiles(target:VersionDescriptor, files:List[Path], connector: RepoConnector):Try[Unit]={

    val res = files.map { localFile =>
      connector.uploadFile(target, localFile)
    }

    sequence(res).map { _ => Unit }
  }

  def transformFile(map: VersionMapping, remotePath:String, transO:Option[Transformer], connector:RepoConnector, prefix:String):Try[Path]={
    connector.downloadFile(map.sourceArtefact, remotePath).flatMap { localPath =>
      val targetFileName = buildTargetFileName(map, remotePath, prefix)
      val targetPath = stageDir.resolve(targetFileName)
      log.info(s"using ${transO.map(_.getClass.getName).getOrElse("<no-op transformer>")} to transform $remotePath writing to file ${targetPath}")
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

  def buildTargetFileName(map: VersionMapping, remotePath: String, prefix: String): String = {
    val fileName = remotePath.split("/").last.stripPrefix(prefix)
    map.repo.filenameFor(map.targetArtefact, fileName)
  }

  def transformFiles(map: VersionMapping, files:List[(String, Option[Transformer])], connector:RepoConnector, prefix:String):Try[List[Path]]={
    sequence{
      files.map { case(file, transO) => transformFile(map, file, transO, connector, prefix) }
    }
  }

  def publish(map: VersionMapping, connector:RepoConnector): Try[Unit] = {
    connector.publish(map.targetArtefact)
  }

  def sequence[A](l:Iterable[Try[A]]):Try[List[A]]={
    l.find(_.isFailure) match {
      case None => Success(l.map(_.get).toList)
      case Some(f) => Failure[List[A]](f.failed.get)
    }
  }
}

object GithubDetails extends Logger {
  type GitPost = (String, JsValue) => Try[Unit]
  type GitPostAndGet = (String, JsValue) => Try[CommitSha]

  def apply(githubCreds: ServiceCredentials, releaserVersion : String) = new GithubDetails(new GithubHttp(githubCreds), releaserVersion, new GithubApi())()

  def dryRun(githubCreds: ServiceCredentials, releaserVersion : String) = {
    log.info("Github : running in dry-run mode")
    val emptyGitPoster: (String, JsValue) => Try[Unit] = (a, b) => { println("Github emptyPost DRY_RUN"); Success(Unit) }
    val emptyGitPosteAndGetter: (String, JsValue) => Try[CommitSha] = (a, b) => { println("Github emptyPost DRY_RUN"); Success("a-fake-tag-sha") }
    new GithubDetails(new GithubHttp(githubCreds), releaserVersion, new GithubApi())(emptyGitPoster, emptyGitPosteAndGetter)
  }
}

class GithubDetails(githubConnector : GithubHttp, releaserVersion : String, githubApi : GithubApi)
                   (gitPost : GitPost = githubConnector.postUnit,
                    gitPostAndGet : GitPostAndGet = githubConnector.post[CommitSha](githubApi.shaFromResponse))
  extends GitTagAndRelease{

  val githubReleaseCreator = githubApi.createRelease(gitPost)(releaserVersion) _
  val githubTagObjectCreator = githubApi.createAnnotatedTagObject(gitPostAndGet)(releaserVersion) _
  val githubTagRefCreator = githubApi.createAnnotatedTagRef(gitPost)(releaserVersion) _

  val verifyGithubCommit = githubApi.verifyCommit(githubConnector.get) _
  val gitHubTagAndRelease = createGitHubTagAndRelease(githubTagObjectCreator, githubTagRefCreator, githubReleaseCreator) _
  val artefactBuilder = ArtefactMetaData.fromFile _
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
  lazy val repoConnectorBuilder = BintrayRepoConnector.apply(workDir, bintrayConnector) _

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

object ReleaserBuilder extends Logger {
  def apply(coordinator: Coordinator, repository : Repositories, repoConnectorBuilder : RepoConnectorBuilder, stageDir : Path) : Releaser = {
    val repoFinder = repository.findReposOfArtefact _
    new Releaser(stageDir, repoFinder, repoConnectorBuilder, coordinator)
  }

  def apply(releaserVersion : String, directories: ReleaseDirectories, githubCreds: ServiceCredentials, bintrayCreds: ServiceCredentials, dryRun : Boolean) : Releaser = {
    val gitHubDetails = if(dryRun) GithubDetails.dryRun(githubCreds, releaserVersion) else GithubDetails(githubCreds, releaserVersion)
    val bintrayDetails = if(dryRun) BintrayDetails.dryRun(bintrayCreds, directories.workDir) else BintrayDetails(bintrayCreds, directories.workDir)

    val coordinator = new Coordinator(directories.stageDir, ArtefactMetaData.fromFile, gitHubDetails.verifyGithubCommit, gitHubDetails.gitHubTagAndRelease)
    val repositories = new Repositories(bintrayDetails.metaDataGetter)(bintrayDetails.repositoryFlavors)

    ReleaserBuilder(coordinator, repositories, bintrayDetails.repoConnectorBuilder, directories.stageDir)
  }
}
