package uk.gov.hmrc.releaser

import org.scalatest.{Matchers, OptionValues, TryValues, WordSpec}
import uk.gov.hmrc.releaser.Builders._

class CredentialsFinderSpecs extends WordSpec with Matchers with TryValues with OptionValues {

  "CredentialsFinder" should {
    "find a github token in the given file" in {

      val creds = CredentialsFinder.findGithubCredsInFile(resource("github-creds.txt")).value

      creds shouldBe ServiceCredentials("token", "thetoken")
    }
  }

}
