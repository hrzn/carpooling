package dispatching.routing

import java.util.Locale

import com.graphhopper.reader.osm.GraphHopperOSM
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.{GHRequest, GraphHopper, PathWrapper}
import com.typesafe.config.ConfigFactory
import org.joda.time.Duration
import utils.{Cache, GPSPoint}


object Routing {
  private val conf = ConfigFactory.load()

  private val osmFile: String = conf.getString("routing.osm.file")
  private val ghLocation: String = conf.getString("routing.graphhopper.location")
  private val weightScale = conf.getDouble("routing.weight.scale")
  private val cacheSpatialResolutionM = conf.getInt("routing.cache.resolution.meters")
  private val cacheMaxSize = conf.getInt("routing.cache.max.size")

  private val hopper: GraphHopper = new GraphHopperOSM().forServer()
  hopper.setDataReaderFile(osmFile)
  hopper.setGraphHopperLocation(ghLocation)
  hopper.setEncodingManager(new EncodingManager("car"))
  hopper.importOrLoad()

  /**
    * Operations related to caching for paths:
    * We use a Long to denote a cell in the key instead of an (Int, Int) tuple, using Cantor pairing
    * We do this because apparently hashing Tuple2 looks quite inefficient...
    */
  private type GridCell = (Int, Int)
  private val cache = new Cache[(Long, Long), Option[PathWrapper]](cacheMaxSize)

  private val metersInOneLatitudeDegree = 111e3
  private val metersInOneLongitudeDegreeAtEquator = 111321D
  private def metersInOneLongitudeDegree(lat: Double) = Math.cos(lat.toRadians) * metersInOneLongitudeDegreeAtEquator

  private def getGridCell(point: GPSPoint): GridCell = {
    val x = (point.lat * metersInOneLatitudeDegree / cacheSpatialResolutionM).toInt
    val y = (point.lon * metersInOneLongitudeDegree(point.lat) / cacheSpatialResolutionM).toInt
    (x, y)
  }

  private def cantorPairing(x: Int, y: Int): Long = (x + y) * (x + y + 1) / 2 + y

  /**
    * Entry point for getting paths.
    * @param origin
    * @param destination
    * @return
    */
  def getPath(origin: GPSPoint, destination: GPSPoint): Option[PathWrapper] = {
    val originCell = getGridCell(origin)
    val destCell = getGridCell(destination)
    val key = (cantorPairing(originCell._1, originCell._2), cantorPairing(destCell._1, destCell._2))  // TODO: cleaner
    cache.get(key) match {
      case Some(pathOpt) => pathOpt
      case None =>
        // we have to compute the path
        val pathOpt = computePath(origin, destination)
        cache.put(key, pathOpt)
        pathOpt
    }
  }

  /**
    * Returns expected duration (in ms) of the corresponding path
    */
  def getDuration(origin: GPSPoint, destination: GPSPoint): Option[Duration] =
    getPath(origin, destination).map(getDuration)

  def getDuration(pw: PathWrapper): Duration = new Duration((pw.getTime * weightScale).toLong)

  def getAndResetCacheStats = cache.getAndResetStats

  /**
    * Computes a path using graphhopper between an origin and a destination
    */
  private def computePath(origin: GPSPoint, destination: GPSPoint): Option[PathWrapper] = {
    val req = new GHRequest(origin.lat, origin.lon, destination.lat, destination.lon).
      setWeighting("fastest").
      setVehicle("car").
      setLocale(Locale.US)
    val resp = hopper.route(req)

    if (resp.hasErrors) None
    else Some(resp.getBest)
  }
}
