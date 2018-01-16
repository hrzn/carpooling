package dispatching

import org.joda.time.{DateTime, Duration}
import utils.GPSPoint

/**
  * The car is the "owner" of passengers. This is because passengers need to change
  * as we learn about pickups and dropoffs
  */
case class Car(position: GPSPoint, capacity: Int, itinerary: Itinerary, passengers: Set[Passenger]) {
  /**
    * Returns a new Car, which includes a new passenger (and, correspondingly, a new itinerary)
    */
  def withPassenger(newPassenger: Passenger, newItinerary: Itinerary): Car = {
    val newPassengers = setPickupDropoffTimes(newItinerary, newPassenger)
    this.copy(passengers = newPassengers, itinerary = newItinerary)
  }

  /**
    * Returns an updated set of (new) passengers, with pickup and dropoff times set according to new itinerary
    */
  private def setPickupDropoffTimes(newItinerary: Itinerary, newPassenger: Passenger): Set[Passenger] = {

    val passengerById: Map[Int, Passenger] = (passengers + newPassenger).map(p => p.id -> p).toMap

    newItinerary.segments.foldLeft(newItinerary.startTime, Set.empty[Passenger]){ case ((curTime, curPassengers), segment) =>
      val newTime = curTime.plus(segment.duration)
      val passenger = curPassengers.find(_.id == segment.passengerId).getOrElse(passengerById(segment.passengerId))

      // remove old one before adding new passenger object in the set; otherwise passengers may be added several times
      // with different pickup dropoff times
      val newCurPassengers = curPassengers - passenger
      segment.courseType match {
        case CourseType.Pickup => (newTime, newCurPassengers + passenger.withPickupTime(newTime))
        case CourseType.Dropoff => (newTime, newCurPassengers + passenger.withDropoffTime(newTime))
      }
    }._2
  }

  /**
    * Returns a new Car, with updated position, and updated state (account for dropoffs and pickups)
    * Also returns number of pickups and dropoffs, respectively.
    */
  def update(currentTime: DateTime): (Car, Int, Int) = {
    val newPosition: GPSPoint = itinerary.getPositionAtTime(currentTime)

    // account for pickups
    var nrPickups: Int = 0
    val withPickups = passengers.map { p =>
      if (!p.pickedUp && p.expPickupTime.get.getMillis < currentTime.getMillis) {
        nrPickups += 1
        p.pickUp(p.expPickupTime.get)
      }
      else p
    }

    // account for dropoffs
    val remainingPassengers = withPickups.filterNot(p => p.pickedUp && p.expDropoffTime.get.getMillis < currentTime.getMillis)
    val nrDropoffs = withPickups.size - remainingPassengers.size

    (this.copy(position = newPosition, passengers = remainingPassengers), nrPickups, nrDropoffs)
  }
}


case class Passenger(id: Int, origin: GPSPoint, destination: GPSPoint, maxPickupTime: Option[DateTime],
                     idealTravelTime: Duration, stretchTolerance: Double, pickedUp: Boolean = false,
                     expPickupTime: Option[DateTime] = None, expDropoffTime: Option[DateTime] = None,
                     idealDropoffTime: Option[DateTime] = None, maxDropoffTime: Option[DateTime] = None) {

  /**
    * Returns a new Passenger, which is picked up at a specified time
    */
  def pickUp(pickupTime: DateTime): Passenger = {
    assert(!pickedUp)  // we cannot create a picked-up version of a passenger that's already picked up

    val idealDropoffTime: DateTime = pickupTime.plus(idealTravelTime)
    val extraTimeSecs = (idealTravelTime.getStandardSeconds * stretchTolerance).toInt
    val maxDropoffTime: DateTime = idealDropoffTime.plusSeconds(extraTimeSecs)

    this.copy(pickedUp = true, expPickupTime = Some(pickupTime), idealDropoffTime = Some(idealDropoffTime),
      maxDropoffTime = Some(maxDropoffTime))
  }

  /**
    * Returns a new Passenger, with pickup time updated (passenger may not be picked up though)
    */
  def withPickupTime(expPickupTime: DateTime): Passenger = this.copy(expPickupTime = Some(expPickupTime))

  /**
    * Returns a new Passenger, with dropoff time updated (passenger may not be dropped off though)
    */
  def withDropoffTime(expDropoffTime: DateTime): Passenger = this.copy(expDropoffTime = Some(expDropoffTime))


  // TODO: eq and hash?
  override def toString: String = "{" + id.toString + s", pu=$pickedUp, put=${expPickupTime.map(_.toString("HH:mm:ss")).getOrElse("-")}" + "}"
}