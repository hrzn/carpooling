package utils

import scala.collection.concurrent.TrieMap

case class CacheStats(cacheSize: Int, nrHits: Long, nrMisses: Long, nrRejectedPuts: Long)

/**
  * A trivial in-memory thread-safe cache, which keeps some statistics about
  * number of hits and misses.
  *
  * @param maxSize maximum number of items we allow the cache to store
  * @tparam K type of keys
  * @tparam V type of values
  */
class Cache[K, V](maxSize: Int = 100000) {
  private val cache = new TrieMap[K, V]
  private var cacheSize: Int = 0
  private var nrHits: Long = 0L
  private var nrMisses: Long = 0L
  private var nrRejectedPuts: Long = 0L

  def get(key: K): Option[V] = {
    val res = cache.get(key)
    if (res.isDefined) nrHits += 1
    else nrMisses += 1
    res
  }

  def put(key: K, value: V): Unit = {
    if (cacheSize < maxSize){
      if (!cache.contains(key)) cacheSize += 1
      cache.put(key, value)
    } else nrRejectedPuts += 1
  }

  def getAndResetStats: CacheStats = {
    val res = CacheStats(cacheSize, nrHits, nrMisses, nrRejectedPuts)
    nrHits = 0
    nrMisses = 0
    nrRejectedPuts = 0
    res
  }
}
