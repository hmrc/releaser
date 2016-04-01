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

package uk.gov.hmrc.releaser.domain

import java.nio.file.{Files, Path}

import uk.gov.hmrc.releaser.{MavenArtefacts, TransformerProvider, IvyArtefacts}

import scala.util.{Failure, Success, Try}

trait RepoFlavour extends PathBuilder{
  val workDir:Path = Files.createTempDirectory("releaser")

  def scalaVersion:String
  def releaseCandidateRepo:String
  def releaseRepo:String
  //def pomTransformer:XmlTransformer
  val artefactBuilder:(VersionMapping, Path) => TransformerProvider
}

trait IvyRepo extends RepoFlavour with BintrayIvyPaths{
  val scalaVersion = "2.10"
  val artefactBuilder = IvyArtefacts.apply _
}

trait MavenRepo extends RepoFlavour with BintrayMavenPaths{
  val scalaVersion = "2.11"
  val artefactBuilder = MavenArtefacts.apply _
}

object RepoFlavours {
  val mavenRepository: RepoFlavour = new BintrayRepository("release-candidates", "releases") with MavenRepo
  val ivyRepository: RepoFlavour = new BintrayRepository("sbt-plugin-release-candidates", "sbt-plugin-releases") with IvyRepo
}

case class BintrayRepository(releaseCandidateRepo:String, releaseRepo:String)

class Repositories(metaDataGetter:(String, String) => Try[Unit])(repos:Seq[RepoFlavour]){

  def findReposOfArtefact(artefactName: ArtefactName): Try[RepoFlavour] = {
    repos.find { repo =>
      metaDataGetter(repo.releaseCandidateRepo, artefactName).isSuccess
    } match {
      case Some(r) => Success(r)
      case None => Failure(new Exception(s"Didn't find a release candidate repository for '$artefactName' in repos ${repos.map(_.releaseCandidateRepo)}"))
    }
  }
}
