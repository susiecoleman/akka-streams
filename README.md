# AKKA Streams

Experimenting with the AKKA Streams Library

## Basic Elements of Akka Stream

Streams always start flowing from a `Source[Out,M1]` then can continue through `Flow[In,Out,M2]` elements or more advanced graph elements to finally be consumed by a `Sink[In,M3]`

Components can be reused typically we are defining blueprints for what we want to run then running them. 

* Sources: Emit a stream (Publishers)
* Sinks: Receive a stream (Subscribers)
* Flows: Receive a stream then emit a stream. Usually performing a transformation on stream items
* Throttle: Controls the speed at which the stream flows. Will assert back pressure upstream if necessary

Akka streams always propagate back pressure from Sinks to Sources. 

## More Elements of Akka Stream

* Junctions: Are used to form 'fan-in' or 'fan-out' structures. These structures either split a source stream or combine multiple streams into 1
    * Broadcast: Emits elements from it's input ports to all of it's output ports
* Buffers: Buffering must be handled explicitly

## Materialized Values

A blueprint is defined for the stream using Source, Sink, Flow etc their type signatures also tell us what type they will be once materialized. `Keep.right` and `Keep.left` are used to specify that we should only care about the materialised type appended to the right or left. e.g
```scala
def lineSink(filename: String): Sink[String, Future[IOResult]] =
    Flow[String]
      .map(s ⇒ ByteString(s + "\n"))
      .toMat(fileSink(filename))(Keep.right)
```

`runWith()` is a convenience method that automatically ignores the materialized value of any other stages except those appended by the `runWith()`

## Questions for next time
- A better way to deal with futures in the tweet function
- Keep left and keep right? materialized types and not used for Source
- Why can't the tweets param be at an object level?
- Tweet counter why foreach?
