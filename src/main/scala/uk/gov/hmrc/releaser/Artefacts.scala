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

import java.nio.file.Path

import scala.collection.immutable.ListMap
import scala.collection.mutable

trait TransformerProvider{
  def transformersForSupportedFiles(filePaths: List[String]): List[(String, Option[Transformer])]
  def isTheJarFile(f:String):Boolean
  def filePrefix:String
}

object IvyArtefacts{
  def apply(map:VersionMapping, localDir:Path) = new IvyArtefacts(map, localDir)
}

class IvyArtefacts(map:VersionMapping, localDir:Path) extends TransformerProvider{

  val regexTransformers = Map(
    map.artefactName+"-sources\\.jar"  -> None,
    map.artefactName+"-javadoc\\.jar"  -> None,
    map.artefactName+"-assembly\\.jar" -> Some(new NoopTransformer),
    map.artefactName+"\\.jar"          -> Some(new JarManifestTransformer),
    "ivy\\.xml"                        -> Some(new IvyTransformer),
    s".+\\.jar$$"                      -> Some(new NoopTransformer),
    s".+\\.tgz$$"                      -> Some(new NoopTransformer))

  def isTheJarFile(f:String):Boolean={
    f == map.artefactName+".jar"
  }

  def filePrefix = ""

  def transformersForSupportedFiles(filePaths: List[String]): List[(String, Option[Transformer])] = {
    filePaths
      .filter(f => regexTransformers.get(f).isDefined)
      .map { f => f -> regexTransformers.get(f).flatten }

    filePaths
      .filter(isRelevantFile)
      .map { f => f -> findTransformer(f).flatten }
  }

  private def isRelevantFile(filePath: String): Boolean = {
    findTransformer(filePath).isDefined
  }

  private def findTransformer(filePath: String): Option[Option[Transformer]] = {
    val fileName = filePath.split("/").last
    regexTransformers
      .find(t => t._1.r.findFirstIn(fileName).isDefined)
      .map(t => t._2)
  }
}


object MavenArtefacts{
  def apply(map:VersionMapping, localDir:Path) = new MavenArtefacts(map, localDir)
}

class MavenArtefacts(map:VersionMapping, localDir:Path) extends TransformerProvider{

  val filePrefix = s"${map.artefactName}_${map.repo.scalaVersion}-${map.sourceVersion.value}"

  val regexTransformers = ListMap(
    s"$filePrefix\\.jar$$" -> Some(new JarManifestTransformer),
    s"$filePrefix\\.pom$$" -> Some(new PomTransformer),
    s"$filePrefix\\.tgz$$" -> Some(new TgzTransformer),
    s".*-javadoc\\.jar$$" -> None,
    s".*-sources\\.jar$$" -> None,
    s".+\\.jar$$" -> Some(new NoopTransformer),
    s".+\\.tgz$$" -> Some(new NoopTransformer))

  def isTheJarFile(f:String):Boolean={
    f.split("/").last == filePrefix+".jar"
  }

  def transformersForSupportedFiles(filePaths: List[String]): List[(String, Option[Transformer])] = {
    filePaths
      .filter(isRelevantFile)
      .map { f => f -> findTransformer(f).flatten }
  }

  private def isRelevantFile(filePath: String): Boolean = {
    findTransformer(filePath).isDefined
  }

  private def findTransformer(filePath: String): Option[Option[Transformer]] = {
    val fileName = filePath.split("/").last
    regexTransformers
      .find(t => t._1.r.findFirstIn(fileName).isDefined)
      .map(t => t._2)
  }
}
