package uk.gov.hmrc.releaser

import scala.util.Try

object VersionNumberCalculator{

  val Version = """(\d+)\.(\d+)\.(\d+)-.*-g.*""".r

  def calculateTarget(rcVersion:String, releaseType: ReleaseType.Value): Try[String] = Try {
    groups(rcVersion).toList.map(_.toInt) match {
       case List(major, minor, patch) => releaseType match {
        case ReleaseType.PATCH => List(major, minor, patch + 1).mkString(".")
        case ReleaseType.MINOR => List(major, minor+1, 0).mkString(".")
        case ReleaseType.MAJOR => List(major+1, 0, 0).mkString(".")
      }
       case _ => throw new IllegalArgumentException("invalid release candidate version " + rcVersion)
    }
  }

  def groups(rcVersion: String): Iterator[String] = {
    for (m <- Version.findAllIn(rcVersion).matchData;
         e <- m.subgroups) yield e
  }
}
