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

import org.scalatest.{Matchers, OptionValues, TryValues, WordSpec}

import scala.util.{Failure, Success, Try}

class RepositoriesSpec extends WordSpec with Matchers with OptionValues with TryValues {

  "Repositories" should {

    val repos = Seq(
      new BintrayRepository("candidate-repo-1", "release-repo-1") with IvyRepo,
      new BintrayRepository("candidate-repo-1", "release-repo-2") with MavenRepo,
      new BintrayRepository("candidate-repo-1", "release-repo-3") with GradleRepo
    )

    val artefactName = "artefact"

    "find the release-candidate/release repository pair that contains a given artefact" in {

      val metaDataGetter:(String, String) => Try[Unit] = (reponame, _) =>  reponame match {
        case "candidate-repo-1" => Success(Unit)
        case _ => Failure(new Exception("fail"))
      }

      val respositories = new Repositories(metaDataGetter)(repos)

      respositories.findReposOfArtefact(artefactName).get.releaseCandidateRepo shouldBe "candidate-repo-1"
    }

    "return exception when the candidate repo isn't found" in {

      val metaDataGetter:(String, String) => Try[Unit] = (reponame, _) =>  reponame match {
        case _ => Failure(new Exception("fail"))
      }

      val respositories = new Repositories(metaDataGetter)(repos)

      respositories.findReposOfArtefact(artefactName).failure.exception.getMessage.length should be > 1
    }
  }
}
