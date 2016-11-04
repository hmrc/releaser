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

trait GitTagAndRelease {

  import uk.gov.hmrc.releaser.domain._

  import scala.util.Try

  def createGitHubTagAndRelease(
                                 githubTagObjectCreator: (Repo, ReleaseVersion, CommitSha) => Try[CommitSha],
                                 githubTagRefCreator: (Repo, ReleaseVersion, CommitSha) => Try[Unit],
                                 githubReleaseCreator: (ArtefactMetaData, VersionMapping) => Try[Unit])
                               (metaData: ArtefactMetaData, map: VersionMapping): Try[Unit] = {
    for (
      tagSha <- githubTagObjectCreator(map.gitRepo, map.targetVersion, metaData.sha);
      _ <- githubTagRefCreator(map.gitRepo, map.targetVersion, tagSha);
      _ <- githubReleaseCreator(metaData, map))
      yield ()
  }
}
