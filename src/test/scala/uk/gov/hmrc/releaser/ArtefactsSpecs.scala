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

package uk.gov.hmrc.releaser

import java.nio.file.Files

import org.scalatest.{Matchers, OptionValues, WordSpec}
import uk.gov.hmrc.releaser.github.Repo

class ArtefactsSpecs extends WordSpec with Matchers with OptionValues{

  "MavenArtefacts.transformersForSupportedFiles" should {
    "generate the correct list of transformers" in {

      val ver = mavenVersionMapping("time", "time")
      val artefacts = new MavenArtefacts(ver, Files.createTempDirectory("test"))
      val files = List(
        "time/time_2.11-1.3.0-1-g21312cc.jar",
        "time/time_2.11-1.3.0-1-g21312cc.zip",
        "time/time_2.11-1.3.0-1-g21312cc.tgz",
        "time/time_2.11-1.3.0-1-g21312cc-assembly.jar",
        "time/time_2.11-1.3.0-1-g21312cc-sources.jar",
        "time/time_2.11-1.3.0-1-g21312cc.pom",
        "time/time_2.11-1.3.0-1-g21312cc.pom.md5",
        "time/time_2.11-other-1.3.0-1-g21312cc.tgz"
      )

      val result: Map[String, Option[Transformer]] = artefacts.transformersForSupportedFiles(filePaths = files).toMap

      result foreach println

      result.size shouldBe 7
      result("time/time_2.11-1.3.0-1-g21312cc.jar").isInstanceOf[Some[JarManifestTransformer]] shouldBe true
      result("time/time_2.11-1.3.0-1-g21312cc.zip").isInstanceOf[Some[NoopTransformer]] shouldBe true
      result("time/time_2.11-1.3.0-1-g21312cc.tgz").isInstanceOf[Some[TgzTransformer]] shouldBe true
      result("time/time_2.11-1.3.0-1-g21312cc-assembly.jar").isInstanceOf[Some[NoopTransformer]] shouldBe true
      result("time/time_2.11-1.3.0-1-g21312cc-sources.jar") shouldBe None
      result("time/time_2.11-1.3.0-1-g21312cc.pom").isInstanceOf[Some[PomTransformer]] shouldBe true
      result("time/time_2.11-other-1.3.0-1-g21312cc.tgz").isInstanceOf[Some[NoopTransformer]] shouldBe true

    }
  }

  "IvyArtefacts.transformersForSupportedFiles" should {
    "generate the correct list of transformers" in {

      val ver = mavenVersionMapping("sbt-bobby", "sbt-bobby")
      val artefacts = new IvyArtefacts(ver, Files.createTempDirectory("test"))
      val files = List(
        "sbt-bobby.jar",
        "sbt-bobby.zip",
        "sbt-bobby.tgz",
        "sbt-bobby-assembly.jar",
        "sbt-bobby-sources.jar",
        "ivy.xml"
      )

      val result: Map[String, Option[Transformer]] = artefacts.transformersForSupportedFiles(filePaths = files).toMap

      result.size shouldBe 6
      result("sbt-bobby.jar").isInstanceOf[Some[JarManifestTransformer]] shouldBe true
      result("sbt-bobby.zip").isInstanceOf[Some[NoopTransformer]] shouldBe true
      result("sbt-bobby.tgz").isInstanceOf[Some[NoopTransformer]] shouldBe true
      result("sbt-bobby-assembly.jar").isInstanceOf[Some[NoopTransformer]] shouldBe true
      result("sbt-bobby-sources.jar") shouldBe None
      result("ivy.xml").isInstanceOf[Some[IvyTransformer]] shouldBe true

    }
  }

  private def mavenVersionMapping(artefactName:String = "a",
                                  repoName:String = "a",
                                  rcVersion:String = "1.3.0-1-g21312cc",
                                  releaseVersion:String = "1.4.0") =
    VersionMapping(
      RepoFlavours.mavenRepository,
      artefactName,
      Repo(repoName),
      ReleaseCandidateVersion(rcVersion),
      ReleaseVersion(releaseVersion))
}
