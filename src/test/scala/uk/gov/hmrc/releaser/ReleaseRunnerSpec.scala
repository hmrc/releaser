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

import java.nio.file.{Files, Path}

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.JsValue
import uk.gov.hmrc.releaser.domain._

import scala.util.{Success, Try}


class ReleaseRunnerSpec extends WordSpec with Matchers {

  val githubCreds = ServiceCredentials("github", "passwordG")
  val bintrayCreds = ServiceCredentials("bintray", "passwordB")
  val tmpDir = Files.createTempDirectory("releaser")
  val versionRelease = getClass.getPackage.getImplementationVersion

  def gitHubDetailsStub() = {
    val emptyGitPoster: (String, JsValue) => Try[Unit] = (a, b) => { println("Github emptyPost DRY_RUN"); Success(Unit) }
    val emptyGitPosteAndGetter: (String, JsValue) => Try[CommitSha] = (a, b) => { println("Github emptyPost DRY_RUN"); Success("a-fake-tag-sha") }

    val releaserVersion = getClass.getPackage.getImplementationVersion
    new GithubDetails(new GithubHttp(githubCreds), releaserVersion, new GithubApi())(emptyGitPoster, emptyGitPosteAndGetter)
  }

  def bintrayDetailsStub(workDir : Path) = {

    val bintrayConnectorStub = new BintrayHttp(bintrayCreds) {
      override def emptyPost(url: String): Try[Unit] = Success(Unit)
      override def putFile(version: VersionDescriptor, file: Path, url: String): Try[Unit] = Success(Unit)
      override def get[A](url: String): Try[String] = Success(s"""{message: "url call : $url"}""")
    }

    new BintrayDetails(bintrayConnectorStub, workDir)
  }

  "Build a Releaser" should {
    "create instance with dry run connectors" in {

      val dryRunReleaser = ReleaserBuilder(versionRelease, ReleaseDirectories(), githubCreds, bintrayCreds, true)
    }
  }
}
