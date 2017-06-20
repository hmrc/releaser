/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.releaser.bintray

object BintrayPaths {
  val bintrayApiRoot = "https://bintray.com/api/v1"

  def metadata(repo:String, artefactName: String): String = {
    s"$bintrayApiRoot/packages/hmrc/$repo/$artefactName"
  }

  def publishUrlFor(v: VersionDescriptor): String = {
    s"${BintrayPaths.bintrayApiRoot}/content/hmrc/${v.repo}/${v.artefactName}/${v.version}/publish"
  }

  def fileListUrlFor(v: VersionDescriptor): String={
    s"${BintrayPaths.bintrayApiRoot}/packages/hmrc/${v.repo}/${v.artefactName}/versions/${v.version}/files"
  }
}

trait BintrayPaths  {
  val `package` = System.getProperty("bintray.package", "uk.gov.hmrc")
  val path = System.getProperty("bintray.path", "uk/gov/hmrc")
  val bintrayRepoRoot = s"https://bintray.com/artifact/download/hmrc"
  val packageExistsRoot = s"https://bintray.com/hmrc/"

  def releaseExistsFor(v: VersionDescriptor): String
  def jarFilenameEndingFor(v: VersionDescriptor): String

  def fileDownloadFor(v: VersionDescriptor, fileName:String): String
  def fileUploadFor(v: VersionDescriptor, fileName:String): String
}

trait BintrayIvyPaths extends BintrayPaths {
  override def releaseExistsFor(v:VersionDescriptor) : String = {
    s"$packageExistsRoot/${v.repo}/${`package`}/${v.version}"
  }

  override def jarFilenameEndingFor(v:VersionDescriptor):String={
    s"${v.artefactName}.jar"
  }

  def fileDownloadFor(v: VersionDescriptor, fileName:String): String = {
    s"$bintrayRepoRoot/${v.repo}/$fileName"
  }

  def fileUploadFor(v: VersionDescriptor, fileName:String): String={
    s"${BintrayPaths.bintrayApiRoot}/content/hmrc/${v.repo}/$fileName"
  }
}

trait BintrayMavenPaths extends BintrayPaths {

  def releaseExistsFor(v:VersionDescriptor) : String = {
    s"$packageExistsRoot/${v.repo}/$path/${v.version}"
  }

  def jarFilenameEndingFor(v:VersionDescriptor) : String = {
    s"-${v.version}.jar"
  }

  def fileDownloadFor(v: VersionDescriptor, fileName:String)={
    s"$bintrayRepoRoot/${v.repo}/$fileName"
  }

  def fileUploadFor(v: VersionDescriptor, fileName:String): String={
    s"${BintrayPaths.bintrayApiRoot}/maven/hmrc/${v.repo}/${v.artefactName}/$fileName"
  }

}
