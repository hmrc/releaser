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

####Naming conventions
Artefacts supplied to the releaser need to follow the following naming convention:

`<artefact-name>_<scala-version>-<version>.<extension>`
E.g: catalogue-frontend_2.11-4.77.0-5-g8d3e2c5.jar

####Supported File Types

The releaser will handle the following file types found in release candidates:
* .pom (mandatory)
* .jar
* .tgz

The following files will be specifically ignored if present:
* *-sources.jar
* *-javadoc.jar

The releaser will modify .jar and .tgz files in order to update instances of version numbers contained within. All other files will be ignored and will not be copied to the released artefact.

####Additional files/non-standard artefacts

The releaser also supports files that do not match the naming conventions above. The following additional filenames are supported:

* .jar
* .tgz
* .zip

Any JAR files present will be modified to update instances of version numbers contained within. Any other files will be renamed if the filenames contain a release candidate version string, but otherwise no transformations will be applied.

If the artefact does NOT contain a JAR matching the naming conventions, you must supply a commit.mf file instead. This file tells the releaser which commit to tag when making the release. An example is as follows:

    sha=c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c
    author=charleskubicek
    date=2011-06-17T14:53:35Z

All artefacts must contain a .POM file, this is mandatory and cannot be omitted.

### Coding in the Open
Release is an integral part of how we release code in the open, more information can be found in the [Coding in the Open Manual](http://hmrc.github.io/coding-in-the-open-manual/)

### License
 
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
