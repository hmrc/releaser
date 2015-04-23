# Releaser

Takes a published release candidate and produces a release.

The release candidate is a published artefact in a Bintray HMRC release candidate repository. Releaser works by taking a release candidate, modifying the verion numbers in the Manifest and file names and uploading files back to Bintray. *All compiled files are not touched in this process*.  

There are release and release candidate repositories in Bintray HMRC for standard (maven) projects and sbt-plugins which are in the Ivy style.


### Building
`sbt assembly` will place releaser-assembly-x.x.x.jar in your target/scala-2.11/ directory

### Running
`java -jar target/scala-2.11/releaser-assembly-x.x.x.jar` artifact release-candidate-version release-version
