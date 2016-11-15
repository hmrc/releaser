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

import org.joda.time.DateTime
import org.scalatest.{Matchers, OptionValues, TryValues, WordSpec}
import uk.gov.hmrc.releaser.domain.ArtefactMetaData

import scala.collection.mutable.ListBuffer
import scala.util.Success

class GitTagAndReleaseSpecs extends WordSpec with Matchers with TryValues with OptionValues with GitTagAndRelease {

  import Builders._

  "createGitHubTagAndRelease" should {
    "create a function that calls github api in the correct order to create an annotated tag and release: create-tag-object -> create-tag-ref -> create-release" in {

      val ver = mavenVersionMapping()
      val executedCalls = ListBuffer[String]()

      createGitHubTagAndRelease(
        (a, b, c) => { executedCalls += "add-object"; Success("sha") },
        (a, b, c) => { executedCalls += "add-ref"; Success() },
        (a, b, c, d) => { executedCalls += "release"; Success() })("sha", "time", DateTime.now(), ver)

      executedCalls.toList shouldBe List("add-object", "add-ref", "release")
    }
  }
}
