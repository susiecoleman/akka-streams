import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.streaming.StreamingMessage
import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken, Tweet}
import com.danielasfregola.twitter4s.http.clients.streaming.TwitterStream

import scala.concurrent.{ExecutionContextExecutor, Future}

object ExternalStream {

  val consumerToken = ConsumerToken(key = "key", secret = "secret")

  val accessToken = AccessToken(key = "key", secret = "secret")

  val streamingClient = TwitterStreamingClient(consumerToken, accessToken)

  def printTweetText: PartialFunction[StreamingMessage, Unit] = {
    case tweet: Tweet => println(tweet.text)
  }

  val stream: Future[TwitterStream] = streamingClient.sampleStatuses(stall_warnings = true)(printTweetText)

  val source: Source[TwitterStream, NotUsed] = Source.fromFuture(stream)

  def printTweets(): Future[Done] = {
    implicit val system = ActorSystem("tweet-stream")
    implicit val materializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    source.runForeach(println)
  }
}
