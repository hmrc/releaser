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

import org.scalatest.mock.MockitoSugar
import org.scalatest.{OptionValues, Matchers, WordSpec}
import org.mockito.Mockito._

import scala.util.{Success, Failure}

class RepositoriesSpec extends WordSpec with Matchers with OptionValues with MockitoSugar{

  "Repositories" should {
    "find the release-candidate/release repository pair that contains a given artefact" in {

      val artefactName = "artefact"

      val metaConnector = mock[BintrayMetaConnector]

      when(metaConnector.getRepoMetaData("rc",  artefactName)).thenReturn(Failure(new Exception("fail")))
      when(metaConnector.getRepoMetaData("rc1", artefactName)).thenReturn(Success())

      val respositories = new Repositories{
        override def connector: BintrayMetaConnector = metaConnector

        override def repos: Seq[RepoFlavour] = Seq(
          new BintrayRepository("rc", "r") with IvyRepo,
          new BintrayRepository("rc1", "r1") with MavenRepo)
      }

      respositories.findReposOfArtefact(artefactName).get.releaseCandidateRepo shouldBe "rc1"

    }
  }
}
