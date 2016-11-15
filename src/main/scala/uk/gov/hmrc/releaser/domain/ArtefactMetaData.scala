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

import java.nio.file.Path
import java.util.jar.Manifest
import java.util.zip.ZipFile

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}


case class ArtefactMetaData(sha:CommitSha, commitAuthor:String, commitDate:DateTime)


object ArtefactMetaData {

  val gitCommitDateFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  def fromFile(p:Path):Try[ArtefactMetaData] = {
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

  implicit class OptionPimp[A](opt:Option[A]){
    def toTry(e:Exception):Try[A] = opt match {
      case Some(x) => Success(x)
      case None => Failure(e)
    }
  }
}
