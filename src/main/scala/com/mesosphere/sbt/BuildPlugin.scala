package com.mesosphere.sbt

import com.github.retronym.SbtOneJar._
import com.mesosphere.cosmos.CosmosIntegrationTestServer
import sbt._
import sbt.Keys._
import scala.Ordering.Implicits._
import scoverage.ScoverageKeys._

object BuildPlugin extends AutoPlugin {

  private val teamcityVersion: Option[String] = sys.env.get("TEAMCITY_VERSION")

  private val twoEleven: List[Int] = parseScalaVersion("2.11")
  private val firstScalaVersionSupportingJvm18: List[Int] = parseScalaVersion("2.11.5")

  private val warnUnusedImport: String = "-Ywarn-unused-import"

  private val parsedScalaVersion: SettingKey[List[Int]] =
    settingKey("The project's Scala version, parsed into a list of version numbers")

  private val supportedJvmVersion: SettingKey[String] =
    settingKey("The JVM version required by this project")

  override def trigger: PluginTrigger = allRequirements

  override def projectConfigurations: Seq[Configuration] = Seq(IntegrationTest extend Test)

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    coverageOutputTeamCity := teamcityVersion.isDefined,
    cancelable in Global := true,

    initialize in LocalRootProject := {
      val ignore = initialize.value
      teamcityVersion.foreach { _ =>
        // add some info into the teamcity build context so that they can be used by later steps
        reportTeamCityParameter("SCALA_VERSION", scalaVersion.value)
        reportTeamCityParameter("PROJECT_VERSION", version.value)
      }
    }
  ) ++ compilationSettings ++ Defaults.itSettings ++ Scalastyle.settings

  private val compilationSettings: Seq[Def.Setting[_]] = Seq(
    parsedScalaVersion := parseScalaVersion(scalaVersion.value),
    supportedJvmVersion := {
      if (parsedScalaVersion.value < firstScalaVersionSupportingJvm18) "1.7" else "1.8"
    },

    javacOptions in Compile ++= Seq(
      "-source", supportedJvmVersion.value,
      "-target", supportedJvmVersion.value,
      "-Xlint:unchecked",
      "-Xlint:deprecation"
    ),

    scalacOptions ++= {
      val targetJvm = s"-target:jvm-${supportedJvmVersion.value}"

      // scalastyle:off line.size.limit
      val commonOptions = Seq(
        "-deprecation",            // Emit warning and location for usages of deprecated APIs.
        "-encoding", "UTF-8",      // Specify character encoding used by source files.
        "-explaintypes",           // Explain type errors in more detail.
        "-feature",                // Emit warning for usages of features that should be imported explicitly.
        targetJvm,                 // Target platform for object files.
        "-unchecked",              // Enable additional warnings where generated code depends on assumptions.
        "-Xfatal-warnings",        // Fail the compilation if there are any warnings.
        "-Xfuture",                // Turn on future language features.
        "-Xlint",                  // Enable or disable specific warnings
        "-Ywarn-adapted-args",     // Warn if an argument list is modified to match the receiver.
        "-Ywarn-dead-code",        // Warn when dead code is identified.
        "-Ywarn-inaccessible",     // Warn about inaccessible types in method signatures.
        "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
        "-Ywarn-nullary-unit",     // Warn when nullary methods return Unit.
        "-Ywarn-numeric-widen",    // Warn when numerics are widened.
        "-Ywarn-value-discard"     // Warn when non-Unit expression results are unused.
      )

      val twoElevenOptions = Seq(
        "-Ywarn-infer-any",        // Warn when a type argument is inferred to be `Any`.
        "-Ywarn-unused",           // Warn when local and private vals, vars, defs, and types are unused.
        warnUnusedImport           // Warn when imports are unused.
      )
      // scalastyle:on line.size.limit

      commonOptions ++ (if (parsedScalaVersion.value < twoEleven) Seq.empty else twoElevenOptions)
    }
  ) ++ Seq(Compile, Test, IntegrationTest).flatMap { config =>
    Seq(
      scalacOptions in (config, console) ~= (_.filterNot(_ == warnUnusedImport)),
      scalacOptions in (config, doc) += "-no-link-warnings"
    )
  }

  /** This should be added to the subproject containing the main class. */
  def allOneJarSettings(mainClassName: String): Seq[Def.Setting[_]] = {
    oneJarSettings :+ (mainClass in oneJar := Some(mainClassName))
  }

  /** This should be appended to (testOptions in IntegrationTest) */
  def itTestOptions(
    javaHomeValue: Option[File],
    classpathPrefix: Seq[File],
    oneJarValue: File,
    streamsValue: Keys.TaskStreams
  ): Seq[TestOption] = {
    val canonicalJavaHome = javaHomeValue.map(_.getCanonicalPath)
    lazy val itServer =
      new CosmosIntegrationTestServer(canonicalJavaHome, classpathPrefix, oneJarValue)

    Seq(
      Tests.Setup(() => itServer.setup(streamsValue.log)),
      Tests.Cleanup(() => itServer.cleanup())
    )
  }

  lazy val publishSettings: Seq[Def.Setting[_]] = Seq(
    publishMavenStyle := true,
    pomIncludeRepository := { _ => false },
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value) {
        Some("snapshots" at nexus + "content/repositories/snapshots")
      } else {
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
      }
    }
  )

  private def parseScalaVersion(v: String): List[Int] = v.split('.').toList.map(_.toInt)

  private def reportTeamCityParameter(key: String, value: String): Unit = {
    // scalastyle:off regex multiple.string.literals
    println(s"##teamcity[setParameter name='env.SBT_$key' value='$value']")
    println(s"##teamcity[setParameter name='system.sbt.$key' value='$value']")
    // scalastyle:on regex multiple.string.literals
  }

}
