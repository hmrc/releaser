/*
 * Copyright 2017 HM Revenue & Customs
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

import org.scalatest.{Matchers, TryValues, WordSpec}

class VersionNumberCalculatorSpec extends WordSpec with Matchers with TryValues {

  import VersionNumberCalculator._

  "VersionNumberCalculator" should {
    "calculate 0.8.2 given a release candidate of 0.8.1-4-ge733d26 and release type of HOTFIX" in {
      calculateTarget(ReleaseCandidateVersion("0.8.1-4-ge733d26"), ReleaseType.HOTFIX).success.value.value shouldBe "0.8.2"
    }

    "calculate 0.9.0 given a release candidate of 0.8.1-4-ge733d26 and release type of MINOR" in {
      calculateTarget(ReleaseCandidateVersion("0.8.1-4-ge733d26"), ReleaseType.MINOR).success.value.value shouldBe "0.9.0"
    }

    "calculate 1.0.0 given a release candidate of 0.8.1-4-ge733d26 and release type of MAJOR" in {
      calculateTarget(ReleaseCandidateVersion("0.8.1-4-ge733d26"), ReleaseType.MAJOR).success.value.value shouldBe "1.0.0"
    }

    "calculate 11.12.20 given a release candidate of 11.12.19-4-ge733d26 and release type of MAJOR" in {
      calculateTarget(ReleaseCandidateVersion("11.12.19-4-ge733d26"), ReleaseType.HOTFIX).success.value.value shouldBe "11.12.20"
    }

    "return a failure given an invalid release number of 0.0.1-SNAPSHOT and release type of PATCH" in {
      calculateTarget(ReleaseCandidateVersion("0.0.1-SNAPSHOT"), ReleaseType.HOTFIX).failure.exception.getMessage should include("SNAPSHOT")
    }
  }
}
