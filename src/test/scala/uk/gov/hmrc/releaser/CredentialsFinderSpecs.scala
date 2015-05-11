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
