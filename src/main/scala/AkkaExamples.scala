import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.util.ByteString
import scala.concurrent._
import java.nio.file.Paths
import scala.concurrent.duration._

object AkkaExamples {

  // Sources emit. They are blueprints of what you want to run. They can be reused
  val source: Source[Int, NotUsed] = Source(1 to 100)

  def basicExample(): Unit = {
    implicit val system: ActorSystem = ActorSystem("basicExample")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val result: Future[Done] = source.runForeach(i ⇒ println(i))

    result.onComplete(_ ⇒ system.terminate())
  }

  def factorials(): Unit = {

    implicit val system: ActorSystem = ActorSystem("basicExample")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    // Nothing is computed at this point it describes what will be computed when the source is used
    val factorials: Source[BigInt, NotUsed] = source.scan(BigInt(1))((acc, next) ⇒ acc * next)
    // This is a data receiver
    val sink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(Paths.get("factorials.txt"))
    val result: Future[IOResult] =
      factorials
        .map(num ⇒ ByteString(s"$num\n"))
        .runWith(sink) // runWith is passed a Sink which is where a stream terminates

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

  def flows = {
    val flow = Flow[Int].map(_*2)
    val sink = flow.to(Sink.foreach(println))
  }

  def throttle(): Unit = {
    implicit val system: ActorSystem = ActorSystem("basicExample")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val result: Future[Done] = source.throttle(1, 1.second, 1, ThrottleMode.Shaping).runForeach(println)

    result.onComplete(_ ⇒ system.terminate())
  }

  def buffer(): Unit = {
    implicit val system: ActorSystem = ActorSystem("basicExample")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val result: Future[Done] = source.buffer(2, OverflowStrategy.dropBuffer).runForeach{ x =>
      Thread.sleep(10000)
      println(x)
    }

    result.onComplete(_ ⇒ system.terminate())
  }

  def graph: NotUsed = {

    implicit val system: ActorSystem = ActorSystem("basicExample")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

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
