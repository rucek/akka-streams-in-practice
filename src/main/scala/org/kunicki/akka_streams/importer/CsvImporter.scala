package org.kunicki.akka_streams.importer

import java.io.{File, FileInputStream}
import java.nio.file.Paths
import java.util.zip.GZIPInputStream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Framing, StreamConverters}
import akka.util.ByteString
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.kunicki.akka_streams.model.{InvalidReading, Reading, ValidReading}
import org.kunicki.akka_streams.repository.ReadingRepository

import scala.concurrent.Future

class CsvImporter(config: Config, readingRepository: ReadingRepository) extends LazyLogging {

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
}
