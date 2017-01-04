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

package uk.gov.hmrc.releaser

object ArgParser{

  case class Config(
                     artefactName: String = "",
                     rcVersion:String = "",
                     releaseType:ReleaseType.Value = ReleaseType.HOTFIX,
                     githubNameOverride:Option[String] = None,
                     tag:Boolean = true,
                     verbose:Boolean = false,
                     dryRun:Boolean = false)

  val currentVersion = getClass.getPackage.getImplementationVersion

  val parser = new scopt.OptionParser[Config]("releaser") {
    override def showUsageOnError = false
    head(s"\nHMRC Releaser", s"$currentVersion\n")
    help("help") text "prints this usage text"
    arg[String]("artefactName") action { (x, c) =>
      c.copy(artefactName = x) } text "the artefact"
    arg[String]("release-candidate") action { (x, c) =>
      c.copy(rcVersion = x) } text "the release candidate"
    arg[String]("release-type") action { (x, c) =>
      c.copy(releaseType = ReleaseType.withName(x)) } validate { x =>
       if (ReleaseType.stringValues.contains(x)) success else failure(releaseTypeErrorMessage(x))
    } text "the release type. Permitted values are: " + ReleaseType.stringValues.mkString(" ")
    opt[Boolean]("tag") action { (x, c) =>
      c.copy(tag = x) } text "tag in github"
    opt[String]("github-name-override") action { (x, c) =>
      c.copy(githubNameOverride = Option(x)) } text "provide a different github repository to the bintray package"
    opt[Boolean]('v', "verbose") action { (x, c) =>
      c.copy(verbose = x) } text "verbose mode (not implemented)"
    opt[Unit]('d', "dryRun") action { (_, c) =>
      c.copy(dryRun = true) } text "dry run"
  }

  def releaseTypeErrorMessage(givenReleaseType:String):String={
    s"release-type must be one of: ${ReleaseType.stringValues.mkString(" ")}. Supplied release-type was '$givenReleaseType'"
  }
}
