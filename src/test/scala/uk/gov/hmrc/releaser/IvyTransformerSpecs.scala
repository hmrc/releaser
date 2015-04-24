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

import java.io.File
import java.nio.file.Files

import org.scalatest._

import scala.io.Source
import scala.xml.XML

class IvyTransformerSpecs extends WordSpec with Matchers with BeforeAndAfterEach with OptionValues with TryValues {

  val bobbyIvyFile = new File(this.getClass.getResource("/sbt-bobby/ivy.xml").toURI).toPath

  var transformer: IvyTransformer = _

  override def beforeEach() {
    transformer = new IvyTransformer(Files.createTempDirectory("test-release"))
  }
  
  "the ivy transformer" should {

    "re-write the ivy with a new version 1.4.0" in {
      val outFile = transformer(bobbyIvyFile, "1.4.0", "ivy.xml").success.get

      val ivyVersionText = (XML.loadFile(outFile.toFile) \ "info" \ "@revision").text

      ivyVersionText shouldBe "1.4.0"
    }
  }
  
}
