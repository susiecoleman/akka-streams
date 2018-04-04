import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.util.ByteString
import scala.concurrent._
import java.nio.file.Paths

object Stream {

  def basicExample = {
    implicit val system = ActorSystem("basicExample")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher

    val source: Source[Int, NotUsed] = Source(1 to 100)
    val done: Future[Done] = source.runForeach(i ⇒ println(i))

    done.onComplete(_ ⇒ system.terminate())
  }

  def factorials = {

    implicit val system = ActorSystem("basicExample")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher

    val source: Source[Int, NotUsed] = Source(1 to 100)
    val factorials = source.scan(BigInt(1))((acc, next) ⇒ acc * next)
    val result: Future[IOResult] =
      factorials
        .map(num ⇒ ByteString(s"$num\n"))
        .runWith(FileIO.toPath(Paths.get("factorials.txt")))

    result.map(x => println(x.count))

    result.onComplete(_ ⇒ system.terminate())
  }

}