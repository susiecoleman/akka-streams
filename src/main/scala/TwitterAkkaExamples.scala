import BasicAkkaExamples.lineSink
import akka.stream.{ActorMaterializer, ClosedShape, IOResult}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import cats.syntax.semigroupal._
import cats.implicits._
import cats._
import cats.data._

import scala.concurrent.{ExecutionContextExecutor, Future}

object TwitterAkkaExamples {

  val akkaTag = Hashtag("#akka")




  def tweetCounter() = {

    implicit val system = ActorSystem("tweet-counter")
    implicit val materializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val tweets: Source[ATweet, NotUsed] = Source(
      ATweet(Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
        ATweet(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
        ATweet(Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
        ATweet(Author("drewhk"), System.currentTimeMillis, "#akka !") ::
        ATweet(Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
        ATweet(Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
        ATweet(Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
        ATweet(Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
        ATweet(Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
        ATweet(Author("drama"), System.currentTimeMillis, "we compared #apples to #oranges!") ::
        Nil)

    val count: Flow[ATweet, Int, NotUsed] = Flow[ATweet].map(_ ⇒ 1)

    val sumSink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)(_ + _)

    val counterGraph: RunnableGraph[Future[Int]] =
      tweets
        .via(count)
        .toMat(sumSink)(Keep.right)

    val sum: Future[Int] = counterGraph.run()

    sum.foreach(c ⇒ println(s"Total tweets processed: $c"))
    sum.onComplete(_ => system.terminate())

  }

  def tweetGraph() = {
    implicit val system = ActorSystem("tweet-graph")
    implicit val materializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val tweets: Source[ATweet, NotUsed] = Source(
      ATweet(Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
        ATweet(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
        ATweet(Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
        ATweet(Author("drewhk"), System.currentTimeMillis, "#akka !") ::
        ATweet(Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
        ATweet(Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
        ATweet(Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
        ATweet(Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
        ATweet(Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
        ATweet(Author("drama"), System.currentTimeMillis, "we compared #apples to #oranges!") ::
        Nil)

    val writeAuthors = Sink.foreach[Author](println)
    val writeHashtags = Sink.foreach[Hashtag](println)
    val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val bcast = b.add(Broadcast[ATweet](2))
      tweets ~> bcast.in
      bcast.out(0) ~> Flow[ATweet].map(_.author) ~> writeAuthors
      bcast.out(1) ~> Flow[ATweet].mapConcat(_.hashtags.toList) ~> writeHashtags
      ClosedShape
    })
    g.run()
  }

  def getTweetAuthors() = {
    implicit val system = ActorSystem("reactive-tweets")
    implicit val materializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val tweets: Source[ATweet, NotUsed] = Source(
      ATweet(Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
        ATweet(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
        ATweet(Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
        ATweet(Author("drewhk"), System.currentTimeMillis, "#akka !") ::
        ATweet(Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
        ATweet(Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
        ATweet(Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
        ATweet(Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
        ATweet(Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
        ATweet(Author("drama"), System.currentTimeMillis, "we compared #apples to #oranges!") ::
        Nil)

    val authors: Source[Author, NotUsed] =
      tweets
        .filter(_.hashtags.contains(akkaTag))
        .map(_.author)

    val result = authors.runWith(Sink.foreach(println))
    result.onComplete(_ ⇒ system.terminate())
  }

  def tweets(): Unit = {

    implicit val system: ActorSystem = ActorSystem("reactive-tweets")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val tweets: Source[ATweet, NotUsed] = Source(
      ATweet(Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
        ATweet(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
        ATweet(Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
        ATweet(Author("drewhk"), System.currentTimeMillis, "#akka !") ::
        ATweet(Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
        ATweet(Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
        ATweet(Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
        ATweet(Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
        ATweet(Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
        ATweet(Author("drama"), System.currentTimeMillis, "we compared #apples to #oranges!") ::
        Nil)

    val hashtags: Source[String, NotUsed] = tweets
      .map(_.hashtags) // Get all sets of hashtags ...
      .reduce(_ ++ _) // ... and reduce them to a single set, removing duplicates across all tweets
      .mapConcat(identity) // Flatten the stream of tweets to a stream of hashtags
      .map(_.name.toUpperCase) // Convert all hashtags to upper case


    val printing = hashtags.runWith(Sink.foreach(println)) // Attach the Flow to a Sink that will print the hashtags
    val filesave = hashtags.runWith(lineSink("hashtags.txt"))

    case class Results(printing: Done, file: IOResult, printing2: Done)

    val res: Future[Results] = (printing, filesave, printing).mapN(Results.apply)

    val result = for {
      _ <- printing
      _ <- filesave
    } yield ()

    res.onComplete(_ ⇒ system.terminate())
  }
}

final case class Author(handle: String)

final case class Hashtag(name: String)

final case class ATweet(author: Author, timestamp: Long, body: String) {
  def hashtags: Set[Hashtag] = body.split(" ").collect {
    case t if t.startsWith("#") ⇒ Hashtag(t.replaceAll("[^#\\w]", ""))
  }.toSet
}