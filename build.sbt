import com.mesosphere.sbt.Build
import com.mesosphere.sbt.BuildPlugin

lazy val root = project.in(file("."))
  .settings(
    name := "sbt-dcos",
    version := "0.2.0-SNAPSHOT",
    organization := "com.mesosphere",
    scalaVersion := "2.10.6",
    sbtPlugin := true,

    // Reuse the data from the metabuild
    libraryDependencies ++= metabuild.BuildInfo.libraries.map(Build.buildInfoDecode),
    metabuild.BuildInfo.plugins.map(moduleId => addSbtPlugin(Build.buildInfoDecode(moduleId))),

    BuildPlugin.publishSettings,
    pomExtra :=
      <url>https://dcos.io</url>
      <licenses>
        <license>
          <name>Apache License Version 2.0</name>
          <url>https://github.com/dcos/sbt-dcos/blob/master/LICENSE</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>https://github.com/dcos/sbt-dcos.git</url>
        <connection>scm:git:https://github.com/dcos/sbt-dcos.git</connection>
      </scm>
      <developers>
        <developer>
          <name>Charles Ruhland</name>
        </developer>
        <developer>
          <name>Jesus Larios Murillo</name>
        </developer>
        <developer>
          <name>José Armando García Sancio</name>
        </developer>
      </developers>
  )
