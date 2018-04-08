import BasicAkkaExamples.lineSink
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.{Done, NotUsed}
import akka.actor.ActorSystem

import scala.concurrent.{ExecutionContextExecutor, Future}

object TwitterAkkaExamples {

  val akkaTag = Hashtag("#akka")


  def tweetCounter() = {

    implicit val system = ActorSystem("tweet-counter")
    implicit val materializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val tweets: Source[Tweet, NotUsed] = Source(
      Tweet(Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
        Tweet(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
        Tweet(Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
        Tweet(Author("drewhk"), System.currentTimeMillis, "#akka !") ::
        Tweet(Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
        Tweet(Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
        Tweet(Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
        Tweet(Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
        Tweet(Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
        Tweet(Author("drama"), System.currentTimeMillis, "we compared #apples to #oranges!") ::
        Nil)

    val count: Flow[Tweet, Int, NotUsed] = Flow[Tweet].map(_ ⇒ 1)

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

    val tweets: Source[Tweet, NotUsed] = Source(
      Tweet(Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
        Tweet(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
        Tweet(Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
        Tweet(Author("drewhk"), System.currentTimeMillis, "#akka !") ::
        Tweet(Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
        Tweet(Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
        Tweet(Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
        Tweet(Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
        Tweet(Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
        Tweet(Author("drama"), System.currentTimeMillis, "we compared #apples to #oranges!") ::
        Nil)

    val writeAuthors = Sink.foreach[Author](println)
    val writeHashtags = Sink.foreach[Hashtag](println)
    val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val bcast = b.add(Broadcast[Tweet](2))
      tweets ~> bcast.in
      bcast.out(0) ~> Flow[Tweet].map(_.author) ~> writeAuthors
      bcast.out(1) ~> Flow[Tweet].mapConcat(_.hashtags.toList) ~> writeHashtags
      ClosedShape
    })
    g.run()
  }

  def getTweetAuthors() = {
    implicit val system = ActorSystem("reactive-tweets")
    implicit val materializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val tweets: Source[Tweet, NotUsed] = Source(
      Tweet(Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
        Tweet(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
        Tweet(Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
        Tweet(Author("drewhk"), System.currentTimeMillis, "#akka !") ::
        Tweet(Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
        Tweet(Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
        Tweet(Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
        Tweet(Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
        Tweet(Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
        Tweet(Author("drama"), System.currentTimeMillis, "we compared #apples to #oranges!") ::
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

    val tweets: Source[Tweet, NotUsed] = Source(
      Tweet(Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
        Tweet(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
        Tweet(Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
        Tweet(Author("drewhk"), System.currentTimeMillis, "#akka !") ::
        Tweet(Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
        Tweet(Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
        Tweet(Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
        Tweet(Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
        Tweet(Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
        Tweet(Author("drama"), System.currentTimeMillis, "we compared #apples to #oranges!") ::
        Nil)

    val hashtags: Source[String, NotUsed] = tweets
      .map(_.hashtags) // Get all sets of hashtags ...
      .reduce(_ ++ _) // ... and reduce them to a single set, removing duplicates across all tweets
      .mapConcat(identity) // Flatten the stream of tweets to a stream of hashtags
      .map(_.name.toUpperCase) // Convert all hashtags to upper case

    val result = for {
      _ <- hashtags.runWith(Sink.foreach(println)) // Attach the Flow to a Sink that will print the hashtags
      _ <- hashtags.runWith(lineSink("hashtags.txt"))
    } yield ()

    result.onComplete(_ ⇒ system.terminate())
  }
}

final case class Author(handle: String)

final case class Hashtag(name: String)

final case class Tweet(author: Author, timestamp: Long, body: String) {
  def hashtags: Set[Hashtag] = body.split(" ").collect {
    case t if t.startsWith("#") ⇒ Hashtag(t.replaceAll("[^#\\w]", ""))
  }.toSet
}