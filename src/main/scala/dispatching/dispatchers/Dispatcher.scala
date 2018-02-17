package dispatching.dispatchers

import com.typesafe.config.ConfigFactory
import dispatching.RideRequest
import org.joda.time.{DateTime, Duration}
import utils.CacheStats

/**
  * Encapsulates what we want to measure.
  * It contains both some "per-slot" stats (which are computed for the last slot at the beginning of a new slot),
  * and "all-time" stats (such as nr of cars), which are cumulative since the creation of the Dispatcher.
  */
case class DispatchingStats(nrCars: Int, nrDemands: Int, nrValidDemandsLastSlot: Int, nrInvalidDemandsLastSlot: Int,
                            nrDeniedDemandsLastSlot: Int, nrPickupsLastSlot: Int, nrDropoffsLastSlot: Int,
                            averageStretchLastSlot: Double, avgNrCarsConsideredPerDemandLastSlot: Double,
                            avgNrOfPassengersPerCar: Double, slotComputeTime: Duration, cacheStats: CacheStats) {

  override def toString: String = s"(cars=$nrCars, total_served=$nrDemands, valid=$nrValidDemandsLastSlot, " +
    s"invalid=$nrInvalidDemandsLastSlot, denied=$nrDeniedDemandsLastSlot, pickups=$nrPickupsLastSlot, " +
    s"dropoffs=$nrDropoffsLastSlot, psgr/car=$avgNrOfPassengersPerCar, time=${slotComputeTime.getMillis/1e3}s., " +
    s"cache_size=${cacheStats.cacheSize}, nr_hits=${cacheStats.nrHits}, nr_misses=${cacheStats.nrMisses}"
}

/**
  * This is where dispatching logic will be implemented (irrespective of whether it's called
  * from a simulation or live context)
  */
trait Dispatcher {
  protected val conf = ConfigFactory.load()

  /**
    * Handle a new ride request
    */
  def handleRequest(rr: RideRequest): Unit

  /**
    * Call this method to update the state of all cars (positions, pickups, dropoffs).
    * This method is the "tick" of the state. It must be called often enough so that
    * the whole state can be considered static between two calls. For instance, this
    * method might be called every 10 seconds or so. In addition, this method MUST be called
    * at least once before the first call to handleRequest(), so that the dispatcher can know
    * what is the current time at the beginning.
    *
    * This method should also reset the slot statistics.
    */
  def setCurrentTime(newCurrentTime: DateTime): Unit

  /**
    * Return the stats (both for current time slot and global)
    */
  def getStats: DispatchingStats
}
