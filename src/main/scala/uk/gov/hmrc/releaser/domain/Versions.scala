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

object VersionNumberCalculator{

  val Version = """(\d+)\.(\d+)\.(\d+)-.*-g.*""".r

  def calculateTarget(rcVersion:String, releaseType: ReleaseType.Value): Try[String] = Try {
    groups(rcVersion).toList.map(_.toInt) match {
      case List(major, minor, patch) => releaseType match {
        case ReleaseType.PATCH => List(major, minor, patch + 1).mkString(".")
        case ReleaseType.MINOR => List(major, minor+1, 0).mkString(".")
        case ReleaseType.MAJOR => List(major+1, 0, 0).mkString(".")
      }
      case _ => throw new IllegalArgumentException("invalid release candidate version " + rcVersion)
    }
  }

  def groups(rcVersion: String): Iterator[String] = {
    for (m <- Version.findAllIn(rcVersion).matchData;
         e <- m.subgroups) yield e
  }
}
