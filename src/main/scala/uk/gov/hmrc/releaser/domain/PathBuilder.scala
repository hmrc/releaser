/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.releaser.domain


trait BintrayPaths{

  val bintrayRepoRoot = "https://bintray.com/artifact/download/hmrc"
  val bintrayApiRoot = "https://bintray.com/api/v1"

  def metadata(repo:String, artefactName: String): String = {
    s"$bintrayApiRoot/packages/hmrc/$repo/$artefactName"
  }

  def publishUrlFor(v: VersionDescriptor): String = {
    s"$bintrayApiRoot/content/hmrc/${v.repo}/${v.artefactName}/${v.version.value}/publish"
  }

  def fileListUrlFor(v: VersionDescriptor): String={
    s"$bintrayApiRoot/packages/hmrc/${v.repo}/${v.artefactName}/versions/${v.version.value}/files"
  }
}

object BintrayPaths extends BintrayPaths

trait PathBuilder extends BintrayPaths{

  def filenameFor(v:VersionDescriptor, suffix:String): String

  def jarFilenameFor(v: VersionDescriptor): String

  def jarDownloadFor(v: VersionDescriptor): String

  def jarUploadFor(v: VersionDescriptor): String

  def fileDownloadFor(v: VersionDescriptor, fileName:String): String

  def fileUploadFor(v: VersionDescriptor, fileName:String): String

  def filePrefixFor(v: VersionDescriptor):String
}

trait BintrayIvyPaths extends PathBuilder {

  val sbtVersion = "sbt_0.13"
  def scalaVersion:String

  override def filenameFor(v:VersionDescriptor, suffix:String):String={
    s"$suffix"
  }

  override def jarFilenameFor(v:VersionDescriptor):String={
    s"${v.artefactName}.jar"
  }

  override def jarDownloadFor(v:VersionDescriptor):String={
    val fileName = jarFilenameFor(v)
    s"$bintrayRepoRoot/${v.repo}/uk.gov.hmrc/${v.artefactName}/scala_$scalaVersion/$sbtVersion/${v.version.value}/jars/$fileName"
  }

  override def jarUploadFor(v:VersionDescriptor):String={
    val fileName = jarFilenameFor(v)
    s"$bintrayApiRoot/content/hmrc/${v.repo}/uk.gov.hmrc/${v.artefactName}/scala_$scalaVersion/$sbtVersion/${v.version.value}/jars/$fileName"
  }

  def filePrefixFor(v: VersionDescriptor) = s"${v.artefactName}_$scalaVersion-${v.version.value}"

  def fileDownloadFor(v: VersionDescriptor, fileName:String): String = {
    val dir = findParentDirectoryFor(v, fileName)
    s"$bintrayRepoRoot/${v.repo}/uk.gov.hmrc/${v.artefactName}/scala_$scalaVersion/$sbtVersion/${v.version.value}/$dir/$fileName"
  }

  private def findParentDirectoryFor(v: VersionDescriptor, fileName: String): String = {
    fileName.stripPrefix(v.artefactName) match {
      case "-javadoc.jar" => "docs"
      case "-sources.jar" => "srcs"
      case ".jar" => "jars"
      case "-assembly.jar" => "jars"
      case "ivy.xml" => "ivys"
      case _ => throw new scala.IllegalArgumentException(s"couldn't find directory for $fileName")
    }
  }

  def fileUploadFor(v: VersionDescriptor, fileName:String): String={
    val dir = findParentDirectoryFor(v, fileName)
    s"$bintrayApiRoot/content/hmrc/${v.repo}/uk.gov.hmrc/${v.artefactName}/scala_$scalaVersion/$sbtVersion/${v.version.value}/$dir/$fileName"
  }
}

trait BintrayMavenPaths extends PathBuilder{

  def scalaVersion:String

  override def filenameFor( v:VersionDescriptor, suffix:String):String={
    s"${v.artefactName}_$scalaVersion-${v.version.value}$suffix"
  }

  def jarFilenameFor(v:VersionDescriptor):String={
    s"${v.artefactName}_$scalaVersion-${v.version.value}.jar"
  }

  def jarDownloadFor(v:VersionDescriptor):String={
    val fileName = jarFilenameFor(v)
    s"$bintrayRepoRoot/${v.repo}/uk/gov/hmrc/${v.artefactName}_$scalaVersion/${v.version.value}/$fileName"
  }

  def jarUploadFor(v:VersionDescriptor):String={
    val fileName = jarFilenameFor(v)
    s"$bintrayApiRoot/maven/hmrc/${v.repo}/${v.artefactName}/uk/gov/hmrc/${v.artefactName}_$scalaVersion/${v.version.value}/$fileName"
  }

  def filePrefixFor(v: VersionDescriptor) = s"${v.artefactName}_$scalaVersion-${v.version.value}"

  def fileDownloadFor(v: VersionDescriptor, fileName:String)={
    s"$bintrayRepoRoot/${v.repo}/uk/gov/hmrc/${v.artefactName}_$scalaVersion/${v.version.value}/$fileName"
  }

  def fileUploadFor(v: VersionDescriptor, fileName:String): String={
    s"$bintrayApiRoot/maven/hmrc/${v.repo}/${v.artefactName}/uk/gov/hmrc/${v.artefactName}_$scalaVersion/${v.version.value}/$fileName"
  }

}
