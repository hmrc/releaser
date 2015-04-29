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

import java.nio.file.{Files, Path}
import java.util.jar.Manifest
import java.util.zip.ZipFile

import org.apache.commons.io.FileUtils
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import uk.gov.hmrc.releaser.domain._

import scala.collection.JavaConversions._
import scala.collection.immutable.SortedSet
import scala.util.matching.Regex.Match
import scala.util.{Failure, Success, Try}

class Logger{
  def info(st:String) = println("[INFO] " + st)
  def debug(st:String) = println("[DEBUG] " + st)
}

object ReleaserMain {
  def main(args: Array[String]) {
    val result = Releaser.main(args)
    System.exit(result)
  }

}

trait Clock{
  def now():DateTime
}

class SystemClock extends Clock {
  def now():DateTime = DateTime.now
}


object ReleaseType extends Enumeration {
  type Margin = Value
  val MAJOR, MINOR, PATCH = Value

  val stringValues: SortedSet[String] = this.values.map(_.toString)
}

object Releaser {

  import ArgParser._

  val mavenRepository: RepoFlavour = new BintrayRepository("release-candidates", "releases") with MavenRepo
  val ivyRepository:   RepoFlavour = new BintrayRepository("sbt-plugin-release-candidates", "sbt-plugin-releases") with IvyRepo

  val log = new Logger()

  def main(args: Array[String]):Int= {
    parser.parse(args, Config()) match {
      case Some(config) => start(config.artefactName, config.rcVersion, config.releaseType)
      case None => -1
    }
  }

  def start(artefactName: String, rcVersion: String, releaseType: ReleaseType.Value)={
    val tmpDir = Files.createTempDirectory("releaser")

    val githubCreds  = ServiceCredentials(Option(System.getenv("GITHUB_USER")).getOrElse(""), Option(System.getenv("GITHUB_PASS")).getOrElse(""))
    val bintrayCreds = ServiceCredentials(Option(System.getenv("BINTRAY_USER")).getOrElse(""), Option(System.getenv("BINTRAY_PASS")).getOrElse(""))

    val releaser = buildReleaser(tmpDir, githubCreds, bintrayCreds)
    val targetVersion = VersionNumberCalculator.calculateTarget(rcVersion, releaseType)

    targetVersion
      .map { tv => releaser.start(artefactName, rcVersion, tv) }
      .map { _ => log.info(s"deleting $tmpDir"); FileUtils.forceDelete(tmpDir.toFile) } match {
        case Failure(e) => log.info(s"failed with error '${e.getMessage}'");e.printStackTrace(); 1
        case Success(_) => 0;
      }
  }

  //TODO not tested
  def buildReleaser(
                     tmpDir:Path,
                     githubCreds: ServiceCredentials,
                     bintrayCreds: ServiceCredentials): Releaser = {

    val githubConnector = new GithubHttp(githubCreds)
    val bintrayConnector = new BintrayHttp(bintrayCreds)
    val clock = new SystemClock()

    val workDir = Files.createDirectories(tmpDir.resolve("work"))
    val stageDir = Files.createDirectories(tmpDir.resolve("stage"))

    val metaDataGetter = new BintrayMetaConnector(bintrayConnector).getRepoMetaData _
    val repoConnectorBuilder = BintrayRepoConnector.apply(workDir, bintrayConnector) _
    val githubPublisher = new GithubApi(clock).postTag(githubConnector.post) _

    val artefactBuilder = ArtefactMetaData.fromFile _

    val coordinator = new Coordinator(stageDir, artefactBuilder, githubPublisher)
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
                   githubTagPublisher:(ArtefactMetaData, VersionMapping) => Try[Unit]){

  val logger = new Logger()

  val manifestTransformer = new ManifestTransformer(stageDir)

  def start(map: VersionMapping, connector:RepoConnector): Try[Unit] ={
    for(
      man <- uploadNewJar(map, connector);
      _   <- uploadNewPom(map, connector);
      _   <- publish(map, connector);
      _   <- githubTagPublisher(man, map)
    ) yield ()
  }

  def uploadNewPom(map:VersionMapping, connector:RepoConnector): Try[Unit] = {

    for(
      localFile   <- connector.downloadPom(map.sourceArtefact);
      transformed <- map.repo.pomTransformer.apply(localFile, map.targetVersion, map.repo.pomFilenameFor(map.targetArtefact));
      _           <- connector.uploadPom(map.targetArtefact, transformed)
    ) yield ()
  }

  def uploadNewJar(map:VersionMapping, connector:RepoConnector): Try[ArtefactMetaData] ={
    for(
      localFile   <- connector.downloadJar(map.sourceArtefact);
      path        <- manifestTransformer(localFile, map.targetVersion, map.repo.jarFilenameFor(map.targetArtefact));
      _           <- connector.uploadJar(map.targetArtefact, path);
      metaData    <- artefactBuilder(localFile)
    ) yield metaData
  }

  def publish(map: VersionMapping, connector:RepoConnector): Try[Unit] = {
    connector.publish(map.targetArtefact)
  }
}

