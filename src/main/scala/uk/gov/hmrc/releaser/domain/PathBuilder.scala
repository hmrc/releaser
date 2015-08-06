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

package uk.gov.hmrc.releaser.domain


trait BintrayPaths {

  val bintrayRepoRoot = "https://bintray.com/artifact/download/hmrc"
  val bintrayApiRoot = "https://bintray.com/api/v1"

  def metadata(repo: String, artefactName: String): String = {
    s"$bintrayApiRoot/packages/hmrc/$repo/$artefactName"
  }

  def publishUrlFor(v: VersionDescriptor): String = {
    s"$bintrayApiRoot/content/hmrc/${v.repo}/${v.artefactName}/${v.version.value}/publish"
  }
}

object BintrayPaths extends BintrayPaths

trait PathBuilder extends BintrayPaths {

  def artifactFilenameFor(v: VersionDescriptor): String

  def artifactDownloadFor(v: VersionDescriptor): String

  def artifactUploadFor(v: VersionDescriptor): String

}

trait BintrayIvyPaths extends PathBuilder {

  import uk.gov.hmrc.releaser.ArtifactType._

  val sbtVersion = "sbt_0.13"

  def scalaVersion: String

  override def artifactFilenameFor(v: VersionDescriptor): String = {
    v.classifier.artifactType match {
      case POM => "ivy.xml"
      case _ => s"${v.artefactName}${v.classifier.suffix.getOrElse("")}.${v.classifier.extension}"
    }
  }

  override def artifactDownloadFor(v: VersionDescriptor): String = {
    val fileName = artifactFilenameFor(v)
    val folder = getIvyFolder(v)
    s"$bintrayRepoRoot/${v.repo}/uk.gov.hmrc/${v.artefactName}/scala_$scalaVersion/$sbtVersion/${v.version.value}/$folder/$fileName"
  }

  override def artifactUploadFor(v: VersionDescriptor): String = {
    val fileName = artifactFilenameFor(v)
    val folder = getIvyFolder(v)
    s"$bintrayApiRoot/content/hmrc/${v.repo}/uk.gov.hmrc/${v.artefactName}/scala_$scalaVersion/$sbtVersion/${v.version.value}/$folder/$fileName"
  }

  private def getIvyFolder(v: VersionDescriptor) = {
    v.classifier.artifactType match {
      case JAR => "jars"
      case POM => "ivys"
      case SOURCE_JAR => "srcs"
      case DOC_JAR => "docs"
      case TGZ => "tgzs"
      case _ => throw new Exception(s"Unsupported artifact type")
    }
  }
}

trait BintrayMavenPaths extends PathBuilder {

  def scalaVersion: String

  override def artifactFilenameFor(v: VersionDescriptor): String = {
    s"${v.artefactName}_$scalaVersion-${v.version.value}${v.classifier.suffix.getOrElse("")}.${v.classifier.extension}"
  }

  override def artifactDownloadFor(v: VersionDescriptor): String = {
    val fileName = artifactFilenameFor(v)
    s"$bintrayRepoRoot/${v.repo}/uk/gov/hmrc/${v.artefactName}_$scalaVersion/${v.version.value}/$fileName"
  }

  override def artifactUploadFor(v: VersionDescriptor): String = {
    val fileName = artifactFilenameFor(v)
    s"$bintrayApiRoot/maven/hmrc/${v.repo}/${v.artefactName}/uk/gov/hmrc/${v.artefactName}_$scalaVersion/${v.version.value}/$fileName"
  }

}
