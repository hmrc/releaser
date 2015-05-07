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

import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, OptionValues, WordSpec}

import scala.util.{Failure, Success, Try}

class RepositoriesSpec extends WordSpec with Matchers with OptionValues with MockitoSugar {

  "Repositories" should {
    "find the release-candidate/release repository pair that contains a given artefact" in {

      val artefactName = "artefact"

      val metaDataGetter:(String, String) => Try[Unit] = (reponame, _) =>  reponame match {
        case "candidate-repo-1" => Success(Unit)
        case _ => Failure(new Exception("fail"))
      }

      val repos = Seq(
        new BintrayRepository("candidate-repo-1", "release-repo-1") with IvyRepo,
        new BintrayRepository("candidate-repo-1", "release-repo-2") with MavenRepo
      )

      val respositories = new Repositories(metaDataGetter)(repos)

      respositories.findReposOfArtefact(artefactName).get.releaseCandidateRepo shouldBe "candidate-repo-1"
    }
  }
}
