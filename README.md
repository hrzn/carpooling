# Carpooling Simulator

## How to use
* Download NYC yellow cab data .csv files from <http://www.nyc.gov/html/tlc/html/about/trip_record_data.shtml>.
Take only files containing data before June 2016 (included), as later files do not contain coordinates of demands.

* Download OpenStreetMap data for NYC region (take `.osm.pbf` format). For example from here 
<http://download.geofabrik.de/north-america/us/new-york-latest.osm.pbf>

* Then, indicate the folders containing the NYC cab data and the OpenStreetMap data in the config file:
    * Option A: replace the placeholder config entries in `src/main/resources/application.conf`
    * Option B: create your own config file, say, `conf/my.conf`, 
    and set the entries `base.data.dir` and `routing.osm.file` accordingly.
    
* In addition, create a folder somewhere for graphhopper data, for instance `data/graphhopper`, and
indicate the path in your config file.

* Install [Scala](https://www.scala-lang.org/) with SBT.

* To launch the simulation:
    * If you selected option A above: `sbt run`
    * If you selected option B above: `sbt run -Dconfig.file=conf/my.conf`