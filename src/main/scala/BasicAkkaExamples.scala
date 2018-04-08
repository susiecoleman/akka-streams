import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.util.ByteString
import scala.concurrent._
import java.nio.file.Paths
import scala.concurrent.duration._

// Based on examples in the akka streams quickstart guide https://doc.akka.io/docs/akka/current/stream/stream-quickstart.html

object BasicAkkaExamples {

  // Sources emit. They are blueprints of what you want to run. They can be reused
  val source: Source[Int, NotUsed] = Source(1 to 100)

  //Sinks receive data from a source and can be reused
  def fileSink(filename: String): Sink[ByteString, Future[IOResult]] = FileIO.toPath(Paths.get(filename))

  def lineSink(filename: String): Sink[String, Future[IOResult]] =
    Flow[String]
      .map(s ⇒ ByteString(s + "\n"))
      .toMat(fileSink(filename))(Keep.right)

  def basicExample(): Unit = {
    implicit val system: ActorSystem = ActorSystem("basicExample")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val result: Future[Done] = source.runForeach(i ⇒ println(i))

    result.onComplete(_ ⇒ system.terminate())
  }

  def factorials(): Unit = {

    implicit val system: ActorSystem = ActorSystem("factorials")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    // Nothing is computed at this point it describes what will be computed when the source is used
    val factorials: Source[BigInt, NotUsed] = source.scan(BigInt(1))((acc, next) ⇒ acc * next)
    // This is a data receiver
    val sink: Sink[ByteString, Future[IOResult]] = fileSink("factorials.txt")
    val result: Future[IOResult] =
      factorials
        .map(num ⇒ ByteString(s"$num\n"))
        .runWith(sink) // runWith is passed a Sink which is where a stream terminates

    result.map(x => println(x.count))

    result.onComplete(_ ⇒ system.terminate())
  }

  def throttle(): Unit = {
    implicit val system: ActorSystem = ActorSystem("throttle")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val result: Future[Done] = source.throttle(1, 1.second, 1, ThrottleMode.Shaping).runForeach(println)

    result.onComplete(_ ⇒ system.terminate())
  }

  def throttledFactorial() = {
    implicit val system: ActorSystem = ActorSystem("throttled-factorials")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val factorials: Source[BigInt, NotUsed] = source.scan(BigInt(1))((acc, next) ⇒ acc * next)
    val result = factorials
      .zipWith(Source(1 to 100))((num, idx) ⇒ s"$idx! = $num")
      .throttle(1, 1.second, 1, ThrottleMode.shaping)
      .runForeach(println)

    result.onComplete(_ ⇒ system.terminate())
  }

}
