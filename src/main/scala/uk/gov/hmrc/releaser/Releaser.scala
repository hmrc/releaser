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

import java.net.URL
import java.nio.file.{Files, Path}

import org.apache.commons.io.FileUtils

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

object Releaser {

  val tmpDir   = Files.createTempDirectory("releaser")
  val workDir  = Files.createDirectories(tmpDir.resolve("work"))
  val stageDir = Files.createDirectories(tmpDir.resolve("stage"))

  val mavenRepository: RepoFlavour = new BintrayRepository("release-candidates", "releases") with MavenRepo
  val ivyRepository:   RepoFlavour = new BintrayRepository("sbt-plugin-release-candidates", "sbt-plugin-releases") with IvyRepo

  val http = new BintrayHttp

  val repositories  = new Repositories {
    override def connector: BintrayMetaConnector = new BintrayMetaConnector(http)
    override def repos: Seq[RepoFlavour] = Seq(mavenRepository, ivyRepository)
  }


  val log = new Logger()

  def main(args: Array[String]):Int= {
    val repoConnectorBuilder = BintrayRepoConnector.apply(workDir, http) _
    val coordinator = new Coordinator(stageDir)
    new Releaser(stageDir, repositories, repoConnectorBuilder, coordinator)
      .start(args)
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

trait Repositories{
  def connector:BintrayMetaConnector
  def repos:Seq[RepoFlavour]

  def findReposOfArtefact(artefactName: String): Try[RepoFlavour] = {
    repos.find { repo =>
      connector.getRepoMetaData(repo.releaseCandidateRepo, artefactName).isSuccess
    } match {
      case Some(r) => Success(r)
      case None => Failure(new Exception(s"Didn't find a release candidate repository for $artefactName"))
    }
  }}

class Releaser(stageDir:Path,
               repositories:Repositories,
               connectorBuilder:(RepoFlavour) => RepoConnector,
               coordinator:Coordinator
                ){

  def start(args: Array[String]): Try[Unit] = {
    val artefactName: String = args(0)
    val rcVersion: String = args(1)
    val targetVersionString: String = args(2)

    repositories.findReposOfArtefact(artefactName) flatMap { repo =>
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


class Coordinator(stageDir:Path){

  val logger = new Logger()

  val manifestTransformer = new ManifestTransformer(stageDir)

  def start(map: VersionMapping, connector:RepoConnector): Try[Unit] ={
    for(
      _ <- uploadNewJar(map, connector);
      _ <- uploadNewPom(map, connector);
      _ <- publish(map, connector)
    ) yield ()
  }

  def uploadNewPom(map:VersionMapping, connector:RepoConnector): Try[Unit] = {

    for(
      localFile   <- connector.downloadPom(map.sourceArtefact);
      transformed <- map.repo.pomTransformer.apply(localFile, map.targetVersion, map.repo.pomFilenameFor(map.targetArtefact));
      _           <- connector.uploadPom(map.targetArtefact, transformed)
    ) yield ()
  }

  def uploadNewJar(map:VersionMapping, connector:RepoConnector): Try[Unit] ={
    for(
      localFile   <- connector.downloadJar(map.sourceArtefact);
      transformed <- manifestTransformer(localFile, map.targetVersion, map.repo.jarFilenameFor(map.targetArtefact));
      _           <- connector.uploadJar(map.targetArtefact, transformed)
    ) yield ()
  }

  def publish(map: VersionMapping, connector:RepoConnector): Try[Unit] = {
    connector.publish(map.targetArtefact)
  }
}

