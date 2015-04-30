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

import org.scalatest.{Matchers, OptionValues, WordSpec}
import play.api.libs.json.{JsNumber, JsObject}
import play.api.libs.ws.{EmptyBody, WSAuthScheme}

class GithubHttpSpecs extends WordSpec with Matchers with OptionValues{

  "GithubHttpSpecs" should {
    "build request holder" in {
      val githubHttp = new GithubHttp(ServiceCredentials("Charles", "123"))

      val body = JsObject(Seq("a" -> JsNumber(1)))
      val call = githubHttp.buildCall("POST", "http://example.com", Some(body))

      call.method shouldBe "POST"
      call.body should not be EmptyBody
      call.url shouldBe "http://example.com"
      call.auth.value shouldBe ("Charles", "123", WSAuthScheme.BASIC)
    }
  }

}
