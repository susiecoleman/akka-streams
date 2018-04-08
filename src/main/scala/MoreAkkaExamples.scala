import BasicAkkaExamples.source
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, OverflowStrategy}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink}

import scala.concurrent.{ExecutionContextExecutor, Future}

object MoreAkkaExamples {

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
