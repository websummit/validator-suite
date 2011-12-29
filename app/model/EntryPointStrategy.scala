package org.w3.vs.model

import org.w3.util._

import java.util.UUID

/** A [[org.w3.vs.model.ActionStrategy]] made of an entry point URL and a distance from it.
  * 
  * @param uuid
  * @param name
  * @param delay
  * @param entrypoint the entry point defining this [[org.w3.vs.model.ExplorationStrategy]]
  * @param distance the maximum distance the crawler should move away from `entrypoint`
  * @param filter a filter to be applied to the URLs being discovered
  */
case class EntryPointStrategy(
    uuid: UUID,
    name: String,
    entrypoint: URL,
    distance: Int,
    linkCheck: Boolean,
    filter: Filter)
extends Strategy {
  
  def seedURLs: Iterable[URL] =
    Seq(entrypoint)
    
  val authorityToObserve: Authority = entrypoint.authority
    
  def shouldObserve(url: URL): Boolean =
    authorityToObserve == url.authority
  
  def fetch(url: URL, distance: Int): FetchAction =
    if ((url.getAuthority == entrypoint.getAuthority) &&
        (distance <= this.distance))
      FetchGET
    else if (linkCheck)
      FetchHEAD
    else
      FetchNothing
}