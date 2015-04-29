package uk.gov.hmrc.releaser

import org.scalatest.{Matchers, TryValues, WordSpec}

class VersionNumberCalculatorSpec extends WordSpec with Matchers with TryValues {

  import VersionNumberCalculator._
  
  "VersionNumberCalculator" should {
    "calculate 0.8.2 given a release candidate of 0.8.1-4-ge733d26 and release type of PATCH" in {
      println(calculateTarget("0.8.1-4-ge733d26", ReleaseType.PATCH))
      calculateTarget("0.8.1-4-ge733d26", ReleaseType.PATCH).success.value shouldBe "0.8.2"
    }

    "calculate 0.9.0 given a release candidate of 0.8.1-4-ge733d26 and release type of MINOR" in {
      calculateTarget("0.8.1-4-ge733d26", ReleaseType.MINOR).success.value shouldBe "0.9.0"
    }

    "calculate 1.0.0 given a release candidate of 0.8.1-4-ge733d26 and release type of MAJOR" in {
      calculateTarget("0.8.1-4-ge733d26", ReleaseType.MAJOR).success.value shouldBe "1.0.0"
    }

    "calculate 11.12.20 given a release candidate of 11.12.19-4-ge733d26 and release type of MAJOR" in {
      calculateTarget("11.12.19-4-ge733d26", ReleaseType.PATCH).success.value shouldBe "11.12.20"
    }

    "return a failure given an invalid release number of 0.0.1-SNAPSHOT and release type of PATCH" in {
      calculateTarget("0.0.1-SNAPSHOT", ReleaseType.PATCH).failure.exception.getMessage should include("SNAPSHOT")
    }
  }
}
