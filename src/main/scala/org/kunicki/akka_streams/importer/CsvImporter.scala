package org.kunicki.akka_streams.importer

import java.nio.file.Paths

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
}
