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

import uk.gov.hmrc.releaser.ArtifactType.ArtifactType

import scala.util.Try

object ArtifactType extends Enumeration {
  type ArtifactType = Value
  val JAR, POM, SOURCE_JAR, DOC_JAR, TGZ = Value
}

case class ArtifactClassifier(artifactType: ArtifactType, extension: String, suffix: Option[String], mandatory: Boolean, isMainArtifact: Boolean = false)

object Artifacts {

  import ArtifactType._

  def buildSupportedArtifacts(): Seq[ArtifactClassifier] = Seq(
    ArtifactClassifier(artifactType = JAR,  extension = "jar", suffix = None, mandatory = true, isMainArtifact = true),
    ArtifactClassifier(artifactType = POM, extension = "pom", suffix = None, mandatory = true),
    ArtifactClassifier(artifactType = DOC_JAR, extension = "jar", suffix = Some("-javadoc"), mandatory = false),
    ArtifactClassifier(artifactType = SOURCE_JAR, extension = "jar", suffix = Some("-sources"), mandatory = false),
    ArtifactClassifier(artifactType = TGZ, extension = "tgz", suffix = None, mandatory = false)
  )

  def findPomArtifact(artifacts: Seq[ArtifactClassifier]) : Try[ArtifactClassifier] = Try {
    artifacts.find(_.artifactType == POM).getOrElse(throw new Exception("POM artifact is not defined"))
  }

  def findMainArtifact(artifacts: Seq[ArtifactClassifier]) : Try[ArtifactClassifier] = Try {
    artifacts.find(_.isMainArtifact).getOrElse(throw new Exception("The main artifact is not defined"))
  }

}
