# Akka Streams in Practice

This is a sample Akka Streams project which uses the library to import data from a number of Gzipped CSV files into a Cassandra table.

The CSV files contain some kind of readings, i.e. `(id, value)` pairs, where every `id` has two associated `value`s and the records for a given `id` appear in subsequent lines in the file. Any of the `value`s may ocassionally be an invalid number. Example:

```
93500;0.5287942176336127
93500;0.3404326895942348
961989;invalid_value
961989;0.27452559752437566
136308;0.07525660747531115
136308;0.6509485024097678
```

The importer streams the Gzipped files and extracts them on the fly, then converts every line to a domain object representing either a valid or an invalid reading. The next step is to compute an average value for the readings under a given `id` when any of the readings is valid. When both readings for a given `id` are invalid, the average is assumed to be `-1`. Finally, the computed average values are written to Cassandra.

# Prerequisites

## CSV files
You can generate the CSV data yourself using the provided `RandomDataGenerator`. There are a few configurable properties of the generator in `application.conf`:

```
generator {
  number-of-files = 100
  number-of-pairs = 1000
  invalid-line-probability = 0.005
}
```

They are pretty self-explanatory: `number-of-files` is the number of files to be generated, `number-of-pairs` is the number of `(id, value)` pairs in each file (since two `value`s are generated for each `id`), `invalid-line-probability` is the probability of the generator inserting a line with a value that is not a valid number.

Note that the importer expectt the files to be compressed with Gzip. You can easily compress the generated files with the following command run in the `./data` directory:

```bash
find . -type f -exec gzip "{}" \;
```

Now you're ready to generate the CSV files:

```bash
sbt "runMain org.kunicki.akka_streams.RandomDataGenerator"
```

## Cassandra

The probably  easiest way to have Cassandra up and running is to use a [Docker](http://docker.io/) image - then all you need to do is run the following command:

```bash
docker run -d --name cassandra cassandra
```

and in a while you should have Cassandra ready at port 9042. When the container has started, it's time to create a keyspace and a table for our data.

First you need to run the CQL shell:

```bash
docker exec -it cassandra cqlsh
```

Then, in `cqlsh` you create an `akka_streams` keyspace:

```cql
CREATE KEYSPACE akka_streams WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : '1' };
```

Finally, let's create the `readings` table:

```cql
CREATE TABLE akka_streams.readings (id int PRIMARY KEY, value float);  
```

# Running

Before running the import you may wish to tweak some configuration settings in `application.conf`:

```
importer {
  import-directory = "./data"
  lines-to-skip = 0
  concurrent-files = 10
  concurrent-writes = 5
  non-io-parallelism = 42
}
```

The `import-directory` is the directory with the CSV files, `lines-to-skip` allows you to optionally skip a number of lines from the top of each file (e.g. CSV headers if you had any), `concurrent-files` tells the importer how many files to read in parallel, `concurrent-writes` determines the number of parallel inserts to Cassandra, `non-io-parallelism` defines the number of threads for in-memory calculations.

Having the configuration tweaked, the test data generated and a Cassandra instance running, you can now run the actual import:

```bash
sbt "runMain org.kunicki.akka_streams.Importer"
```
