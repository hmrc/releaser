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

import java.io._
import java.nio.file.{Files, Path}
import java.util.jar._
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}

import com.google.common.io.ByteStreams
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream, TarArchiveOutputStream}
import org.apache.commons.compress.compressors.gzip.{GzipCompressorInputStream, GzipCompressorOutputStream}
import org.apache.commons.io.{FileUtils, IOUtils}
import resource._
import uk.gov.hmrc.PosixFileAttributes

import scala.collection.JavaConversions._
import scala.util.{Failure, Try}
import scala.xml._
import scala.xml.transform.{RewriteRule, RuleTransformer}

trait Transformer {
  def apply(localFile: Path, artefactName: String, sourceVersion: ReleaseCandidateVersion, targetVersion: ReleaseVersion, targetFile: Path): Try[Path]
}

trait XmlTransformer extends Transformer {

  def apply(localPomFile: Path, artefactName: String, sourceVersion: ReleaseCandidateVersion, targetVersion: ReleaseVersion, targetFile: Path): Try[Path] = {
    val updatedT: Try[Node] = updateVersion(XML.loadFile(localPomFile.toFile), targetVersion)
    updatedT.flatMap { updated => Try {
      Files.write(targetFile, updated.mkString.getBytes)
    }
    }
  }

  def updateVersion(node: Node, newVersion: ReleaseVersion): Try[Node]

}

class PomTransformer extends XmlTransformer {

  def updateVersion(node: Node, newVersion: ReleaseVersion): Try[Node] = {
    if ((node \ "version").isEmpty) {
      Failure(new Exception("Didn't find project element in pom file"))
    } else {

      def updateVersionRec(node: Node, newVersion:ReleaseVersion): Node = node match {
        case <project>{ n @ _* }</project> => <project>{ n.map { a => updateVersionRec(a, newVersion) }} </project>
        case <version>{ _* }</version> => <version>{ newVersion.value }</version>
        case other @ _ => other
      }
      Try {
        updateVersionRec(node, newVersion)
      }
    }
  }
}


class IvyTransformer extends XmlTransformer {

  def updateVersion(node: Node, newVersion: ReleaseVersion): Try[Node] = {
    if ((node \ "info" \ "@revision").isEmpty) {
      Failure(new Exception("Didn't find revision element in ivy file"))
    } else {

      val rewrite = new RuleTransformer(new RewriteRule {
        override def transform(node: Node) = node match {
          case n: Elem if n.label == "info" =>
            n % Attribute("revision", Text(newVersion.value), n.attributes.remove("revision"))
          case other => other
        }
      })

      Try {
        rewrite(node)
      }
    }
  }
}

class JarManifestTransformer extends Transformer {

  val versionNumberFields = Set("Git-Describe", "Implementation-Version", "Specification-Version")

  def manifestTransformer(manifest: Manifest, updatedVersionNumber: ReleaseVersion): Manifest = {

    manifest.getMainAttributes.keysIterator.foldLeft(new Manifest()) { (newMan, key) =>
      if (versionNumberFields.contains(key.toString)) {
        newMan.getMainAttributes.put(key, updatedVersionNumber.value)
      } else {
        newMan.getMainAttributes.put(key, manifest.getMainAttributes.get(key))
      }
      newMan
    }
  }

  def apply(localJarFile: Path, artefactName: String, sourceVersion: ReleaseCandidateVersion, targetVersion: ReleaseVersion, target: Path): Try[Path] = Try {

    for {
      jarFile <- managed(new ZipFile(localJarFile.toFile))
      zout <- managed(new ZipOutputStream(new FileOutputStream(target.toFile)))
    } {

      jarFile.entries().foreach { ze =>

        if (ze.getName == "META-INF/MANIFEST.MF") {
          val newZipEntry = new ZipEntry(ze.getName)
          newZipEntry.setTime(ze.getTime)
          zout.putNextEntry(newZipEntry)
          val newManifest: Manifest = manifestTransformer(new Manifest(jarFile.getInputStream(ze)), targetVersion)
          newManifest.write(zout)
        } else {
          zout.putNextEntry(new ZipEntry(ze))
          ByteStreams.copy(jarFile.getInputStream(ze), zout)
        }
      }
    }

    target
  }
}

class TgzTransformer extends Transformer {

  import PosixFileAttributes._

  override def apply(localTgzFile: Path, artefactName: String, sourceVersion: ReleaseCandidateVersion, targetVersion: ReleaseVersion, targetFile: Path): Try[Path] = Try {
    val decompressedArchivePath = decompressTgz(localTgzFile)
    renameFolder(decompressedArchivePath, artefactName, sourceVersion, targetVersion)
    compressTgz(decompressedArchivePath, targetFile)
    targetFile
  }

  private def decompressTgz(localTgzFile: Path): Path = {
    val bytes = new Array[Byte](2048)
    val fin = new BufferedInputStream(new FileInputStream(localTgzFile.toFile))
    val gzIn = new GzipCompressorInputStream(fin)
    val tarIn = new TarArchiveInputStream(gzIn)

    val targetDecompressPath = localTgzFile.getParent.resolve("tmp_tgz")
    targetDecompressPath.toFile.mkdirs()
    Iterator continually tarIn.getNextTarEntry takeWhile (null !=) foreach { tarEntry =>
      val targetEntryFile = new File(targetDecompressPath.toFile, tarEntry.getName)
      if (tarEntry.isDirectory) {
        targetEntryFile.mkdirs()
      } else {
        targetEntryFile.getParentFile.mkdirs()
        val fos = new BufferedOutputStream(new FileOutputStream(targetEntryFile), 2048)
        Iterator continually tarIn.read(bytes) takeWhile (-1 !=) foreach (read => fos.write(bytes, 0, read))
        fos.close()
        Files.setPosixFilePermissions(targetEntryFile.toPath, tarEntry.getMode)
      }
    }

    tarIn.close()

    targetDecompressPath
  }


  private def renameFolder(decompressedArchivePath: Path, artefactName: String, sourceVersion: ReleaseCandidateVersion, targetVersion: ReleaseVersion): Try[Path] = Try {
    val folderToRename = decompressedArchivePath.resolve(s"$artefactName-${sourceVersion.value}")
    val targetFolder = folderToRename.resolveSibling(s"$artefactName-${targetVersion.value}")
    FileUtils.moveDirectory(folderToRename.toFile, targetFolder.toFile)
    targetFolder
  }

  private def compressTgz(expandedFolder: Path, targetFile: Path): Try[Path] = Try {
    import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_GNU

    val outputStream = new TarArchiveOutputStream(new GzipCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(targetFile.toFile))))
    outputStream.setLongFileMode(LONGFILE_GNU)

    val mainEntry = new TarArchiveEntry(expandedFolder.toFile, ".")

    addFolderToTarGz(outputStream, mainEntry)

    outputStream.finish()
    outputStream.close()

    targetFile
  }

  private def addFolderToTarGz(tOut: TarArchiveOutputStream, tarEntry: TarArchiveEntry): Try[Unit] = Try {
    val f = tarEntry.getFile
    tOut.putArchiveEntry(tarEntry)
    tOut.closeArchiveEntry()

    val children = f.listFiles()
    if (children != null) {
      for (child <- children) {
        addEntryToTarGz(tOut, new TarArchiveEntry(child, tarEntry.getName + child.getName))
      }
    }

  }

  private def addEntryToTarGz(tOut: TarArchiveOutputStream, tarEntry: TarArchiveEntry): Try[Unit] = Try {
    val f = tarEntry.getFile
    if (f.isFile) {
      tarEntry.setMode(Files.getPosixFilePermissions(f.toPath))
      tOut.putArchiveEntry(tarEntry)
      IOUtils.copy(new FileInputStream(f), tOut)
      tOut.closeArchiveEntry()
    } else {
      addFolderToTarGz(tOut, tarEntry)
    }
  }

}

class NoopTransformer() extends Transformer {
  override def apply(localFile: Path, artefactName: String, sourceVersion: ReleaseCandidateVersion, targetVersion: ReleaseVersion, targetFile: Path): Try[Path] = {
    Try {
      Files.copy(localFile, targetFile)
    }
  }
}
