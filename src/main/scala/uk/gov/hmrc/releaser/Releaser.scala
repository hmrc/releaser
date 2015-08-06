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

import play.api.libs.json.JsValue
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

  import uk.gov.hmrc.releaser.ArgParser._
  import uk.gov.hmrc.releaser.domain.RepoFlavours._

  val log = new Logger()

  val tmpDir = Files.createTempDirectory("releaser")
  log.info("Using temp dir: " + tmpDir)

  val workDir = Files.createDirectories(tmpDir.resolve("work"))
  val stageDir = Files.createDirectories(tmpDir.resolve("stage"))

  def apply(args: Array[String]):Int= {
    parser.parse(args, Config()) match {
      case Some(config) => {
        val githubName = config.githubNameOverride.getOrElse(config.artefactName)
        start(config.artefactName, ReleaseCandidateVersion(config.rcVersion), config.releaseType, githubName, config.dryRun)
      }
      case None => -1
    }
  }

  def start(
             artefactName: String,
             rcVersion: ReleaseCandidateVersion,
             releaseType: ReleaseType.Value,
             gitHubName:String,
             dryRun:Boolean = false
             ):Int={


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
        buildDryRunReleaser(githubCredsOpt.get, bintrayCredsOpt.get)
      } else {
        buildReleaser(githubCredsOpt.get, bintrayCredsOpt.get)
      }

      val targetVersion = VersionNumberCalculator.calculateTarget(rcVersion, releaseType)

      targetVersion.flatMap { tv =>
        val result: Try[Unit] = releaser.start(artefactName, Repo(gitHubName), rcVersion, tv)
        Try {
         // FileUtils.forceDelete(tmpDir.toFile)
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
                     githubCreds: ServiceCredentials,
                     bintrayCreds: ServiceCredentials): Releaser = {

    val githubConnector = new GithubHttp(githubCreds)
    val EmptyBintrayConnector = new BintrayHttp(bintrayCreds){
      override def emptyPost(url:String): Try[Unit] = { println("BintrayHttp emptyPost DRY_RUN");Success(Unit)}
      override def putFile(version: VersionDescriptor, file: Path, url: String): Try[Unit] = {
        println(s"BintrayHttp putFile DRY_RUN: $file")
        Success(Unit)
      }
    }

    val releaserVersion = getClass.getPackage.getImplementationVersion

    val emptyGitPoster: (String, JsValue) => Try[Unit] = (a, b) => { println("Github emptyPost DRY_RUN"); Success(Unit) }
    val emptyGitPosteAndGetter: (String, JsValue) => Try[CommitSha] = (a, b) => { println("Github emptyPost DRY_RUN"); Success("a-fake-tag-sha") }

    val metaDataGetter = new BintrayMetaConnector(EmptyBintrayConnector).getRepoMetaData _
    val repoConnectorBuilder = BintrayRepoConnector.apply(workDir, EmptyBintrayConnector) _
    val githubReleaseCreator = GithubApi.createRelease(emptyGitPoster)(releaserVersion) _
    val githubTagObjectCreator = GithubApi.createAnnotatedTagObject(emptyGitPosteAndGetter)(releaserVersion) _
    val githubTagRefCreator = GithubApi.createAnnotatedTagRef(emptyGitPoster)(releaserVersion) _
    val verifyGithubCommit = GithubApi.verifyCommit(githubConnector.get) _
    val gitHubTagAndRelease = createGitHubTagAndRelease(githubTagObjectCreator, githubTagRefCreator, githubReleaseCreator) _

    val artefactBuilder = ArtefactMetaData.fromFile _

    val classifiersBuilder = Artifacts.buildSupportedArtifacts _

    val coordinator = new Coordinator(stageDir, artefactBuilder, verifyGithubCommit, gitHubTagAndRelease)
    val repoFinder = new Repositories(metaDataGetter)(Seq(mavenRepository, ivyRepository)).findReposOfArtefact _
    new Releaser(stageDir, repoFinder, repoConnectorBuilder, classifiersBuilder, coordinator)
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
                     githubCreds: ServiceCredentials,
                     bintrayCreds: ServiceCredentials): Releaser = {

    val githubConnector = new GithubHttp(githubCreds)
    val bintrayConnector = new BintrayHttp(bintrayCreds)

    val releaserVersion = getClass.getPackage.getImplementationVersion

    val metaDataGetter = new BintrayMetaConnector(bintrayConnector).getRepoMetaData _
    val repoConnectorBuilder = BintrayRepoConnector.apply(workDir, bintrayConnector) _
    val githubReleaseCreator = GithubApi.createRelease(githubConnector.postUnit)(releaserVersion) _
    val githubTagObjectCreator = GithubApi.createAnnotatedTagObject(githubConnector.post[CommitSha](GithubApi.shaFromResponse))(releaserVersion) _
    val githubTagRefCreator = GithubApi.createAnnotatedTagRef(githubConnector.postUnit)(releaserVersion) _
    val verifyGithubCommit = GithubApi.verifyCommit(githubConnector.get) _
    val gitHubTagAndRelease = createGitHubTagAndRelease(githubTagObjectCreator, githubTagRefCreator, githubReleaseCreator) _

    val artefactBuilder = ArtefactMetaData.fromFile _

    val classifiersBuilder = Artifacts.buildSupportedArtifacts _

    val coordinator = new Coordinator(stageDir, artefactBuilder, verifyGithubCommit, gitHubTagAndRelease)
    val repoFinder = new Repositories(metaDataGetter)(Seq(mavenRepository, ivyRepository)).findReposOfArtefact _
    new Releaser(stageDir, repoFinder, repoConnectorBuilder, classifiersBuilder, coordinator)
  }
}

class Releaser(stageDir:Path,
               repositoryFinder:(ArtefactName) => Try[RepoFlavour],
               connectorBuilder:(RepoFlavour) => RepoConnector,
               classifiersBuilder: () => Seq[ArtifactClassifier],
               coordinator:Coordinator){

  def start(artefactName: String, gitRepo:Repo, rcVersion: ReleaseCandidateVersion, targetVersionString: ReleaseVersion): Try[Unit] = {

    repositoryFinder(artefactName) flatMap { repo =>
      val artifacts = classifiersBuilder.apply()
      val artifactMappings = VersionMapping(repo, artefactName, artifacts, gitRepo, rcVersion, targetVersionString)

      coordinator.start(artifactMappings, connectorBuilder(repo))
    }
  }
}

class Coordinator(
                   stageDir:Path,
                   artefactBuilder:(Path) => Try[ArtefactMetaData],
                   verifyGithubTagExists:(Repo, CommitSha) => Try[Unit],
                   createGithubTagAndRelease:(ArtefactMetaData, VersionMapping) => Try[Unit]){

  val logger = new Logger()

  val manifestTransformer = new ManifestTransformer

  def start(map: VersionMapping, connector:RepoConnector): Try[Unit] ={
    for(
      localFiles              <- connector.downloadArtifacts(map.sourceArtefacts);
      mainArtifactType        <- Artifacts.findMainArtifact(localFiles.keys.toSeq);
      pomArtifactType         <- Artifacts.findPomArtifact(localFiles.keys.toSeq);
      mainArtifactPath        <- findLocalArtifact(localFiles, mainArtifactType);
      pomArtifactPath         <- findLocalArtifact(localFiles, pomArtifactType);
      updatedPomArtifactPath  <- map.repo.pomTransformer.apply(pomArtifactPath, map.targetVersion, pomArtifactPath.getFileName.toString, stageDir);
      metaData                <- artefactBuilder(mainArtifactPath);
      _                       <- verifyGithubTagExists(map.gitRepo, metaData.sha);
      updatedMainArtifactPath <- manifestTransformer(mainArtifactPath, map.targetVersion, mainArtifactPath.getFileName.toString, stageDir);
      updatedLocalFiles       <- updateLocalArtifactMap(localFiles, Seq(mainArtifactType -> updatedMainArtifactPath, pomArtifactType -> updatedPomArtifactPath));
      _                       <- connector.uploadArtifacts(map.targetArtefacts, updatedLocalFiles);
      _                       <- publish(map.mainTargetArtefact, connector);
      _                       <- createGithubTagAndRelease(metaData, map)
    ) yield ()
  }

  private def findLocalArtifact(downloadedArtifacts: Map[ArtifactClassifier, Path], artifactType: ArtifactClassifier) : Try[Path] = Try {
    downloadedArtifacts(artifactType)
  }

  private def updateLocalArtifactMap(downloadedArtifacts: Map[ArtifactClassifier, Path], newArtifacts: Seq[(ArtifactClassifier, Path)]) = Try {
    downloadedArtifacts ++ newArtifacts
  }

  def publish(mainArtifact: VersionDescriptor, connector:RepoConnector): Try[Unit] = {
    connector.publish(mainArtifact)
  }
}

