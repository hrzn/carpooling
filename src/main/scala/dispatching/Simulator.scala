package dispatching

import com.typesafe.config.ConfigFactory
import dispatching.dispatchers.SimpleDispatcher
import org.joda.time.LocalDate

object Simulator extends App {
  val conf = ConfigFactory.load()

  val timeSlotDurationMs: Long = (conf.getDouble("time.slot.duration.seconds") * 1e3).toLong

  // create the dispatcher
  val dispatcher = new SimpleDispatcher()

  // get demands for a day
  // TODO: put day in config
  val day = new LocalDate(2016, 6, 1)
  println("loading demands...")
  val demands = Demands.getDayYellow(day)
  println("done.")

  // init time
  var currentTime = demands.head.time
  dispatcher.setCurrentTime(currentTime)

  // iterate through demands and update time slots when needed
  demands.foreach{ demand =>
    // do we need to update time slot?
    if (demand.time.getMillis - currentTime.getMillis >= timeSlotDurationMs) {
      currentTime = demand.time
      dispatcher.setCurrentTime(currentTime)

      val stats = dispatcher.getStats
      println(s"${currentTime.toString("HH:mm:ss")}: $stats")
    }

    // treat demand:
    dispatcher.handleRequest(demand)
  }
}
