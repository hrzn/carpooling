package dispatching

import java.nio.file.{Path, Paths}

import com.typesafe.config.ConfigFactory
import org.joda.time.{DateTime, LocalDate}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import utils.GPSPoint

import scala.io.Source


/**
  * An actual ride request
  *
  * @param time when the request arrived
  * @param origin (lat, lon) origin
  * @param destination (lat, lon) destination
  * @param nrPassengers number of passengers
  * @param pickupDeadline the deadline, if any, for the pickup
  * @param stretchTolerance the fraction of ideal travel time by which actual duration can be inflated
  *
  * @param distance the real distance traveled by the real-world car
  * @param fareAmount the fare amount (not including tips, tolls etc)
  * @param dropoffTime the time at which the actual passenger(s) were dropped off
  */
case class RideRequest(time: DateTime, origin: GPSPoint, destination: GPSPoint, nrPassengers: Int,
                       pickupDeadline: Option[DateTime], stretchTolerance: Double,
                       distance: Double, fareAmount: Double, dropoffTime: DateTime)

object RideRequest {
  private val conf = ConfigFactory.load()

  private val dtFormatter: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  private val pickupToleranceMinutes = conf.getInt("passengers.pickup.tolerance.minutes")
  private val stretchTolerance = conf.getDouble("passengers.stretch.tolerance")

  def apply(csvFields: Map[String, String]): RideRequest = {
    /**
      * Parses an NYC yellow cab demand
      */
    val pickupDt = dtFormatter.parseDateTime(csvFields(Demands.pickupDtKey))
    val dropoffDt = dtFormatter.parseDateTime(csvFields(Demands.dropoffDtKey))

    val nrPassengers = csvFields(Demands.passengerCountKey).toInt
    val distance = csvFields(Demands.tripDistanceKey).toDouble
    val pickupLon = csvFields(Demands.pickupLonKey).toDouble
    val pickupLat = csvFields(Demands.pickupLatKey).toDouble
    val dropoffLon = csvFields(Demands.dropoffLonKey).toDouble
    val dropoffLat = csvFields(Demands.dropoffLatKey).toDouble
    val fareAmount = csvFields(Demands.fareAmountKey).toDouble

    val origin = GPSPoint(pickupLat, pickupLon)
    val destination = GPSPoint(dropoffLat, dropoffLon)

    val pickupDeadline = pickupDt.plusMinutes(pickupToleranceMinutes)

    new RideRequest(pickupDt, origin, destination, nrPassengers, Some(pickupDeadline),
      stretchTolerance, distance, fareAmount, dropoffDt)
  }
}


object Demands {
  /**
    * Code to handle yellow cabs csv files
    * Parses NYC Yellow cabs .csv files upto June 2016 (after which the files do not contain lat/lon of
    * dropoff/pickups).
    */
  private val conf = ConfigFactory.load()

  val pickupDtKey = "pickup_datetime"
  val dropoffDtKey = "dropoff_datetime"
  val passengerCountKey = "passenger_count"
  val tripDistanceKey = "trip_distance"
  val pickupLonKey = "pickup_longitude"
  val pickupLatKey = "pickup_latitude"
  val dropoffLonKey = "dropoff_longitude"
  val dropoffLatKey = "dropoff_latitude"
  val fareAmountKey = "fare_amount"

  private val baseDataDir = conf.getString("base.data.dir")

  def getDayYellow(date: LocalDate): List[RideRequest] = {
    // TODO: Use Scala pickling for per-day caching?

    val filePath: Path = Paths.get(baseDataDir, "yellow_tripdata_" + date.getYear.toString + "-" + "%02d".format(date.getMonthOfYear) + ".csv")
    println("Will read file " + filePath)

    val dateStr = date.toString("yyyy-MM-dd")

    val bufferedSource = Source.fromFile(filePath.toString)
    val firstLine = bufferedSource.getLines().find(_ => true).get  // raise an exception if no first line is present
    val fieldsWithIdx = firstLine.split(",").zipWithIndex

    // get all fields indices
    val allKeys = List(pickupDtKey, dropoffDtKey, passengerCountKey, tripDistanceKey, pickupLonKey, pickupLatKey,
      dropoffLonKey, dropoffLatKey, fareAmountKey)

    def getIndexOfKey(key: String): Int = fieldsWithIdx.filter{case (s, _) => s contains key}.head._2
    val fieldIndexes = allKeys.map(k => k -> getIndexOfKey(k)).toMap

    val requests = bufferedSource.getLines.flatMap{ line =>
      val fields = line.split(",")
      if (fields(fieldIndexes(pickupDtKey)) contains dateStr) {
        val fieldMap = fieldIndexes.map{ case (key, idx) => key -> fields(idx) }
        Some(RideRequest(fieldMap))
      } else None
    }.toList.sortBy(_.time.getMillis)

    bufferedSource.close

    requests
  }
}
