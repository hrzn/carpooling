name := "ridesharing"

version := "0.0"

scalaVersion := "2.12.4"

libraryDependencies ++=
  Seq(
    "com.graphhopper" % "graphhopper-reader-osm" % "0.8.2",
    "joda-time" % "joda-time" % "2.9.9",
    "com.typesafe" % "config" % "1.3.1")
