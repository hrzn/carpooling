package dispatching

import java.util.Locale

import com.graphhopper.{GHRequest, GraphHopper, PathWrapper}
import com.graphhopper.reader.osm.GraphHopperOSM
import com.graphhopper.routing.util.EncodingManager
import com.typesafe.config.ConfigFactory
import org.joda.time.Duration


// TODO: implement caching here

object Routing {
  private val conf = ConfigFactory.load()

  private val osmFile: String = conf.getString("routing.osm.file")
  private val ghLocation: String = conf.getString("routing.graphhopper.location")
  private val weightScale = conf.getDouble("routing.weight.scale")

  private val hopper: GraphHopper = new GraphHopperOSM().forServer()
  hopper.setDataReaderFile(osmFile)

  hopper.setGraphHopperLocation(ghLocation)
  hopper.setEncodingManager(new EncodingManager("car"))

  hopper.importOrLoad()

  /**
    * Do not use this directly for computing durations
    */
  def getPath(origin: GPSPoint, destination: GPSPoint): Option[PathWrapper] = {
    val req = new GHRequest(origin.lat, origin.lon, destination.lat, destination.lon).
      setWeighting("fastest").
      setVehicle("car").
      setLocale(Locale.US)
    val resp = hopper.route(req)

    if (resp.hasErrors) None
    else Some(resp.getBest)
  }

  /**
    * Returns expected duration (in ms) of the corresponding path
    * @param origin
    * @param destination
    * @return
    */
  def getDuration(origin: GPSPoint, destination: GPSPoint): Option[Duration] =
    getPath(origin, destination).map(getDuration)

  def getDuration(pw: PathWrapper): Duration = new Duration((pw.getTime * weightScale).toLong)
}
