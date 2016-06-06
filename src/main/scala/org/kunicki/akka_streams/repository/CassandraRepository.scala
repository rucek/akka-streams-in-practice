package org.kunicki.akka_streams.repository
import org.kunicki.akka_streams.model.ValidReading

import scala.concurrent.Future

import com.websudos.phantom.dsl._

class ReadingRepository {

  def save(reading: ValidReading): Future[ResultSet] = MyDatabase.readings.store(reading)
}

object Defaults {

  val connector = ContactPoint.local.keySpace("akka_streams")
}

class Readings extends CassandraTable[ConcreteReadings, ValidReading] {

  object id extends IntColumn(this) with PrimaryKey[Int]

  object value extends DoubleColumn(this)

  def fromRow(row: Row): ValidReading = ValidReading(id(row), value(row))
}

abstract class ConcreteReadings extends Readings with RootConnector {

  def store(reading: ValidReading): Future[ResultSet] = {
    insert().value(_.id, reading.id).value(_.value, reading.value).future()
  }
}

class MyDatabase(val keyspace: KeySpaceDef) extends Database(keyspace) {
  object readings extends ConcreteReadings with keyspace.Connector
}

object MyDatabase extends MyDatabase(Defaults.connector)
