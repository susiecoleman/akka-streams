# AKKA Streams

Experimenting with the AKKA Streams Library

## Basic Elements of Akka Stream

Components can be reused typically we are defining blueprints for what we want to run then running them. 

- Sources: Emit a stream
- Sinks: Receive a stream
- Flows: Receive a stream then emit a stream. Usually performing a transformation on stream items
- Throttle: Controls the speed at which the stream flows. Will assert back pressure upstream if necessary

## Questions for next time
- A better way to deal with futures in the tweet function
- Keep left and keep right?