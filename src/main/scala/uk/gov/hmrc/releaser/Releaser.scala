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
import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient}


import java.io.{FileOutputStream, File}
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.TimeUnit

import play.api.libs.ws._
import play.api.libs.ws.ning.NingWSClient

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

  val workDir = Files.createTempDirectory("releaser").toFile
  val paths: BintrayMavenPaths = new BintrayMavenPaths()
  val downloader = new BintrayConnector(paths, workDir)
  val transformer = new Transformer(workDir)

  val log = new Logger()

  def main(args: Array[String]):Int= {
    new Releaser(downloader, transformer, paths, releaseCandidateRepo, releaseRepo).start(args)
  }
  
  def calculateTarget(releaseType: String): String = "0.9.9"
}

case class VersionDescriptor(repo:String, artefactName:String, scalaVersion:String, version:String)


class Releaser(bintray:Connector, transformer:Transformer, pathBuilder: PathBuilder, releaseCandidateRepo:String, releaseRepo:String){

  val scalaVersion: String = "2.11"

  def start(args: Array[String]): Int = {
    val artefactName: String = args(0)
    val rcVersion: String = args(1)
    val targetVersionString: String = args(2)

    release(artefactName, rcVersion, targetVersionString) match {
      case Failure(e) => e.printStackTrace(); 1
      case Success(_) => 0;
    }
  }

  def release(artefactName: String, rcVersion:String, targetVersionString:String): Try[Unit] ={

    val sourceVersion = VersionDescriptor(releaseCandidateRepo, artefactName, scalaVersion, rcVersion)
    val targetVersion = VersionDescriptor(releaseRepo, artefactName, scalaVersion, targetVersionString)

    for(
      localZipFile <- bintray.download(sourceVersion);
      transformed  <- transformer(localZipFile, targetVersionString, pathBuilder.jarFilenameFor(targetVersion));
      result       <- bintray.upload(targetVersion, transformed)
    ) yield result
  }

}

//https://bintray.com/artifact/download/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26/jars/sbt-bobby.jar
//https://bintray.com/api/v1/   content/hmrc/sbt-plugin-releases          /uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/1.0.0/jars/sbt-bobby.jar
class BintrayIvyPaths() extends PathBuilder {

  val sbtVersion = "sbt_0.13"
  val bintrayRepoRoot = "https://bintray.com/artifact/download/hmrc/"
  val bintrayApiRoot = "https://bintray.com/api/v1/content/hmrc/"

  override def jarFilenameFor(v:VersionDescriptor):String={
    s"${v.artefactName}.jar"
  }

  override def jarUrlFor(v:VersionDescriptor):String={
    val fileName = jarFilenameFor(v)
    s"$bintrayRepoRoot${v.repo}/uk.gov.hmrc/${v.artefactName}/scala_${v.scalaVersion}/${sbtVersion}/${v.version}/jars/$fileName"
  }

  override def jarUploadFor(v:VersionDescriptor):String={
    val fileName = jarFilenameFor(v)
    s"$bintrayApiRoot${v.repo}/uk.gov.hmrc/${v.artefactName}/scala_${v.scalaVersion}/$sbtVersion/${v.version}/jars/$fileName"
  }
}

//https://bintray.com/artifact/download/hmrc/releases/uk/gov/hmrc/http-verbs_2.11/1.4.0/http-verbs_2.11-1.4.0.jar
class BintrayMavenPaths() extends PathBuilder{

  val bintrayRepoRoot = "https://bintray.com/artifact/download/hmrc/"
  val bintrayApiRoot = "https://bintray.com/api/v1/maven/hmrc/"

  def jarFilenameFor(v:VersionDescriptor):String={
    s"${v.artefactName}_${v.scalaVersion}-${v.version}.jar"
  }

  def jarUrlFor(v:VersionDescriptor):String={
    val fileName = jarFilenameFor(v)
    s"$bintrayRepoRoot${v.repo}/uk/gov/hmrc/${v.artefactName}_${v.scalaVersion}/${v.version}/$fileName"
  }


  def jarUploadFor(v:VersionDescriptor):String={
    val fileName = jarFilenameFor(v)
    s"$bintrayApiRoot${v.repo}/${v.artefactName}/uk/gov/hmrc/${v.artefactName}_${v.scalaVersion}/${v.version}/$fileName"
  }
}

trait Connector{
  def upload(version: VersionDescriptor, jarFile:File):Try[Unit];
  def download(version:VersionDescriptor):Try[File]
}

class BintrayConnector(bintrayPaths:PathBuilder, workDir:File) extends Connector{

  val log = new Logger()

  val scalaVersion = "2.10"

  val ws = new NingWSClient(new NingAsyncHttpClientConfigBuilder(new DefaultWSClientConfig).build())

  def upload(version: VersionDescriptor, jarFile:File):Try[Unit] = {

    val url = bintrayPaths.jarUploadFor(version)

    log.info(s"version $version")
    log.info(s"posting file to $url")
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
      .put(jarFile)

    val result: WSResponse = Await.result(call, Duration.apply(1, TimeUnit.MINUTES))

    log.info(s"result ${result.status} - ${result.statusText}")

    result.status match {
      case s if s >= 200 && s < 300 => Success(Unit)
      case _ @ e => Failure(new Exception(s"Didn't get expected status code when writing to Bintray. Got status ${result.status}: ${result.body}"))
    }
  }

  def download(version:VersionDescriptor):Try[File] = {

    val fileName = bintrayPaths.jarFilenameFor(version)
    val artefactUrl = bintrayPaths.jarUrlFor(version)
    val targetZipFile = new File(workDir, fileName)

    Http.url2File(artefactUrl, targetZipFile) map { unit => targetZipFile }
  }

}

object Http{

  val log = new Logger()

  def url2File(url: String, targetFile: File): Try[Unit] = Try {
    log.info(s"downloading $url to $targetFile")
    new URL(url) #> targetFile !!
  }
}


