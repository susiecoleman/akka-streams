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

  val tweetSource: Source[TweetExample, NotUsed] = Source(
    TweetExample(Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
      TweetExample(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
      TweetExample(Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
      TweetExample(Author("drewhk"), System.currentTimeMillis, "#akka !") ::
      TweetExample(Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
      TweetExample(Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
      TweetExample(Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
      TweetExample(Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
      TweetExample(Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
      TweetExample(Author("drama"), System.currentTimeMillis, "we compared #apples to #oranges!") ::
      Nil)

  def tweetCounter() = {

    implicit val system = ActorSystem("tweet-counter")
    implicit val materializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val count: Flow[TweetExample, Int, NotUsed] = Flow[TweetExample].map(_ ⇒ 1)

    val sumSink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)(_ + _)

    val counterGraph: RunnableGraph[Future[Int]] =
      tweetSource
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

    val writeAuthors = Sink.foreach[Author](println)
    val writeHashtags = Sink.foreach[Hashtag](println)
    val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val bcast = b.add(Broadcast[TweetExample](2))
      tweetSource ~> bcast.in
      bcast.out(0) ~> Flow[TweetExample].map(_.author) ~> writeAuthors
      bcast.out(1) ~> Flow[TweetExample].mapConcat(_.hashtags.toList) ~> writeHashtags
      ClosedShape
    })
    g.run()
  }

  def getTweetAuthors() = {
    implicit val system = ActorSystem("reactive-tweets")
    implicit val materializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val authors: Source[Author, NotUsed] =
      tweetSource
        .filter(_.hashtags.contains(akkaTag))
        .map(_.author)

    val result = authors.runWith(Sink.foreach(println))
    result.onComplete(_ ⇒ system.terminate())
  }

  def tweets(): Unit = {

    implicit val system: ActorSystem = ActorSystem("reactive-tweets")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val hashtags: Source[String, NotUsed] = tweetSource
      .map(_.hashtags) // Get all sets of hashtags ...
      .reduce(_ ++ _) // ... and reduce them to a single set, removing duplicates across all tweets
      .mapConcat(identity) // Flatten the stream of tweets to a stream of hashtags
      .map(_.name.toUpperCase) // Convert all hashtags to upper case


    val printing = hashtags.runWith(Sink.foreach(println)) // Attach the Flow to a Sink that will print the hashtags
    val filesave = hashtags.runWith(lineSink("hashtags.txt"))

    //Using applicative from cats library to resolve multiple futures in to a single future. Using this so that the futures are not dependant on each other and run in parallel.
    val res: Future[Results] = (printing, filesave, printing).mapN(Results.apply)

    //Using a for comprehension assumes that the previous futures are dependant on each other. Applicative is preferable for performance
    val result = for {
      _ <- printing
      _ <- filesave
    } yield ()

    res.onComplete(_ ⇒ system.terminate())
  }
}

final case class Author(handle: String)

final case class Hashtag(name: String)

final case class TweetExample(author: Author, timestamp: Long, body: String) {
  def hashtags: Set[Hashtag] = body.split(" ").collect {
    case t if t.startsWith("#") ⇒ Hashtag(t.replaceAll("[^#\\w]", ""))
  }.toSet
}

case class Results(printing: Done, file: IOResult, printing2: Done)
