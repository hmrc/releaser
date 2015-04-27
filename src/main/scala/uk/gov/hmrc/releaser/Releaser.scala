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

import scala.collection.JavaConversions._
import java.net.URL
import java.nio.file.{Files, Path}
import java.util.jar.Manifest
import java.util.zip.{ZipFile, ZipEntry}

import com.google.common.io.ByteStreams
import org.apache.commons.io.FileUtils
import org.joda.time.DateTime
import play.api.data.format.Formats
import play.api.libs.json.{JsValue, Json}

import scala.util.{Failure, Success, Try}

class Logger{
  def info(st:String) = println(st)
}

object ReleaserMain {
  def main(args: Array[String]) {
    if(args.length != 3){
      println("<artefact name> <release-candidate-version> <target-version>")
      System.exit(1)
    }

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

object Releaser {

  val mavenRepository: RepoFlavour = new BintrayRepository("release-candidates", "releases") with MavenRepo
  val ivyRepository:   RepoFlavour = new BintrayRepository("sbt-plugin-release-candidates", "sbt-plugin-releases") with IvyRepo


  val log = new Logger()

  def main(args: Array[String]):Int= {

    val tmpDir   = Files.createTempDirectory("releaser")
    val workDir  = Files.createDirectories(tmpDir.resolve("work"))
    val stageDir = Files.createDirectories(tmpDir.resolve("stage"))

    val githubConnector = new GithubHttp("token")
    val bintrayConnector = new BintrayHttp

    val metaDataGetter = new BintrayMetaConnector(bintrayConnector).getRepoMetaData _
    val repoConnectorBuilder = BintrayRepoConnector.apply(workDir, bintrayConnector) _
    val artefactBuilder = ArtefactMetaData.fromFile(new SystemClock()) _

    val coordinator = new Coordinator(stageDir, artefactBuilder, Github.postTag(githubConnector.post))
    val repoFinder  = new Repositories(metaDataGetter)(Seq(mavenRepository, ivyRepository)).findReposOfArtefact _
    val releaser = new Releaser(stageDir, repoFinder, repoConnectorBuilder, coordinator)

    releaser.start(args)
      .map{ _ => log.info(s"deleting $tmpDir"); FileUtils.forceDelete(tmpDir.toFile) } match {
        case Failure(e) => log.info(s"failed with error '${e.getMessage}'");e.printStackTrace(); 1
        case Success(_) => 0;
      }
  }
  
  def calculateTarget(releaseType: String): String = "0.9.9"
}

trait RepoFlavour extends PathBuilder{
//  def pathBuilder:PathBuilder
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

  def start(args: Array[String]): Try[Unit] = {
    val artefactName: String = args(0)
    val rcVersion: String = args(1)
    val targetVersionString: String = args(2)

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

  import Pimps._

  def fromFile(clock:Clock)(p:Path):Try[ArtefactMetaData] = {
    Try {new ZipFile(p.toFile) }.flatMap { jarFile =>
      jarFile.entries().filter(_.getName == "META-INF/MANIFEST.MF").toList.headOption.map { ze =>
        val man = new Manifest(jarFile.getInputStream(ze))
        ArtefactMetaData(
          man.getMainAttributes.getValue("Git-Head-Rev"),
          man.getMainAttributes.getValue("Implementation-Title"), clock.now())
      }.toTry(new Exception(s"Failed to retrieve maniefts from $p"))
    }
  }
}

case class ArtefactMetaData(sha:String, name:String, date:DateTime)

//class GithubTagger(github:GithubHttp){
object Github{

  val taggerName = "hmrc-web-operations"
  val taggerEmail = "hmrc-web-operations@hmrc.digital.gov.uk"

  case class GitTagger( name:String, email:String, date:DateTime)
  case class GitTag(tag:String, message:String, `object`:String, tagger:GitTagger, `type`:String = "commit" )

  object GitTagger { implicit val formats = Json.format[GitTagger] }
  object GitTag    { implicit val formats = Json.format[GitTag] }

  def postTag(githubConnector: (String, JsValue) => Try[Unit])(a: ArtefactMetaData, v: VersionMapping): Try[Unit] = {
    githubConnector(Github.url(v), Github.tag(v, a))
  }

  def tag(versionMapping:VersionMapping, pomData:ArtefactMetaData):JsValue={
    //POST /repos/:owner/:repo/git/tags
//
//    {
//      "tag": "v0.0.1",
//      "message": "initial version\n",
//      "object": "c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c",
//      "type": "commit",
//      "tagger": {
//        "name": "Scott Chacon",
//        "email": "schacon@gmail.com",
//        "date": "2011-06-17T14:53:35-07:00"
//      }
//    }

    val message = s"releasing"
    val tag: GitTag = GitTag(versionMapping.targetVersion, message, pomData.sha, GitTagger(taggerName, taggerEmail, DateTime.now()))

    Json.toJson(tag)
  }

  def url(artefactName:VersionMapping)={
    s"https://github.com/repos/hmrc/${artefactName.artefactName}/git/tags"
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

