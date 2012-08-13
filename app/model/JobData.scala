package org.w3.vs.model

import org.joda.time.DateTime
import scala.math._

case class JobData (
    runId: RunId,
    jobId: JobId,
    resources: Int,
    errors: Int,
    warnings: Int,
    createdAt: DateTime,
    completedAt: Option[DateTime]) {
  
  def health: Int = JobData.health(resources, errors, warnings)
  
  def isCompleted: Boolean = completedAt.isDefined

}

object JobData {

  def apply(runVO: RunVO): JobData = {
    null // JobData(runVO.id, runVO.jobId, runVO.resources, runVO.errors, runVO.warnings, runVO.createdAt, runVO.completedAt)
  }
  
  def health(resources: Int, errors: Int, warnings: Int): Int = {
    if (resources == 0) 0
    else {
      val errorAverage = errors.toDouble / resources.toDouble
      val h = (exp(log(0.5) / 10 * errorAverage) * 100).toInt
      max(1, h)
    }
  }

}
