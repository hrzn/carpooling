package dispatching

case class GPSPoint(lat: Double, lon: Double) {
  /**
    * Exact (haversine) distance
    * @return distance to another point, in km
    */
  def distanceTo(other: GPSPoint): Double = {
    val dLat = Math.toRadians(other.lat - lat)
    val dLong = Math.toRadians(other.lon - lon)

    val startLat = Math.toRadians(lat)
    val endLat = Math.toRadians(other.lat)

    val a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(startLat) * Math.cos(endLat) * Math.pow(Math.sin(dLong / 2), 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    6371 * c
  }

  def approxDistanceTo(other: GPSPoint): Double = ???
}

object Utils {

}
