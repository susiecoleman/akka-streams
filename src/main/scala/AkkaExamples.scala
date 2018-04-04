import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.util.ByteString
import scala.concurrent._
import java.nio.file.Paths
import scala.concurrent.duration._

object AkkaExamples {

  def basicExample(): Unit = {
    implicit val system: ActorSystem = ActorSystem("basicExample")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val source: Source[Int, NotUsed] = Source(1 to 100)
    val done: Future[Done] = source.runForeach(i ⇒ println(i))

    done.onComplete(_ ⇒ system.terminate())
  }

  def factorials(): Unit = {

    implicit val system: ActorSystem = ActorSystem("basicExample")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val source: Source[Int, NotUsed] = Source(1 to 100)
    val factorials = source.scan(BigInt(1))((acc, next) ⇒ acc * next)
    val result: Future[IOResult] =
      factorials
        .map(num ⇒ ByteString(s"$num\n"))
        .runWith(FileIO.toPath(Paths.get("factorials.txt")))

    result.map(x => println(x.count))

    result.onComplete(_ ⇒ system.terminate())
  }

  def tweets(): Unit = {

    val akkaTag = Hashtag("#akka")

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

    implicit val system: ActorSystem = ActorSystem("reactive-tweets")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val result = tweets
      .map(_.hashtags) // Get all sets of hashtags ...
      .reduce(_ ++ _) // ... and reduce them to a single set, removing duplicates across all tweets
      .mapConcat(identity) // Flatten the stream of tweets to a stream of hashtags
      .map(_.name.toUpperCase) // Convert all hashtags to upper case
      .runWith(Sink.foreach(println)) // Attach the Flow to a Sink that will finally print the hashtags

    result.onComplete(_ ⇒ system.terminate())
  }

  def throttle(): Unit = {
    implicit val system: ActorSystem = ActorSystem("basicExample")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val source: Future[Done] = Source(1 to 100).throttle(1, 1.second, 1, ThrottleMode.Shaping).runForeach(println)

    source.onComplete(_ ⇒ system.terminate())
  }

  def buffer(): Unit = {
    implicit val system: ActorSystem = ActorSystem("basicExample")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val source: Future[Done] = Source(1 to 100).buffer(2, OverflowStrategy.dropBuffer).runForeach{ x =>
      Thread.sleep(10000)
      println(x)
    }

    source.onComplete(_ ⇒ system.terminate())
  }

  def graph: NotUsed = {

    implicit val system: ActorSystem = ActorSystem("basicExample")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    val source = Source(1 to 100)

    val evenFlow = Flow[Int].filter(_ % 2 == 0)

    val odd: Sink[Int, Future[Done]] = Sink.foreach[Int](println)
    val even: Sink[Int, Future[Done]] = Sink.foreach[Int](x => println(x * 10))
    val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val bcast = b.add(Broadcast[Int](2))
      source ~> bcast.in
      bcast.out(0) ~> evenFlow ~> even
      bcast.out(1) ~> Flow[Int].filter(_ % 2 != 0) ~> odd
      ClosedShape
    })
    g.run()
  }

}

final case class Author(handle: String)

final case class Hashtag(name: String)

final case class Tweet(author: Author, timestamp: Long, body: String) {
  def hashtags: Set[Hashtag] = body.split(" ").collect {
    case t if t.startsWith("#") ⇒ Hashtag(t.replaceAll("[^#\\w]", ""))
  }.toSet
}
