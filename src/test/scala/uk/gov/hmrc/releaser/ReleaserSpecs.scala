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

import org.joda.time.DateTime
import org.scalatest.{OptionValues, TryValues, Matchers, WordSpec}
import uk.gov.hmrc.releaser.domain.{Repo, RepoFlavours, VersionMapping, ArtefactMetaData}

import scala.collection.mutable.ListBuffer
import scala.util.Success

class ReleaserSpecs extends WordSpec with Matchers with TryValues with OptionValues {

  import Builders._

  "createGitHubTagAndRelease" should {
    "create a function that calls github api in the correct order to create an annotated tag and release: create-tag-object -> create-tag-ref -> create-release" in {

      val artefactMetaData = ArtefactMetaData("sha", "time", DateTime.now())
      val ver = mavenVersionMapping()

      val executedCalls = ListBuffer[String]()

      Releaser.createGitHubTagAndRelease(
        (a, b, c) => { executedCalls += "add-object"; Success("sha") },
        (a, b, c) => { executedCalls += "add-ref"; Success() },
        (a, b) => { executedCalls += "release"; Success() })(artefactMetaData, ver)

      executedCalls.toList shouldBe List("add-object", "add-ref", "release")
    }
  }
}
