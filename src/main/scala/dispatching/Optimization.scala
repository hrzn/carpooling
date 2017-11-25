package dispatching

import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime

object Optimization {
  private val conf = ConfigFactory.load()
  private val maxSpeedKmh = conf.getDouble("max.speed.kmh")

  /**
    * Selects the best itinerary for a given set of passengers, starting from a specified position
    */
  def getBestItineraryForCar(passengers: Set[Passenger], currentPosition: GPSPoint,
                             currentTime: DateTime, carCapacity: Int): Option[Itinerary] = {
    val itineraries = computeFeasibleItineraries(passengers, currentPosition, currentTime, carCapacity)
    if (itineraries.isEmpty) None
    else Some(itineraries.minBy(_.duration.getMillis))
  }

  /**
    * Computes whether it is (approximately) feasible to go from A to B before a given deadline
    * The approximation is obtained by assuming cars can only travel below a certain speed
    */
  def isApproximatelyFeasible(origin: GPSPoint, destination: GPSPoint, startTime: DateTime, deadline: DateTime): Boolean = {
    val distanceKm = origin.distanceTo(destination)
    val minDurationSeconds = 3600 * distanceKm / maxSpeedKmh
    startTime.plusSeconds(minDurationSeconds.toInt).getMillis <= deadline.getMillis
  }


  /**
    * Compute all feasible itineraries for a list of passengers, starting from a current position at a given time.
    * If there is no feasible itinerary (given passengers' constraints), returns an empty list
    * @param passengers The set of passengers to dropoff
    * @param currentPosition The current position of the car (i.e., where the execution in the present call has to start)
    * @param currentTime Current time (corresponding to current position)
    * @param carCapacity The capacity of the car carying the passengers (without driver)
    */
  private def computeFeasibleItineraries(passengers: Set[Passenger], currentPosition: GPSPoint,
                                         currentTime: DateTime, carCapacity: Int): Set[Itinerary] = {

    passengers.flatMap{ p =>
      if (p.pickedUp) {
        // try to dropoff p
        if (!isApproximatelyFeasible(currentPosition, p.destination, currentTime, p.maxDropoffTime.get))
          // There is no way we can deliver this passenger on time
          return Set.empty

        // Get a better estimate with the actual duration
        val durationOpt = Routing.getDuration(currentPosition, p.destination)
        if (durationOpt.isEmpty) return Set.empty  // there is no path
        val estimatedDropoffTime = currentTime.plus(durationOpt.get)
        if (estimatedDropoffTime.getMillis > p.maxDropoffTime.get.getMillis) return Set.empty  // we cannot make it on time

        // assume we dropoff p first. Compute segment and, if necessary, recurse further
        val segment = CourseSegment(p.id, currentPosition, p.destination, CourseType.Dropoff, currentTime, durationOpt.get)

        if (passengers.size == 1) return Set(Itinerary(List(segment)))  // this is the first segment, down the recursion

        // compute possibilities for remaining passengers
        val remainingItineraries = computeFeasibleItineraries(passengers.filter(_.id != p.id), p.destination,
          estimatedDropoffTime, carCapacity)
        remainingItineraries.map(i => Itinerary(segment :: i.segments))

      } else {
        // Try to pickup p
        val occupancy = passengers.count(_.pickedUp)
        assert(occupancy <= carCapacity)

        if (occupancy == carCapacity) return Set.empty  // we cannot pickup anyone now
        if (!isApproximatelyFeasible(currentPosition, p.origin, currentTime, p.maxPickupTime)) return Set.empty  // we cannot make it

        // Get a better estimate with the actual duration
        val durationOpt = Routing.getDuration(currentPosition, p.origin)
        if (durationOpt.isEmpty) return Set.empty  // there is no path
        val estimatedPickupTime = currentTime.plus(durationOpt.get)
        if (estimatedPickupTime.getMillis > p.maxPickupTime.getMillis) return Set.empty  // we cannot make it on time

        val newPassengers = passengers.map( pa => if (pa.id == p.id) pa.pickUp(estimatedPickupTime) else pa )
        val remainingItineraries = computeFeasibleItineraries(newPassengers, p.origin, estimatedPickupTime, carCapacity)
        val segment = CourseSegment(p.id, currentPosition, p.origin, CourseType.Pickup, currentTime, durationOpt.get)

        remainingItineraries.map(i => Itinerary(segment :: i.segments))
      }
    }
  }
}
