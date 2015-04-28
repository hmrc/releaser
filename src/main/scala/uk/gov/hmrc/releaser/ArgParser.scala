package uk.gov.hmrc.releaser

object ArgParser{

  case class Config(
                     artefactName: String = "",
                     rcVersion:String = "",
                     targetVersion:String = "",
                     tag:Boolean = true,
                     verbose:Boolean = false,
                     dryRun:Boolean = false)


  val parser = new scopt.OptionParser[Config]("releaser") {
    head("HMRC Releaser", "1.0-alpha")
    arg[String]("artefactName") action { (x, c) =>
      c.copy(artefactName = x) } text "the artefact"
    arg[String]("release-candidate") action { (x, c) =>
      c.copy(rcVersion = x) } text "the release candidate"
    arg[String]("target-version") action { (x, c) =>
      c.copy(targetVersion = x) } text "the target version"
    opt[Boolean]("tag") action { (x, c) =>
      c.copy(tag = x) } text "tag in github"
    opt[Boolean]('v', "verbose") action { (x, c) =>
      c.copy(verbose = x) } text "verbose mode (not implemented)"
    opt[Boolean]('d', "dryRun") action { (x, c) =>
      c.copy(dryRun = x) } text "dry run"
  }

}
