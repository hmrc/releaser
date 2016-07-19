# Releaser

[![Build Status](https://travis-ci.org/hmrc/releaser.svg?branch=master)](https://travis-ci.org/hmrc/releaser) [ ![Download](https://api.bintray.com/packages/hmrc/releases/releaser/images/download.svg) ](https://bintray.com/hmrc/releases/releaser/_latestVersion)

Releases artefacts from release candidates with one command. For use as part of a continuous delivery pipeline in which users can create a release from a development commit and create a tag in Github with one command. This automates numerous manual steps required to release an artefact with the existing [release](https://github.com/hmrc/release) scripts.

The release candidate is a published artefact in a Bintray HMRC release candidate repository. Releaser works by taking a release candidate, modifying the verion numbers in the Manifest and file names and uploading files back to Bintray. *All compiled files are not touched in this process*.  

There are release and release candidate repositories in Bintray HMRC for standard (maven) projects and sbt-plugins which are in the Ivy style.

### Building
`sbt assembly` will place releaser-assembly-x.x.x.jar in your target/scala-2.11/ directory

### Running
`java -jar target/scala-2.11/releaser-assembly-x.x.x.jar` artifact release-candidate-version release-version

##### Extra flags
- `-d | --dryRun`: perform a dry run of a relase. Downloads files and transforms but does not upload or create releases on github.com. Useful during development
- `--github-name-override`: provide a different github repository to the bintray package. The default is to assume the github repository has the same name as the Bintry repository and this flag allows the user to provide a differnt github.com repository name.

#### Configuration Parameters
You can specify custom timeouts to be passed to the play.ws libraries that this project uses with the following System properties (-Dproperty.name=value)

wsclient.timeout.connection - Connection timeout (seconds)
wsclient.timeout.idle - Idle timeout (seconds)
wsclient.timeout.request - Request timeout (seconds)

### Supported Artifacts
Releaser will release:
- Maven based libraries and services found in https://bintray.com/hmrc/release-candidates
- Ivy based libraries for SBT plugins found in https://bintray.com/hmrc/sbt-plugin-release-candidates

### Coding in the Open
Release is an integral part of how we release code in the open, more information can be found in the [Coding in the Open Manual](http://hmrc.github.io/coding-in-the-open-manual/)

### License
 
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
