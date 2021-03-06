package org.kunicki.akka_streams

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.kunicki.akka_streams.importer.CsvImporter
import org.kunicki.akka_streams.repository.ReadingRepository


object Importer extends App {

  implicit val system = ActorSystem("akka-streams-in-practice")

  private val config = ConfigFactory.load()
  private val readingRepository = new ReadingRepository

  import system.dispatcher

  new CsvImporter(config, readingRepository).importFromFiles
    .onComplete { _ =>
      readingRepository.shuthdown
      system.terminate()
    }
}
