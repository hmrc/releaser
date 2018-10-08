/*
 * Copyright 2018 HM Revenue & Customs
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
import java.nio.file.{Files, Path}

import org.scalatest._

import scala.xml.XML

class PomTransformerSpecs extends WordSpec with Matchers with BeforeAndAfterEach with OptionValues with TryValues {

  val timePomFile = new File(this.getClass.getResource("/time/uk/gov/hmrc/time_2.11/1.3.0-1-g21312cc/time_2.11-1.3.0-1-g21312cc.pom").toURI).toPath

  var transformer: PomTransformer = _
  var tempDir:Path = _

  override def beforeEach() {
    transformer = new PomTransformer()
    tempDir = Files.createTempDirectory("tmp")
  }

  "the pom transformer" should {

    "re-write the pom with a new version 1.4.0" in {

      val outFile = transformer(
        timePomFile,
        "time",
        ReleaseCandidateVersion("1.3.0-1-g21312cc"),
        ReleaseVersion("1.4.0"),
        tempDir.resolve("time-1.4.0.pom")
      ).success.get

      val pomVersionText = (XML.loadFile(outFile.toFile) \ "version").text
      pomVersionText shouldBe "1.4.0"

    }
  }
}
