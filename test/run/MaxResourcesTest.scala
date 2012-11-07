package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._
import org.w3.vs.http.Http._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.w3.util.Util._
import org.w3.banana._

class MaxResourcesTest extends RunTestHelper with TestKitHelper {

  val maxResources = 100

  val strategy =
    Strategy(
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = maxResources,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorsConfiguration = Map.empty)
  
  val job = Job(name = "@@", strategy = strategy, creator = userTest.id, organization = organizationTest.id)
  
  val servers = Seq(Webserver(9001, Website.tree(4).toServlet))

  "shoudldn't access more that 100 resources" in {

    (for {
      a <- Organization.save(organizationTest)
      b <- Job.save(job)
    } yield ()).getOrFail(5.seconds)

    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

    val (orgId, jobId, runId) = job.run().getOrFail()

    job.listen(testActor)

    fishForMessagePF(3.seconds) {
      case UpdateData(_, _, activity) if activity == Idle => {
        val rrs = ResourceResponse.bananaGetFor(orgId, jobId, runId).getOrFail()
        rrs must have size (maxResources)
      }
    }

  }
  
}
