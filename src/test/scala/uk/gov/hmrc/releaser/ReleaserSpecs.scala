package uk.gov.hmrc.releaser

import org.joda.time.DateTime
import org.scalatest.{OptionValues, TryValues, Matchers, WordSpec}
import uk.gov.hmrc.releaser.domain.{RepoFlavours, VersionMapping, ArtefactMetaData}

import scala.collection.mutable.ListBuffer
import scala.util.Success

class ReleaserSpecs extends WordSpec with Matchers with TryValues with OptionValues {

  "createGitHubTagAndRelease" should {
    "create a function that calls github api in the correct order to create an annotated tag and release: create-tag-object -> create-tag-ref -> create-release" in {

      val artefactMetaData = ArtefactMetaData("sha", "time", DateTime.now())
      val ver = VersionMapping(RepoFlavours.mavenRepository, "a", "1", "2")

      val executedCalls = ListBuffer[String]()

      Releaser.createGitHubTagAndRelease(
        (a, b, c) => { executedCalls += "add-object"; Success("sha") },
        (a, b, c) => { executedCalls += "add-ref"; Success() },
        (a, b) => { executedCalls += "release"; Success() })(artefactMetaData, ver)

      executedCalls.toList shouldBe List("add-object", "add-ref", "release")
    }
  }
}
