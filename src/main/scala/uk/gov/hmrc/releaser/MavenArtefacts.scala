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

import java.nio.file.Path

import uk.gov.hmrc.releaser.domain._

trait TransformerProvider{
  def transformersForSupportedFiles(filePaths: List[String]): List[(String, Option[Transformer])]
  def isTheJarFile(f:String):Boolean
  def filePrefix:String
}

object IvyArtefacts{
  def apply(map:VersionMapping, localDir:Path) = new IvyArtefacts(map, localDir)
}

class IvyArtefacts(map:VersionMapping, localDir:Path) extends TransformerProvider{

  val parentDirTransformers = Map(
    map.artefactName+"-sources.jar" -> None,
    map.artefactName+"-javadoc.jar" -> None,
    map.artefactName+".jar"         -> Some(new JarManifestTransformer(localDir)),
    "ivy.xml"                       -> Some(new IvyTransformer(localDir))
  )

  def isTheJarFile(f:String):Boolean={
    f == map.artefactName+".jar"
  }

  def filePrefix = ""

  def transformersForSupportedFiles(filePaths: List[String]): List[(String, Option[Transformer])] = {
    filePaths
      .filter(f => parentDirTransformers.get(f).isDefined)
      .map { f => f -> parentDirTransformers.get(f).flatten }
  }
}


object MavenArtefacts{
  def apply(map:VersionMapping, localDir:Path) = new MavenArtefacts(map, localDir)
}

class MavenArtefacts(map:VersionMapping, localDir:Path) extends TransformerProvider{

  val prefixTransformers = Map(
    "-javadoc.jar" -> None,
    "-sources.jar" -> None,
    "-assembly.jar" -> None,
    ".jar" -> Some(new JarManifestTransformer(localDir)),
    ".pom" -> Some(new PomTransformer(localDir)),
    ".tgz" -> None
  )

  def filePrefix = s"${map.artefactName}_${map.repo.scalaVersion}-${map.sourceVersion.value}"

  def isTheJarFile(f:String):Boolean={
    f.split("/").last == filePrefix+".jar"
  }

  def transformersForSupportedFiles(filePaths: List[String]): List[(String, Option[Transformer])] = {
    filePaths
      .filter(isRelevantFile(filePrefix))
      .map { f => f -> findTransformer(filePrefix, f).flatten }
  }

  private def isRelevantFile(filePrefix: String)(filePath: String): Boolean = {
    findTransformer(filePrefix, filePath).isDefined
  }

  private def findTransformer(filePrefix: String, filePath: String): Option[Option[Transformer]] = {
    val fileName = filePath.split("/").last
    prefixTransformers.get(fileName.stripPrefix(filePrefix))
  }
}
