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

import java.io.File
import java.nio.file.{Files, Path}

import org.apache.commons.io.FileUtils
import play.api.Logger
import play.api.libs.json.JsValue
import uk.gov.hmrc.releaser.domain._

import scala.collection.immutable.SortedSet
import scala.util.{Failure, Success, Try}

class Logger{
  def info(st:String) = println("[INFO] " + st)
  def debug(st:String) = println("[DEBUG] " + st)
  def warn(st:String) = println("[WARN] " + st)
}

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

object Releaser {

  import uk.gov.hmrc.releaser.ArgParser._
  import uk.gov.hmrc.releaser.domain.RepoFlavours._

  val log = new Logger()

  def apply(args: Array[String]):Int= {
    parser.parse(args, Config()) match {
      case Some(config) => {
        val githubName = config.githubNameOverride.getOrElse(config.artefactName)
        start(config.artefactName, config.artefactSuffix, ReleaseCandidateVersion(config.rcVersion), config.releaseType, githubName, config.dryRun)
      }
      case None => -1
    }
  }

  def start(
             artefactName: String,
             artefactSuffix: String,
             rcVersion: ReleaseCandidateVersion,
             releaseType: ReleaseType.Value,
             gitHubName:String,
             dryRun:Boolean = false
             ):Int={
    val tmpDir = Files.createTempDirectory("releaser")

    val githubCredsFile  = System.getProperty("user.home") + "/.github/.credentials"
    val bintrayCredsFile = System.getProperty("user.home") + "/.bintray/.credentials"

    val githubCredsOpt  = CredentialsFinder.findGithubCredsInFile(new File(githubCredsFile).toPath)
    val bintrayCredsOpt = CredentialsFinder.findBintrayCredsInFile(new File(bintrayCredsFile).toPath)

    if(githubCredsOpt.isEmpty){
      log.info(s"Didn't find github credentials in $githubCredsFile")
      -1
    } else if(bintrayCredsOpt.isEmpty){
      log.info(s"Didn't find Bintray credentials in $bintrayCredsFile")
      -1
    } else {

      val releaser = if (dryRun) {
        log.info("starting in dry-run mode")
        buildDryRunReleaser(tmpDir, githubCredsOpt.get, bintrayCredsOpt.get)
      } else {
        buildReleaser(tmpDir, githubCredsOpt.get, bintrayCredsOpt.get)
      }

      val targetVersion = VersionNumberCalculator.calculateTarget(rcVersion, releaseType)

      targetVersion.flatMap { tv =>
        val result: Try[Unit] = releaser.start(artefactName, artefactSuffix, Repo(gitHubName), rcVersion, tv)
        Try {
          FileUtils.forceDelete(tmpDir.toFile)
        }
        result
      } match {
        case Failure(e) => {e.printStackTrace(); log.info(s"Releaser failed to release $artefactName $artefactSuffix $rcVersion with error '${e.getMessage}'")}; 1
        case Success(_) => log.info(s"Releaser successfully released $artefactName $artefactSuffix ${targetVersion.getOrElse("")}"); 0;
      }
    }
  }

  //TODO not tested
  def buildDryRunReleaser(
                     tmpDir:Path,
                     githubCreds: ServiceCredentials,
                     bintrayCreds: ServiceCredentials): Releaser = {

    val githubConnector = new GithubHttp(githubCreds)
    val EmptyBintrayConnector = new BintrayHttp(bintrayCreds){
      override def emptyPost(url:String): Try[Unit] = { println("BintrayHttp emptyPost DRY_RUN");Success(Unit)}
      override def putFile(version: VersionDescriptor, file: Path, url: String): Try[Unit] = { println("BintrayHttp putFile DRY_RUN");Success(Unit) }
    }

    val releaserVersion = getClass.getPackage.getImplementationVersion

    val emptyGitPoster: (String, JsValue) => Try[Unit] = (a, b) => { println("Github emptyPost DRY_RUN"); Success(Unit) }
    val emptyGitPosteAndGetter: (String, JsValue) => Try[CommitSha] = (a, b) => { println("Github emptyPost DRY_RUN"); Success("a-fake-tag-sha") }

    val workDir = Files.createDirectories(tmpDir.resolve("work"))
    val stageDir = Files.createDirectories(tmpDir.resolve("stage"))

    val metaDataGetter = new BintrayMetaConnector(EmptyBintrayConnector).getRepoMetaData _
    val repoConnectorBuilder = BintrayRepoConnector.apply(workDir, EmptyBintrayConnector) _
    val githubReleaseCreator = GithubApi.createRelease(emptyGitPoster)(releaserVersion) _
    val githubTagObjectCreator = GithubApi.createAnnotatedTagObject(emptyGitPosteAndGetter)(releaserVersion) _
    val githubTagRefCreator = GithubApi.createAnnotatedTagRef(emptyGitPoster)(releaserVersion) _
    val verifyGithubCommit = GithubApi.verifyCommit(githubConnector.get) _
    val gitHubTagAndRelease = createGitHubTagAndRelease(githubTagObjectCreator, githubTagRefCreator, githubReleaseCreator) _

    val artefactBuilder = ArtefactMetaData.fromFile _

    val coordinator = new Coordinator(stageDir, artefactBuilder, verifyGithubCommit, gitHubTagAndRelease)
    val repoFinder = new Repositories(metaDataGetter)(Seq(mavenRepository, ivyRepository)).findReposOfArtefact _
    new Releaser(stageDir, repoFinder, repoConnectorBuilder, coordinator)
  }

  def createGitHubTagAndRelease(
                                  githubTagObjectCreator: (Repo, ReleaseVersion, CommitSha) => Try[CommitSha],
                                  githubTagRefCreator: (Repo, ReleaseVersion, CommitSha) => Try[Unit],
                                  githubReleaseCreator: (ArtefactMetaData, VersionMapping) => Try[Unit])
                                (metaData: ArtefactMetaData, map: VersionMapping):Try[Unit]={
    for(
      tagSha <- githubTagObjectCreator(map.gitRepo, map.targetVersion, metaData.sha);
      _      <- githubTagRefCreator(map.gitRepo, map.targetVersion, tagSha);
      _      <- githubReleaseCreator(metaData, map))
      yield ()
  }

  //TODO not tested
  def buildReleaser(
                     tmpDir:Path,
                     githubCreds: ServiceCredentials,
                     bintrayCreds: ServiceCredentials): Releaser = {

    val githubConnector = new GithubHttp(githubCreds)
    val bintrayConnector = new BintrayHttp(bintrayCreds)

    val releaserVersion = getClass.getPackage.getImplementationVersion

    val workDir = Files.createDirectories(tmpDir.resolve("work"))
    val stageDir = Files.createDirectories(tmpDir.resolve("stage"))

    val metaDataGetter = new BintrayMetaConnector(bintrayConnector).getRepoMetaData _
    val repoConnectorBuilder = BintrayRepoConnector.apply(workDir, bintrayConnector) _
    val githubReleaseCreator = GithubApi.createRelease(githubConnector.postUnit)(releaserVersion) _
    val githubTagObjectCreator = GithubApi.createAnnotatedTagObject(githubConnector.post[CommitSha](GithubApi.shaFromResponse))(releaserVersion) _
    val githubTagRefCreator = GithubApi.createAnnotatedTagRef(githubConnector.postUnit)(releaserVersion) _
    val verifyGithubCommit = GithubApi.verifyCommit(githubConnector.get) _
    val gitHubTagAndRelease = createGitHubTagAndRelease(githubTagObjectCreator, githubTagRefCreator, githubReleaseCreator) _

    val artefactBuilder = ArtefactMetaData.fromFile _

    val coordinator = new Coordinator(stageDir, artefactBuilder, verifyGithubCommit, gitHubTagAndRelease)
    val repoFinder = new Repositories(metaDataGetter)(Seq(mavenRepository, ivyRepository)).findReposOfArtefact _
    new Releaser(stageDir, repoFinder, repoConnectorBuilder, coordinator)
  }
}

class Releaser(stageDir:Path,
               repositoryFinder:(ArtefactName) => Try[RepoFlavour],
               connectorBuilder:(RepoFlavour) => RepoConnector,
               coordinator:Coordinator){

  def start(artefactName: String, artefactSuffix : String, gitRepo:Repo, rcVersion: ReleaseCandidateVersion, targetVersionString: ReleaseVersion): Try[Unit] = {

    val artName = s"$artefactName$artefactSuffix"

    repositoryFinder(artName) flatMap { repo =>
      val ver = VersionMapping(repo, artName, gitRepo, rcVersion, targetVersionString)
      coordinator.start(ver, connectorBuilder(repo))
    }
  }
}

class Coordinator(
                   stageDir:Path,
                   artefactBuilder:(Path) => Try[ArtefactMetaData],
                   verifyGithubTagExists:(Repo, CommitSha) => Try[Unit],
                   createGithubTagAndRelease:(ArtefactMetaData, VersionMapping) => Try[Unit]){

  val logger = new Logger()

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
      Logger.info(s"using ${transO.map(_.getClass.getName).getOrElse("<no-op transformer>")} to transform $remotePath writing to file ${targetPath}")
      if(targetPath.toFile.exists()){
        Logger.info(s"already have $targetPath, not updating")
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
