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

import java.io.{File, FileOutputStream}
import java.util.jar._
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}

import com.google.common.io.ByteStreams

import scala.collection.JavaConversions._
import scala.util.Try

class Transformer(stagingDir:File){

  val versionNumberFields = Set("Git-Describe", "Implementation-Version", "Specification-Version")

  def manifestTransformer(manifest: Manifest, updatedVersionNumber:String): Manifest = {

    manifest.getMainAttributes.keysIterator.foldLeft(new Manifest()) { (newMan, key) =>
      if(versionNumberFields.contains(key.toString)){
        newMan.getMainAttributes.put(key, updatedVersionNumber)
      } else {
        newMan.getMainAttributes.put(key, manifest.getMainAttributes.get(key))
      }
      newMan
    }
  }

  def apply(localJarFile:File, targetVersion:String, targetJarName:String):Try[File] = Try {

    val jarFile: ZipFile = new ZipFile(localJarFile)

    val targetFile = new File(stagingDir, targetJarName)

    val zout = new ZipOutputStream(new FileOutputStream(targetFile))

    jarFile.entries().foreach { ze =>

      if(ze.getName == "META-INF/MANIFEST.MF"){
        zout.putNextEntry(new ZipEntry(ze.getName))
        val newManifest:Manifest = manifestTransformer(new Manifest(jarFile.getInputStream(ze)), targetVersion)
        newManifest.write(zout)
      } else {
        zout.putNextEntry(new ZipEntry(ze))
        ByteStreams.copy(jarFile.getInputStream(ze), zout)
      }
    }

    zout.flush()
    zout.close()
    jarFile.close()

    targetFile
  }
}
