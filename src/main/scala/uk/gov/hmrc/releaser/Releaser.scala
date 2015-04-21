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
import java.net.URL
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit

import play.api.libs.ws._
import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient}
import play.api.mvc.Results

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.sys.process._
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

//  val releaseCandidateRepo = "sbt-plugin-release-candidates"
  val releaseCandidateRepo = "release-candidates"
//  val releaseRepo = "sbt-plugin-releases"
  val releaseRepo = "releases"

  val workDir = Files.createTempDirectory("releaser")
  val paths: BintrayMavenPaths = new BintrayMavenPaths()
  val downloader = new BintrayConnector(paths, workDir)

  val log = new Logger()

  def main(args: Array[String]):Int= {
    new Releaser(downloader, workDir, paths, releaseCandidateRepo, releaseRepo).start(args)
  }
  
  def calculateTarget(releaseType: String): String = "0.9.9"
}

case class VersionDescriptor(repo:String, artefactName:String, scalaVersion:String, version:String)


class Releaser(bintray:Connector, stageDir:Path, pathBuilder: PathBuilder, releaseCandidateRepo:String, releaseRepo:String){

  val scalaVersion: String = "2.11"

  val manifestTransformer = new ManifestTransformer(stageDir)
  val pomTransformer      = new PomTransformer(stageDir)


  def start(args: Array[String]): Int = {
    val artefactName: String = args(0)
    val rcVersion: String = args(1)
    val targetVersionString: String = args(2)

    publishNewVersion(artefactName, rcVersion, targetVersionString) match {
      case Failure(e) => e.printStackTrace(); 1
      case Success(_) => 0;
    }
  }

  def publish(targetVersion: VersionDescriptor): Try[URL] = {
    bintray.publish(targetVersion)
  }

  def publishNewVersion(artefactName: String, rcVersion: String, targetVersionString: String): Try[Set[URL]] ={
    val sourceVersion = VersionDescriptor(releaseCandidateRepo, artefactName, scalaVersion, rcVersion)
    val targetVersion = VersionDescriptor(releaseRepo, artefactName, scalaVersion, targetVersionString)

    val result = for(
      jarUrl <- uploadNewJar(sourceVersion, targetVersion);
      pomUrl <- uploadNewPom(sourceVersion, targetVersion)
    ) yield Set(pomUrl, jarUrl)

    result flatMap { urls =>
      publish(targetVersion).map { _ => urls }
    }
  }

  def uploadNewPom(sourceVersion:VersionDescriptor, target:VersionDescriptor): Try[URL] = {

    for(
      localFile    <- bintray.downloadPom(sourceVersion);
      transformed  <- pomTransformer(localFile, target.version, pathBuilder.pomFilenameFor(target));
      jarUrl       <- bintray.uploadPom(target, transformed)
    ) yield jarUrl  }

  def uploadNewJar(sourceVersion:VersionDescriptor, target:VersionDescriptor): Try[URL] ={

    for(
      localZipFile <- bintray.downloadJar(sourceVersion);
      transformed  <- manifestTransformer(localZipFile, target.version, pathBuilder.jarFilenameFor(target));
      jarUrl       <- bintray.uploadJar(target, transformed)
    ) yield jarUrl
  }
}


trait Connector{
  def uploadJar(version: VersionDescriptor, jarFile:Path):Try[URL]
  def downloadJar(version:VersionDescriptor):Try[Path]
  def uploadPom(version: VersionDescriptor, pomPath:Path):Try[URL]
  def downloadPom(version:VersionDescriptor):Try[Path]
  def publish(version: VersionDescriptor):Try[URL]
}

class BintrayConnector(bintrayPaths:PathBuilder, workDir:Path) extends Connector{

  val log = new Logger()

  val scalaVersion = "2.10"

  val ws = new NingWSClient(new NingAsyncHttpClientConfigBuilder(new DefaultWSClientConfig).build())

  def uploadPom(version: VersionDescriptor, pomFile:Path):Try[URL] ={
    val url = bintrayPaths.pomUploadFor(version)
    putFile(version, pomFile, url)
  }

  def uploadJar(version: VersionDescriptor, jarFile:Path):Try[URL] = {
    val url = bintrayPaths.jarUploadFor(version)
    putFile(version, jarFile, url)
  }

  def publish(version: VersionDescriptor):Try[URL]={
    val url = bintrayPaths.publishUrlFor(version)
    publishFile(url)
  }

  def downloadPom(version:VersionDescriptor):Try[Path]={

    val fileName = bintrayPaths.pomFilenameFor(version)
    val pomUrl = bintrayPaths.pomUrlFor(version)

    downloadFile(pomUrl, fileName)
  }


  def downloadJar(version:VersionDescriptor):Try[Path] = {

    val fileName = bintrayPaths.jarFilenameFor(version)
    val artefactUrl = bintrayPaths.jarUrlFor(version)

    downloadFile(artefactUrl, fileName)
  }

  def downloadFile(url:String, fileName:String):Try[Path]={
    val targetFile = workDir.resolve(fileName)

    Http.url2File(url, targetFile) map { unit => targetFile }
  }

  def publishFile(url:String): Try[URL] ={
    log.info(s"posting file to $url")

    val call = ws.url(url)
      .withAuth(
        System.getenv("BINTRAY_USER"),
        System.getenv("BINTRAY_PASS"),
        WSAuthScheme.BASIC)
      .withHeaders(
        "content-type" -> "application/json")
      .post(Results.EmptyContent())

    val result: WSResponse = Await.result(call, Duration.apply(1, TimeUnit.MINUTES))

    log.info(s"result ${result.status} - ${result.statusText}")

    result.status match {
      case s if s >= 200 && s < 300 => Success(new URL(url))
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Bintray. Got status ${result.status}: ${result.body}"))
    }
  }

  def putFile(version: VersionDescriptor, file: Path, url: String): Try[URL] = {
    log.info(s"version $version")
    log.info(s"putting file to $url")
    log.info(s"bintray user ${System.getenv("BINTRAY_USER")}")

    val call = ws.url(url)
      .withAuth(
        System.getenv("BINTRAY_USER"),
        System.getenv("BINTRAY_PASS"),
        WSAuthScheme.BASIC)
      .withHeaders(
        "content-type" -> "application/json",
        "X-Bintray-Package" -> version.artefactName,
        "X-Bintray-Version" -> version.version)
      .put(file.toFile)

    val result: WSResponse = Await.result(call, Duration.apply(1, TimeUnit.MINUTES))

    log.info(s"result ${result.status} - ${result.statusText}")

    result.status match {
      case s if s >= 200 && s < 300 => Success(new URL(url))
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Bintray. Got status ${result.status}: ${result.body}"))
    }
  }
}

object Http{

  val log = new Logger()

  def url2File(url: String, targetFile: Path): Try[Unit] = Try {
    log.info(s"downloading $url to $targetFile")
    new URL(url) #> targetFile.toFile !!
  }
}


