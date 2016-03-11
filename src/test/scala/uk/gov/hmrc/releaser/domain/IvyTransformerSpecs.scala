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

import java.io.File
import java.nio.file.Files

import org.scalatest._

import scala.xml.XML

class IvyTransformerSpecs extends WordSpec with Matchers with BeforeAndAfterEach with OptionValues with TryValues {

  val bobbyIvyFile = new File(this.getClass.getResource("/sbt-bobby/ivy.xml").toURI).toPath

  var transformer: IvyTransformer = _

  override def beforeEach() {
    transformer = new IvyTransformer
  }
  
  "the ivy transformer" should {

    "re-write the ivy with a new version 1.4.0" in {
      val dir = Files.createTempDirectory("test-release")
      val outFile = transformer(
        bobbyIvyFile,
        "artefact",
        ReleaseCandidateVersion("1.3.0-1-234235"),
        ReleaseVersion("1.4.0"),
        dir.resolve("ivy.xml")
      ).success.get

      val ivyVersionText = (XML.loadFile(outFile.toFile) \ "info" \ "@revision").text

      ivyVersionText shouldBe "1.4.0"
    }
  }
  
}
