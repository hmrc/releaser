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

import uk.gov.hmrc.releaser.ReleaseType

import scala.util.Try

trait Version {
  def value:String

  override def toString = value
}
case class ReleaseVersion(major: Int, minor: Int, revision: Int) extends Version{
  val value = s"$major.$minor.$revision"
}

object ReleaseVersion{
  def apply(st:String):ReleaseVersion = {
    val parts = st.split('.').map(_.toInt)
    ReleaseVersion(parts.head, parts(1), parts(2))
  }
}
case class ReleaseCandidateVersion(value:String) extends Version

case class VersionDescriptor(
                              repo:String,
                              artefactName:String,
                              gitHubName:Repo,
                              version:Version)

case class VersionMapping (
                            repo:RepoFlavour,
                            artefactName:String,
                            gitRepo:Repo,
                            sourceVersion:ReleaseCandidateVersion,
                            targetVersion:ReleaseVersion
                            ) {

  def targetArtefact = VersionDescriptor(repo.releaseRepo, artefactName, gitRepo, targetVersion)
  def sourceArtefact = VersionDescriptor(repo.releaseCandidateRepo, artefactName, gitRepo, sourceVersion)
}

object VersionNumberCalculator{

  val VersionRegex = """(\d+)\.(\d+)\.(\d+)-.*-g.*""".r

  def calculateTarget(rcVersion:ReleaseCandidateVersion, releaseType: ReleaseType.Value): Try[ReleaseVersion] = Try {
    groups(rcVersion.value).toList.map(_.toInt) match {
      case List(major, minor, patch) => releaseType match {
        case ReleaseType.PATCH => ReleaseVersion(major, minor, patch + 1)
        case ReleaseType.MINOR => ReleaseVersion(major, minor+1, 0)
        case ReleaseType.MAJOR => ReleaseVersion(major+1, 0, 0)
      }
      case _ => throw new IllegalArgumentException("invalid release candidate version " + rcVersion)
    }
  }

  def groups(rcVersion: String): Iterator[String] = {
    for (m <- VersionRegex.findAllIn(rcVersion).matchData;
         e <- m.subgroups) yield e
  }
}
