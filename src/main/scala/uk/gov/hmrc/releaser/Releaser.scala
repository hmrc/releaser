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
    val coordinatorBuilder = Coordinator.apply(stageDir) _
    new Releaser(stageDir, repositories, repoConnectorBuilder, coordinatorBuilder)
      .start(args)
      .map{ _ => log.info(s"deleting $tmpDir"); FileUtils.forceDelete(tmpDir.toFile) } match {
        case Failure(e) => log.info(s"failed with error '${e.getMessage}'");e.printStackTrace(); 1
        case Success(_) => 0;
      }
  }
  
  def calculateTarget(releaseType: String): String = "0.9.9"
}

case class VersionDescriptor(repo:String, artefactName:String, scalaVersion:String, version:String)


trait RepoFlavour{
  def pathBuilder:PathBuilder  
  def scalaVersion:String
  def releaseCandidateRepo:String
  def releaseRepo:String
  def pomTransformer(workDir:Path):Transformer
}

trait IvyRepo extends RepoFlavour{
  val pathBuilder = new BintrayIvyPaths
  val scalaVersion = "2.10"
  def pomTransformer(workDir:Path) = new IvyTransformer(workDir)
}

trait MavenRepo extends RepoFlavour{
  val pathBuilder = new BintrayMavenPaths
  val scalaVersion = "2.11"
  def pomTransformer(workDir:Path) = new PomTransformer(workDir)
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
               connectorBuilder:(PathBuilder) => RepoConnector,
               coordinatorBuilder:(RepoConnector, PathBuilder, Transformer) => Coordinator
                ){

  def start(args: Array[String]): Try[Unit] = {
    val artefactName: String = args(0)
    val rcVersion: String = args(1)
    val targetVersionString: String = args(2)

    publishNewVersion(artefactName, rcVersion, targetVersionString)
  }

  def publishNewVersion(
                         artefactName: String,
                         rcVersion: String,
                         targetVersionString: String): Try[Unit] ={

    repositories.findReposOfArtefact(artefactName) map { repo =>
      val connector = connectorBuilder(repo.pathBuilder)
      val sourceVersion = VersionDescriptor(repo.releaseCandidateRepo, artefactName, repo.scalaVersion, rcVersion)
      val targetVersion = VersionDescriptor(repo.releaseRepo, artefactName, repo.scalaVersion, targetVersionString)

      coordinatorBuilder(connector, repo.pathBuilder, repo.pomTransformer(stageDir)).start(sourceVersion, targetVersion)
    }
  }

}

object Coordinator{
  def apply(stageDir:Path)(connector:RepoConnector, bintrayPaths:PathBuilder, pomTransformer:Transformer):Coordinator={
    new Coordinator(stageDir, connector, bintrayPaths, pomTransformer)
  }
}

class Coordinator(stageDir:Path, connector:RepoConnector, bintrayPaths:PathBuilder, pomTransformer:Transformer){

  val logger = new Logger()

  val manifestTransformer = new ManifestTransformer(stageDir)

  def start(sourceVersion:VersionDescriptor, targetVersion:VersionDescriptor): Try[Unit] ={
    for(
      _ <- uploadNewJar(sourceVersion, targetVersion, connector);
      _ <- uploadNewPom(sourceVersion, targetVersion, connector)
    ) yield publish(targetVersion)
  }


  def publish(targetVersion: VersionDescriptor): Try[Unit] = {
    connector.publish(targetVersion)
  }

  def uploadNewPom(sourceVersion:VersionDescriptor, target:VersionDescriptor, bintray:RepoConnector): Try[URL] = {
    logger.info(s"uploadNewPom mapping ${bintrayPaths.pomFilenameFor(sourceVersion)} -> ${bintrayPaths.pomFilenameFor(target)}")

    for(
      localFile    <- bintray.downloadPom(sourceVersion);
      transformed  <- pomTransformer(localFile, target.version, bintrayPaths.pomFilenameFor(target));
      jarUrl       <- bintray.uploadPom(target, transformed)
    ) yield jarUrl
  }

  def uploadNewJar(sourceVersion:VersionDescriptor, target:VersionDescriptor, bintray:RepoConnector): Try[URL] ={
    logger.info(s"uploadNewJar mapping ${bintrayPaths.jarFilenameFor(sourceVersion)} -> ${bintrayPaths.jarFilenameFor(target)}")

    for(
      localZipFile <- bintray.downloadJar(sourceVersion);
      transformed  <- manifestTransformer(localZipFile, target.version, bintrayPaths.jarFilenameFor(target));
      jarUrl       <- bintray.uploadJar(target, transformed)
    ) yield jarUrl
  }
}
