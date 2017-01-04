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

import java.nio.file.Path
import java.util.jar.Manifest
import java.util.zip.ZipFile

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import uk.gov.hmrc.releaser.github.CommitSha

import scala.collection.JavaConversions._
import scala.io.Source
import scala.util.{Failure, Success, Try}

trait MetaDataProvider {
  def fromJarFile(p: Path): Try[ArtefactMetaData]
  def fromCommitManifest(p: Path): Try[ArtefactMetaData]
}

case class ArtefactMetaData(sha:CommitSha, commitAuthor:String, commitDate:DateTime)

class ArtefactMetaDataProvider extends MetaDataProvider {
  import ArtefactMetaDataProvider._

  def fromJarFile(p: Path): Try[ArtefactMetaData] = {
    Try {new ZipFile(p.toFile) }.flatMap { jarFile =>
      jarFile.entries().filter(_.getName == "META-INF/MANIFEST.MF").toList.headOption.map { ze =>
        val man = new Manifest(jarFile.getInputStream(ze))
        ArtefactMetaData(
          man.getMainAttributes.getValue("Git-Head-Rev"),
          man.getMainAttributes.getValue("Git-Commit-Author"),
          gitCommitDateFormat.parseDateTime(man.getMainAttributes.getValue("Git-Commit-Date"))
        )
      }.toTry(new Exception(s"Failed to retrieve manifest from $p"))
    }
  }

  def fromCommitManifest(p: Path): Try[ArtefactMetaData] = {
    Try {
      val map = Source.fromFile(p.toFile)
        .getLines().toSeq
        .map(_.split("="))
        .map { case Array(key, value) => key.trim -> value.trim }.toMap

      ArtefactMetaData(map("sha"), map("author"),  gitCommitDateFormat.parseDateTime(map("date")))
    }
  }
}

object ArtefactMetaDataProvider {

  val gitCommitDateFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  implicit class OptionPimp[A](opt: Option[A]){
    def toTry(e:Exception):Try[A] = opt match {
      case Some(x) => Success(x)
      case None => Failure(e)
    }
  }
}
