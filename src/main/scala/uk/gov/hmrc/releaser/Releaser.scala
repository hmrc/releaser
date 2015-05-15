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
import play.api.libs.json.JsValue
import uk.gov.hmrc.releaser.GithubApi.TagRefResponse
import uk.gov.hmrc.releaser.domain._

import scala.collection.immutable.SortedSet
import scala.util.{Failure, Success, Try}

class Logger{
  def info(st:String) = println("[INFO] " + st)
  def debug(st:String) = println("[DEBUG] " + st)
}

object ReleaserMain {
  def main(args: Array[String]):Unit= {
    val result = Releaser(args)
    System.exit(result)
  }

}

object ReleaseType extends Enumeration {
  type Margin = Value
  val MAJOR, MINOR, PATCH = Value

  val stringValues: SortedSet[String] = this.values.map(_.toString)
}

object Releaser {

  import ArgParser._
  import RepoFlavours._

  val log = new Logger()

  def apply(args: Array[String]):Int= {
    parser.parse(args, Config()) match {
      case Some(config) => start(config.artefactName, config.rcVersion, config.releaseType, config.dryRun)
      case None => -1
    }
  }

  def start(artefactName: String, rcVersion: String, releaseType: ReleaseType.Value, dryRun:Boolean = false):Int={
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
        log.info("starting in dry run mode")
        buildDryRunReleaser(tmpDir, githubCredsOpt.get, bintrayCredsOpt.get)
      } else {
        buildReleaser(tmpDir, githubCredsOpt.get, bintrayCredsOpt.get)
      }

      val targetVersion = VersionNumberCalculator.calculateTarget(rcVersion, releaseType)

      targetVersion.flatMap { tv =>
        val result: Try[Unit] = releaser.start(artefactName, rcVersion, tv)
        Try {
          FileUtils.forceDelete(tmpDir.toFile)
        }
        result
      } match {
        case Failure(e) => log.info(s"Releaser failed to release $artefactName $rcVersion with error '${e.getMessage}'"); 1
        case Success(_) => log.info(s"Releaser successfully released $artefactName ${targetVersion.getOrElse("")}"); 0;
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
      override def emptyPost(url:String): Try[Unit] = { println("BintrayHttp emptyPost DRY_RUN");Success()}
      override def putFile(version: VersionDescriptor, file: Path, url: String): Try[Unit] = { println("BintrayHttp putFile DRY_RUN");Success() }
    }

    val releaserVersion = getClass.getPackage.getImplementationVersion

    val emptyGitPoster: (String, JsValue) => Try[Unit] = (a, b) => { println("Github emptyPost DRY_RUN"); Success() }
    val emptyGitPosteAndGetter: (String, JsValue) => Try[CommitSha] = (a, b) => { println("Github emptyPost DRY_RUN"); Success("a-fake-tag-sha") }

    val workDir = Files.createDirectories(tmpDir.resolve("work"))
    val stageDir = Files.createDirectories(tmpDir.resolve("stage"))

    val metaDataGetter = new BintrayMetaConnector(EmptyBintrayConnector).getRepoMetaData _
    val repoConnectorBuilder = BintrayRepoConnector.apply(workDir, EmptyBintrayConnector) _
    val githubReleaseCreator = GithubApi.createRelease(emptyGitPoster)(releaserVersion) _
    val githubTagObjectCreator = GithubApi.createAnnotatedTagObject(emptyGitPosteAndGetter)(releaserVersion) _
    val githubTagRefCreator = GithubApi.createAnnotatedTagRef(emptyGitPoster)(releaserVersion) _
    val verifyGithubCommit = GithubApi.verifyCommit(githubConnector.get) _

    val artefactBuilder = ArtefactMetaData.fromFile _

    val coordinator = new Coordinator(stageDir, artefactBuilder, verifyGithubCommit, githubReleaseCreator, githubTagObjectCreator, githubTagRefCreator)
    val repoFinder = new Repositories(metaDataGetter)(Seq(mavenRepository, ivyRepository)).findReposOfArtefact _
    new Releaser(stageDir, repoFinder, repoConnectorBuilder, coordinator)
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

    val artefactBuilder = ArtefactMetaData.fromFile _

    val coordinator = new Coordinator(stageDir, artefactBuilder, verifyGithubCommit, githubReleaseCreator, githubTagObjectCreator, githubTagRefCreator)
    val repoFinder = new Repositories(metaDataGetter)(Seq(mavenRepository, ivyRepository)).findReposOfArtefact _
    new Releaser(stageDir, repoFinder, repoConnectorBuilder, coordinator)
  }
}

class Releaser(stageDir:Path,
               repositoryFinder:(String) => Try[RepoFlavour],
               connectorBuilder:(RepoFlavour) => RepoConnector,
               coordinator:Coordinator
                ){

  def start(artefactName: String, rcVersion: String, targetVersionString: String): Try[Unit] = {

    repositoryFinder(artefactName) flatMap { repo =>
      val ver = VersionMapping(repo, artefactName, rcVersion, targetVersionString)
      coordinator.start(ver, connectorBuilder(repo))
    }
  }
}

class Coordinator(
                   stageDir:Path,
                   artefactBuilder:(Path) => Try[ArtefactMetaData],
                   verifyGithubTagExists:(CommitSha, String) => Try[Unit],
                   createGitHubRelease:(ArtefactMetaData, VersionMapping) => Try[Unit],
                   createGitHubAnnotatedTagObject:(String, String, CommitSha) => Try[CommitSha],
                   createGitHubAnnotatedTagRef:(String, String, CommitSha) => Try[Unit]){

  val logger = new Logger()

  val manifestTransformer = new ManifestTransformer(stageDir)

  def start(map: VersionMapping, connector:RepoConnector): Try[Unit] ={
    for(
      localFile <- connector.downloadJar(map.sourceArtefact);
      metaData  <- artefactBuilder(localFile);
      _         <- verifyGithubTagExists(map.artefactName, metaData.sha);
      path      <- manifestTransformer(localFile, map.targetVersion, map.repo.jarFilenameFor(map.targetArtefact));
      _         <- connector.uploadJar(map.targetArtefact, path);
      _         <- uploadNewPom(map, connector);
      _         <- publish(map, connector);
      tagSha    <- createGitHubAnnotatedTagObject(map.artefactName, map.targetVersion, metaData.sha);
      _         <- createGitHubAnnotatedTagRef(map.artefactName, map.targetVersion, tagSha);
      _         <- createGitHubRelease(metaData, map)
    ) yield ()
  }

  def uploadNewPom(map:VersionMapping, connector:RepoConnector): Try[Unit] = {

    for(
      localFile   <- connector.downloadPom(map.sourceArtefact);
      transformed <- map.repo.pomTransformer.apply(localFile, map.targetVersion, map.repo.pomFilenameFor(map.targetArtefact));
      _           <- connector.uploadPom(map.targetArtefact, transformed)
    ) yield ()
  }

  def publish(map: VersionMapping, connector:RepoConnector): Try[Unit] = {
    connector.publish(map.targetArtefact)
  }
}

