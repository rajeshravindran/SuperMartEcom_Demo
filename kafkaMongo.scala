import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.streaming._
import org.apache.spark.streaming.kafka010
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.sql.types.{DoubleType, StringType, StructField, StructType}
import org.apache.kafka.common.serialization.StringDeserializer
import org.joda.time.format.DateTimeFormat
import com.mongodb.spark._
import org.apache.spark.streaming.kafka010.KafkaUtils
import java.util



object kafkaMongo extends App{


  val spark = SparkSession
    .builder()
    .appName("Kafka-Mongo-Spark-Stream")
    .master("local[*]")
    .config("spark.mongodb.output.uri","mongodb://127.0.0.1/learning_mongo")
    .getOrCreate()

  import spark.implicits._

  val schemaString = "ID CartID CartUpdDate ProductAdd ProductRemove isOrdered"
  val fields = schemaString.split(" ").map(fieldname => StructField(fieldname, StringType, nullable=true))
  val schema = StructType(fields)

  val consumerParams = Map[String, Object](
    "bootstrap.servers" -> "127.0.0.1:9092",
    "group.id" -> "cust-add-cart",
    "auto.offset.reset" -> "latest",
    "key.deserializer" -> classOf[StringDeserializer].getName(),
    "value.deserializer" -> classOf[StringDeserializer].getName()
  )

  val topics = Array("Cart-Add")

  val ssc = new StreamingContext(spark.sparkContext, Seconds(18))
  val kafkaConsumer = KafkaUtils.createDirectStream(ssc, PreferConsistent,Subscribe[String, String](topics, consumerParams))

  val elementStream = kafkaConsumer.map(v=>v.value()).foreachRDD{
    rdd => {
      val itemsRDD = rdd.map(i => i.split(" ")).map(e => Row(e(0).trim, e(1).trim, e(2).trim, e(3).trim, e(4).trim, e(5).trim))
      val itemsDF = spark.createDataFrame(itemsRDD, schema)
      itemsDF.show()
      MongoSpark
        .write(itemsDF)
        .option("spark.mongodb.output.uri", "mongodb://127.0.0.1/learning_mongo")
        .option("collection","cartLive")
        .mode("Append")
        .save()
    }
  }

  ssc.start()
  ssc.awaitTermination()
}



