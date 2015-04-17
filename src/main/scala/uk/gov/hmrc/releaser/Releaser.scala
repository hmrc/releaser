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

import java.io.File
import java.net.URL
import java.nio.file.Files

import scala.sys.process._
import scala.util.{Failure, Success, Try}

class Logger{
  def info(st:String) = println(st)
}

object Releaser {

  val reposToTry = Seq("sbt-plugin-release-candidates")
  val workDir = Files.createTempDirectory("releaser").toFile
  val downloader = new BintrayConnector(new BintrayIvyPaths("https://bintray.com/artifact/download/hmrc/"), workDir)

  val log = new Logger()

  def main(args: Array[String]) {
    new Releaser(downloader, reposToTry).start(args)
  }
  
  def calculateTarget(releaseType: String): String = "1.0.0"
}

class Releaser(bintray:BintrayConnector, reposToTry:Seq[String]){

  import Releaser._

  def start(args: Array[String]): Int ={
    val artefactName: String = args(0)
    val rcVersion: String = args(1)
    val targetVersion: String = calculateTarget(args(2))

    val transformedZipOpt = bintray.download(artefactName, rcVersion, reposToTry) flatMap { case(repo, localZipFile) =>
      Transformer(localZipFile, targetVersion) map { transformed =>
        repo -> transformed
      }
    }

    val result = transformedZipOpt map { case (repo, transformedZip) =>
      bintray.upload(repo, transformedZip)
    }

    result match {
      case Failure(e) => e.printStackTrace(); 1
      case Success(_) => 0;
    }
  }

}

//https://bintray.com/artifact/download/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26/jars/sbt-bobby.jar
class BintrayIvyPaths(bintrayRepoRoot:String){

  val sbtVersion = "sbt_0.13"

  def jarFilenameFor(artefactName:String):String={
    s"$artefactName.jar"
  }

  def jarUrlFor(repo:String, artefactName:String, scalaVersion:String, releaseCandidateVersion:String):String={
    val fileName = jarFilenameFor(artefactName)
    s"$bintrayRepoRoot$repo/uk.gov.hmrc/${artefactName}/scala_$scalaVersion/$sbtVersion/${releaseCandidateVersion}/jars/$fileName"
  }
}

//https://bintray.com/artifact/download/hmrc/releases/uk/gov/hmrc/http-verbs_2.11/1.4.0/http-verbs_2.11-1.4.0-javadoc.jar
class BintrayPaths(bintrayRepoRoot:String){
  def jarFilenameFor(artefactName:String, scalaVersion:String, releaseCandidateVersion:String):String={
    s"${artefactName}_$scalaVersion-$releaseCandidateVersion.jar"
  }

  def jarUrlFor(repo:String, artefactName:String, scalaVersion:String, releaseCandidateVersion:String):String={
    val fileName = jarFilenameFor(artefactName, scalaVersion, releaseCandidateVersion)
    s"$bintrayRepoRoot$repo/uk/gov/hmrc/${artefactName}_$scalaVersion/${releaseCandidateVersion}/$fileName"
  }
}

class BintrayConnector(bintrayPaths:BintrayIvyPaths, workDir:File){

  val scalaVersion = "2.10"
  
  def upload(repo:String, jarFile:File):Try[Unit] = ???

  def download(artefactName:String, releaseCandidateVersion:String, reposToTry:Seq[String]):Try[(String, File)] = {

    val fileName = bintrayPaths.jarFilenameFor(artefactName)
    val artefactUrl = bintrayPaths.jarUrlFor(reposToTry.head, artefactName, scalaVersion, releaseCandidateVersion)
    val targetZipFile = new File(workDir, fileName)

    Http.url2File(artefactUrl, targetZipFile).map { unit =>
      reposToTry.head -> targetZipFile
    }
  }

}

object Http{

  val log = new Logger()

  def url2File(url: String, targetFile: File): Try[Unit] = Try {
    log.info(s"downloading $url to $targetFile")
    new URL(url) #> targetFile !!
  }
}

object Transformer{
  def apply(localZipFile:File, targetVersion:String):Try[File] = ???
}
