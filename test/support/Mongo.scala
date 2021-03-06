package support

import java.util.concurrent.TimeUnit

import io.sdkman.repos.{Candidate, Version}
import org.mongodb.scala.{MongoClient, _}
import org.mongodb.scala.bson.collection.immutable.Document

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import org.mongodb.scala.ScalaObservable

object Mongo {

  import Helpers._

  lazy val mongoClient = MongoClient("mongodb://localhost:27017")

  lazy val db = mongoClient.getDatabase("sdkman")

  lazy val appCollection = db.getCollection("application")

  def insertAliveOk() = appCollection.insertOne(Document("alive" -> "OK")).results()

  lazy val versionsCollection = db.getCollection("versions")

  lazy val candidatesCollection = db.getCollection("candidates")

  def insertVersions(vs: Seq[Version]) = vs.foreach(insertVersion)

  def insertVersion(v: Version) =
    versionsCollection.insertOne(
      Document(
        "candidate" -> v.candidate,
        "version" -> v.version,
        "platform" -> v.platform,
        "url" -> v.url))
      .results()

  def insertCandidates(cs: Seq[Candidate]) = cs.foreach(insertCandidate)

  def insertCandidate(c: Candidate) =
    candidatesCollection.insertOne(
      c.default.fold(
        Document(
          "candidate" -> c.candidate,
          "name" -> c.name,
          "description" -> c.description,
          "websiteUrl" -> c.websiteUrl,
          "distribution" -> c.distribution)) { default =>
        Document(
          "candidate" -> c.candidate,
          "name" -> c.name,
          "description" -> c.description,
          "default" -> default,
          "websiteUrl" -> c.websiteUrl,
          "distribution" -> c.distribution)
      }
    ).results()

  def dropAllCollections() = {
    appCollection.drop().results()
    versionsCollection.drop().results()
    candidatesCollection.drop().results()
  }
}

object Helpers {

  implicit class DocumentObservable[C](val observable: Observable[Document]) extends ImplicitObservable[Document] {
    override val converter: (Document) => String = (doc) => doc.toJson
  }

  implicit class GenericObservable[C](val observable: Observable[C]) extends ImplicitObservable[C] {
    override val converter: (C) => String = (doc) => doc.toString
  }

  trait ImplicitObservable[C] {
    val observable: Observable[C]
    val converter: (C) => String

    def results(): Seq[C] = Await.result(observable.toFuture(), Duration(10, TimeUnit.SECONDS))

    def headResult() = Await.result(observable.head(), Duration(10, TimeUnit.SECONDS))

    def printResults(initial: String = ""): Unit = {
      if (initial.length > 0) print(initial)
      results().foreach(res => println(converter(res)))
    }

    def printHeadResult(initial: String = ""): Unit = println(s"$initial${converter(headResult())}")
  }

}