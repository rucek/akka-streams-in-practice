package org.kunicki.akka_streams.importer

import java.io.{File, FileInputStream}
import java.nio.file.Paths
import java.util.zip.GZIPInputStream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, Flow, Framing, GraphDSL, Merge, Sink, Source, StreamConverters}
import akka.stream.{ActorAttributes, ActorMaterializer, FlowShape, Supervision}
import akka.util.ByteString
import com.typesafe.config.Config
import com.typesafe.scalalogging.{LazyLogging, Logger}
import com.websudos.phantom.dsl.ResultSet
import org.kunicki.akka_streams.model.{InvalidReading, Reading, ValidReading}
import org.kunicki.akka_streams.repository.ReadingRepository

import scala.concurrent.Future
import scala.util.{Failure, Success}

class CsvImporter(config: Config, readingRepository: ReadingRepository)
                 (implicit system: ActorSystem) extends LazyLogging {

  import system.dispatcher

  private val importDirectory = Paths.get(config.getString("importer.import-directory")).toFile
  private val linesToSkip = config.getInt("importer.lines-to-skip")
  private val concurrentFiles = config.getInt("importer.concurrent-files")
  private val concurrentWrites = config.getInt("importer.concurrent-writes")
  private val nonIOParallelism = config.getInt("importer.non-io-parallelism")

  def parseLine(filePath: String)(line: String): Future[Reading] = Future {
    val fields = line.split(";")
    val id = fields(0).toInt
    try {
      val value = fields(1).toDouble
      ValidReading(id, value)
    } catch {
      case t: Throwable =>
        logger.error(s"Unable to parse line in $filePath:\n$line: ${t.getMessage}")
        InvalidReading(id)
    }
  }

  val lineDelimiter: Flow[ByteString, ByteString, NotUsed] =
    Framing.delimiter(ByteString("\n"), 128, allowTruncation = true)

  val parseFile: Flow[File, Reading, NotUsed] =
    Flow[File].flatMapConcat { file =>
      val gzipInputStream = new GZIPInputStream(new FileInputStream(file))

      StreamConverters.fromInputStream(() => gzipInputStream)
        .via(lineDelimiter)
        .drop(linesToSkip)
        .map(_.utf8String)
        .mapAsync(parallelism = nonIOParallelism)(parseLine(file.getPath))
    }

  val computeAverage: Flow[Reading, ValidReading, NotUsed] =
    Flow[Reading].grouped(2).mapAsyncUnordered(parallelism = nonIOParallelism) { readings =>
      Future {
        val validReadings = readings.collect { case r: ValidReading => r }
        val average = if (validReadings.nonEmpty) validReadings.map(_.value).sum / validReadings.size else -1
        ValidReading(readings.head.id, average)
      }
    }

  val storeReadings: Flow[ValidReading, ResultSet, NotUsed] =
    Flow[ValidReading].mapAsyncUnordered(parallelism = concurrentWrites) { reading =>
      readingRepository.save(reading).andThen {
        case Success(_) => logger.info(s"Saved $reading")
        case Failure(e) => logger.error(s"Unable to save $reading: ${e.getMessage}")
      }
    }

  val importSingleFile: Flow[File, ResultSet, NotUsed] =
    Flow[File]
      .via(parseFile)
      .via(computeAverage)
      .via(storeReadings)

  def importFromFiles = {
    implicit val materializer = ActorMaterializer()

    val files = importDirectory.listFiles.toList
    logger.info(s"Starting import of ${files.size} files from ${importDirectory.getPath}")

    val startTime = System.currentTimeMillis()

    val balancer = Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val balancer = builder.add(Balance[File](concurrentFiles))
      val merge = builder.add(Merge[ResultSet](concurrentFiles))

      (1 to concurrentFiles).foreach { _ =>
        balancer ~> importSingleFile ~> merge
      }

      FlowShape(balancer.in, merge.out)
    })

    Source(files)
      .via(balancer)
      .withAttributes(CsvImporter.resumingLoggingStrategy(logger))
      .runWith(Sink.ignore)
      .andThen {
        case Success(_) =>
          val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
          logger.info(s"Import finished in ${elapsedTime}s")
        case Failure(e) => logger.error("Import failed", e)
      }
  }
}

object CsvImporter {

  private def resumingLoggingDecider(logger: Logger): Supervision.Decider = {
    case e: Throwable =>
      logger.error("Exception thrown during stream processing", e)
      Supervision.Resume
  }

  def resumingLoggingStrategy(logger: Logger) =
    ActorAttributes.supervisionStrategy(resumingLoggingDecider(logger))
}
