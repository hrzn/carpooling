package dispatching

import com.graphhopper.PathWrapper
import dispatching.CourseType.CourseType
import org.joda.time.{DateTime, Duration}

object CourseType extends Enumeration {
  type CourseType = Value
  val Dropoff, Pickup = Value
}

/**
  * A segment of a course, representing a trajectory until a dropoff or a pickup, and some meta-information
  * (used to represent sub-paths in the simulation).
  * @param passengerId the ID of the passenger concerned by the sub-path
  * @param origin the point where this segment is starting from
  * @param destination the point where this segment is leading to
  * @param courseType the end goal of this course segment (dropoff or pickup)
  * @param startTime time at which the segment starts
  * @param duration segment duration
  */
case class CourseSegment(passengerId: Int, origin: GPSPoint, destination: GPSPoint,
                         courseType: CourseType, startTime: DateTime, duration: Duration) {
  lazy val path: Option[PathWrapper] = Routing.getPath(origin, destination)

  def getPositionAtTime(time: DateTime): GPSPoint = {
    if (time.getMillis < startTime.getMillis) origin
    else if (time.getMillis > startTime.plus(duration).getMillis) destination
    else {
      // else use the path
      path match {
        case Some(p) =>
          val frac: Double = (time.getMillis - startTime.getMillis) / duration.getMillis
          val idx = Math.min((frac * p.getPoints.getSize).toInt, p.getPoints.getSize - 1)
          GPSPoint(p.getPoints.getLat(idx), p.getPoints.getLon(idx))
        case _ => throw new Exception("Segment with no path.")
      }
    }
  }

  override def toString: String = (if (courseType == CourseType.Pickup) "pickup" else "dropoff") + " " + passengerId.toString
}

/**
  * An itinerary, represented as a time-sorted list of course segments
  */
case class Itinerary(segments: List[CourseSegment]) {
  require(segments.nonEmpty)
  val duration: Duration = segments.map(_.duration).reduce(_ plus _)

  def startTime: DateTime = segments.head.startTime

  def getPositionAtTime(time: DateTime): GPSPoint = {
    val segment = getSegmentAtTime(time)
    segment.getPositionAtTime(time)
  }

  private def getSegmentAtTime(time: DateTime): CourseSegment = {
    if (time.getMillis <= startTime.getMillis) segments.head
    else {
      def loop(currentTime: DateTime, remainingSegments: List[CourseSegment]): CourseSegment = {
        remainingSegments match {
          case s :: Nil => s
          case s :: segs =>
            val newTime = segs.head.startTime
            if (currentTime.getMillis <= newTime.getMillis) s
            else loop(newTime, segs.tail)
          case _ => throw new Exception("This shouldn't happen")
        }
      }
      loop(segments.head.startTime, segments)
    }
  }

  override def toString: String = "[" + segments.mkString(", ") + "]"
}
