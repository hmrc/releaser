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

package uk.gov.hmrc.releaser.bintray

import uk.gov.hmrc.releaser.domain.VersionDescriptor

import scala.util.Try

class BintrayMetaConnector(bintrayHttp: BintrayHttp) {

  def getRepoMetaData(repoName:String, artefactName: String): Try[Unit] = {
    val url = BintrayPaths.metadata(repoName, artefactName)
    bintrayHttp.get(url).map { _ => Unit}
  }

  def publish(version: VersionDescriptor): Try[Unit] = {
    val url = BintrayPaths.publishUrlFor(version)
    bintrayHttp.emptyPost(url)
  }
}
