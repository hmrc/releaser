package uk.gov.hmrc.releaser.bintray

import uk.gov.hmrc.releaser.MetaConnector
import uk.gov.hmrc.releaser.domain.{BintrayPaths, VersionDescriptor}

import scala.util.Try

class BintrayMetaConnector(bintrayHttp:BintrayHttp) extends MetaConnector{

  def getRepoMetaData(repoName:String, artefactName: String):Try[Unit]={
    val url = BintrayPaths.metadata(repoName, artefactName)
    bintrayHttp.get(url).map { _ => Unit}
  }

  def publish(version: VersionDescriptor):Try[Unit]={
    val url = BintrayPaths.publishUrlFor(version)
    bintrayHttp.emptyPost(url)
  }
}
