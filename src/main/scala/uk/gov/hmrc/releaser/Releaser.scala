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

import org.joda.time.format.DateTimeFormat

import scala.collection.JavaConversions._
import java.net.URL
import java.nio.file.{Files, Path}
import java.util.jar.Manifest
import java.util.zip.{ZipFile, ZipEntry}

import com.google.common.io.ByteStreams
import org.apache.commons.io.FileUtils
import org.joda.time.DateTime
import play.api.data.format.Formats
import play.api.libs.json.{Writes, JsString, JsValue, Json}

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
  def now:DateTime = DateTime.now
}

case class ServiceCredentials(user:String, pass:String)



object Releaser {

  import ArgParser._

  val mavenRepository: RepoFlavour = new BintrayRepository("release-candidates", "releases") with MavenRepo
  val ivyRepository:   RepoFlavour = new BintrayRepository("sbt-plugin-release-candidates", "sbt-plugin-releases") with IvyRepo


  val log = new Logger()

  def main(args: Array[String]):Int= {
    parser.parse(args, Config()) match {
      case Some(config) => start(config.artefactName, config.rcVersion, "0.0.0")
      case None => -1
    }
  }

  def start(artefactName: String, rcVersion: String, targetVersion: String)={
    val tmpDir = Files.createTempDirectory("releaser")

    val githubCreds  = ServiceCredentials(Option(System.getenv("GITHUB_USER")).getOrElse(""), Option(System.getenv("GITHUB_PASS")).getOrElse(""))
    val bintrayCreds = ServiceCredentials(Option(System.getenv("BINTRAY_USER")).getOrElse(""), Option(System.getenv("BINTRAY_PASS")).getOrElse(""))

    val releaser = buildReleaser(tmpDir, githubCreds, bintrayCreds)

    releaser.start(artefactName, rcVersion, targetVersion)
      .map{ _ => log.info(s"deleting $tmpDir"); FileUtils.forceDelete(tmpDir.toFile) } match {
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

  def calculateTarget(releaseType: String): String = "0.9.9"
}

trait RepoFlavour extends PathBuilder{
  val workDir:Path = Files.createTempDirectory("releaser")
  
  def scalaVersion:String
  def releaseCandidateRepo:String
  def releaseRepo:String
  def pomTransformer:XmlTransformer
}

trait IvyRepo extends RepoFlavour with BintrayIvyPaths{
  val scalaVersion = "2.10"
  val pomTransformer = new IvyTransformer(workDir)
}

trait MavenRepo extends RepoFlavour with BintrayMavenPaths{
  val scalaVersion = "2.11"
  val pomTransformer = new PomTransformer(workDir)
}


case class BintrayRepository(releaseCandidateRepo:String, releaseRepo:String)

class Repositories(metaDataGetter:(String, String) => Try[Unit])(repos:Seq[RepoFlavour]){

  def findReposOfArtefact(artefactName: String): Try[RepoFlavour] = {
    repos.find { repo =>
      metaDataGetter(repo.releaseCandidateRepo, artefactName).isSuccess
    } match {
      case Some(r) => Success(r)
      case None => Failure(new Exception(s"Didn't find a release candidate repository for $artefactName"))
    }
  }}

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

case class VersionDescriptor(repo:String, artefactName:String, version:String)


case class VersionMapping (
                          repo:RepoFlavour,
                          artefactName:String, 
                          sourceVersion:String,
                          targetVersion:String
                          ) {
  
  def targetArtefact = VersionDescriptor(repo.releaseRepo, artefactName, targetVersion)
  def sourceArtefact = VersionDescriptor(repo.releaseCandidateRepo, artefactName, sourceVersion)

}

object Pimps{
  implicit class OptionPimp[A](opt:Option[A]){
    def toTry(e:Exception):Try[A] = opt match {
      case Some(x) => Success(x)
      case None => Failure(e)
    }
  }
}

object ArtefactMetaData{

  val gitCommitDateFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  import Pimps._

  def fromFile(p:Path):Try[ArtefactMetaData] = {
    Try {new ZipFile(p.toFile) }.flatMap { jarFile =>
      jarFile.entries().filter(_.getName == "META-INF/MANIFEST.MF").toList.headOption.map { ze =>
        val man = new Manifest(jarFile.getInputStream(ze))
        ArtefactMetaData(
          man.getMainAttributes.getValue("Git-Head-Rev"),
          man.getMainAttributes.getValue("Git-Commit-Author"),
          gitCommitDateFormat.parseDateTime(man.getMainAttributes.getValue("Git-Commit-Date"))
        )
      }.toTry(new Exception(s"Failed to retrieve manifest from $p"))
    }
  }
}

case class ArtefactMetaData(sha:String, commitAuthor:String, commitDate:DateTime)

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

