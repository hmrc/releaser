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

import java.nio.file.Path
import java.util.jar
import java.util.jar.Attributes
import java.util.zip.ZipFile

import org.scalatest.{Matchers, OptionValues, WordSpec}
import uk.gov.hmrc.releaser.RepoConnector.RepoConnectorBuilder
import uk.gov.hmrc.releaser.domain.RepoFlavours._
import uk.gov.hmrc.releaser.domain._

import scala.util.{Failure, Try}
import scala.xml.XML


class ReleaseRunnerSpec extends WordSpec with Matchers with OptionValues{

  import Builders._
  
  "ReleaseBuilder" should {
    "create instance of Release" in {

      val fakeRepoConnector = Builders.buildConnector(
        filesuffix = "",
        "/lib/lib_2.11-1.3.0-1-g21312cc.jar",
        Set("/lib/lib_2.11-1.3.0-1-g21312cc.pom", "/lib/lib_2.11-1.3.0-1-g21312cc-assembly.jar")
      )
      val fakeRepoConnectorBuilder: RepoConnectorBuilder = (r) => fakeRepoConnector

      val directories = ReleaseDirectories()
      val coordinator = Builders.buildDefaultCoordinator(directories.tmpDirectory())
      val repository = new Repositories(successfulGithubMetaDataGetter)(Seq(mavenRepository, ivyRepository))

      val releaser = ReleaserBuilder(coordinator, repository, fakeRepoConnectorBuilder, directories.stageDir)

      releaser.start("lib", Repo("lib"), ReleaseCandidateVersion("1.3.0-1-g21312cc"), ReleaseVersion("0.9.9")) match {
        case Failure(e) => fail(e)
        case _ =>
      }

      fakeRepoConnector.uploadedFiles.size shouldBe 3

      val Some((assemblyVersion, assemblyFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("-assembly.jar"))
      val Some((pomVersion, pomFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".pom"))
      val Some((jarVersion, jarFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("9.jar"))

      val publishedDescriptor = fakeRepoConnector.lastPublishDescriptor

      publishedDescriptor should not be None

      jarVersion.version.value shouldBe "0.9.9"

      val jarManifest = manifestFromZipFile(jarFile)

      jarManifest.value.getValue("Implementation-Version") shouldBe "0.9.9"

      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
      pomVersionText shouldBe "0.9.9"

    }
  }


  def manifestFromZipFile(file: Path): Option[Attributes] = {
    import scala.collection.JavaConversions._

    val zipFile: ZipFile = new ZipFile(file.toFile)

    zipFile.entries().toList.find { ze =>
      ze.getName == "META-INF/MANIFEST.MF"
    }.flatMap { ze =>
      Try(new jar.Manifest(zipFile.getInputStream(ze))).map { man =>
        man.getMainAttributes
      }.toOption
    }
  }
}
