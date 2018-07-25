package org.kunicki.akka_streams

import java.nio.file.Paths
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.Random

object RandomDataGenerator extends App with LazyLogging {

  implicit val system = ActorSystem("random-data-generator")
  implicit val materializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val config = ConfigFactory.load()
  val numberOfFiles = config.getInt("generator.number-of-files")
  val numberOfPairs = config.getInt("generator.number-of-pairs")
  val invalidLineProbability = config.getDouble("generator.invalid-line-probability")

  logger.info("Starting generation")

  val f = Source(1 to numberOfFiles)
    .mapAsyncUnordered(numberOfFiles) { _ =>
      val fileName = UUID.randomUUID().toString
      Source(1 to numberOfPairs).map { case _ =>
        val id = Random.nextInt(1000000)
        Seq(ValidReading(id), ValidReading(id)).map { reading =>
          val value = if (Random.nextDouble() > invalidLineProbability) reading.value.toString else "invalid_value"
          ByteString(s"${reading.id};$value\n")
        }.foldLeft(ByteString())(_ concat _)
      }.runWith(FileIO.toPath(Paths.get(s"data/$fileName")))
    }
    .runWith(Sink.ignore)

  Await.ready(f, Duration.Inf)
  logger.info("Generated random data")
  f.onComplete(_ => system.terminate())
}
