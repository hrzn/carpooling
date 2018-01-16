package dispatching.dispatchers
import dispatching._
import dispatching.routing.Routing
import org.joda.time.{DateTime, Duration}


/**
  * A dispatcher that spawns a car when a new demand cannot be satisfied by existing cars
  * Useful to study "stable" number of cars needed to satisfy all demands.
  *
  * Upon receiving a new request, this dispatcher selects the car that can handle
  * the request with the least additional time (if any). If no such car exists, it creates a new one.
  */
class SimpleDispatcher extends Dispatcher {
  // mutable set for updating cars, and for quick deletion:
  private var cars: scala.collection.mutable.Set[Car] = scala.collection.mutable.Set.empty[Car]

  private var nextPassengerId: Int = 1  // keep track of passenger IDs, globally

  private val defaultCarCapacity = conf.getInt("cars.max.capacity")

  // global stats:
  private var totalValidDemands: Int = 0

  // slot stats
  //   temporary counters
  private var nrValidDemands: Int = 0
  private var nrInvalidDemands: Int = 0
  private var nrDeniedDemands: Int = 0
  private var sumNrCarsConsidered: Int = 0

  private var nrPickups: Int = 0
  private var nrDropoffs: Int = 0
  private var avgStretch: Double = 0D

  private var lastSlotComputeTime: Duration = new Duration(0L)
  private var slotStartWallClockTime: DateTime = new DateTime(0L)


  override def handleRequest(rr: RideRequest): Unit = {
    Routing.getPath(rr.origin, rr.destination) match {
      case Some(idealPath) =>
        val idealDuration = Routing.getDuration(idealPath)
        val passenger = Passenger(nextPassengerId, rr.origin, rr.destination, rr.pickupDeadline, idealDuration, rr.stretchTolerance)
        nextPassengerId += 1

        // first, prune cars that are too far away to have any chance to pickup this passenger:
        val candidateCars = rr.pickupDeadline match {
          case Some(deadline) => cars.par.filter(car => Optimization.isApproximatelyFeasible(car.position, rr.origin, rr.time, deadline))
          case _ => cars.par
        }

        // for the remaining candidate cars, compute their best itinerary:
        val itinerariesForCars = candidateCars.flatMap{ car =>
          val itineraryOpt = Optimization.getBestItineraryForCar(car.passengers + passenger, car.position, rr.time, car.capacity)
          itineraryOpt.map{ itinerary =>
            val extraTime = itinerary.duration.minus(car.itinerary.duration)
            (car, itinerary, extraTime)
          }
        }

        sumNrCarsConsidered += itinerariesForCars.size

        if (itinerariesForCars.isEmpty) {
          // spawn a new car
          val itineraryOpt = Optimization.getBestItineraryForCar(Set(passenger), passenger.origin, rr.time, defaultCarCapacity)
          itineraryOpt match {
            case Some(itinerary) =>
              // we need to update the passenger with pickup and dropoff times, hence we call the "with passenger" method
              // TODO: write an apply method in Car object accepting itinerary and passenger set?
              val newCar = Car(passenger.origin, defaultCarCapacity, itinerary, Set.empty).withPassenger(passenger, itinerary)
              cars.add(newCar)
              nrValidDemands += 1
            case None =>  // this request is not feasible, we do nothing
              nrInvalidDemands += 1
          }
        } else {
          val bestCombination = itinerariesForCars.minBy(_._3.getMillis)
          val car = bestCombination._1
          val updatedCar = car.withPassenger(passenger, bestCombination._2)

          // remove old instance of the car from our car collection, and add the new one
          cars.remove(car)  // leave car for garbage collection
          cars.add(updatedCar)
          nrValidDemands += 1
        }

      case _ =>
        // no path exists, do nothing
        nrInvalidDemands += 1
    }
  }

  override def setCurrentTime(newCurrentTime: DateTime): Unit = {
    val updates = cars.map(_.update(newCurrentTime))
    cars = updates.map(_._1)

    // update metrics
    totalValidDemands += nrValidDemands

    val updatesList = updates.toList  // need the toList to not collapse numbers ;)
    nrPickups = updatesList.map(_._2).sum
    nrDropoffs = updatesList.map(_._3).sum

    val now = new DateTime()
    lastSlotComputeTime = new Duration(now.getMillis -  slotStartWallClockTime.getMillis)
    slotStartWallClockTime = now
  }

  override def getStats: DispatchingStats = {
    val stats = DispatchingStats(cars.size, totalValidDemands, nrValidDemands,
      nrInvalidDemands, nrDeniedDemands, nrPickups, nrDropoffs, avgStretch,
      sumNrCarsConsidered / nrValidDemands.toDouble,
      cars.toList.map(_.passengers.count(_.pickedUp)).sum / cars.size.toDouble,
      lastSlotComputeTime, Routing.getAndResetCacheStats)

    nrValidDemands = 0
    nrInvalidDemands = 0
    nrDeniedDemands = 0
    sumNrCarsConsidered = 0

    stats
  }
}
