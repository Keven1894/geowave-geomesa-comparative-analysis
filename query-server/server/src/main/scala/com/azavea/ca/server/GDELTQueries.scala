package com.azavea.ca.server

import com.azavea.ca.core._
import com.azavea.ca.server.results._
import com.azavea.ca.server.geomesa.connection.GeoMesaConnection
import com.azavea.ca.server.geowave.connection.GeoWaveConnection
import com.azavea.ca.server.geowave.GeoWaveQuerier

import akka.http.scaladsl.server.Directives._
import de.heikoseeberger.akkahttpcirce._
import io.circe.generic.auto._
import org.geotools.data._
import org.geotools.filter.text.ecql.ECQL
import org.opengis.filter.Filter

import scala.concurrent.Future

object GDELTQueries
    extends BaseService
    with CAQueryUtils
    with CirceSupport
    with AkkaSystem.LoggerExecutor {

  val gwTableName = "geowave.gdelt"
  val gwFeatureTypeName = "gdelt-event"

  val gmTableName = "geomesa.gdelt"
  val gmFeatureTypeName = "gdelt-event"

  def routes =
    pathPrefix("gdelt") {
      pathPrefix("ping") {
        pathEndOrSingleSlash {
          get {
            complete { Future { "pong" } } }
        }
      } ~
      pathPrefix("reset") {
        pathEndOrSingleSlash {
          get {
            complete { Future { resetDataStores() ; "done" } } }
        }
      } ~
      pathPrefix("spatiotemporal") {
        pathPrefix("in-france-region-bbox-7-days") {
          val queryName = "GDELT-IN-FRANCE-REGION-BBOX-7-DAYS"

          pathEndOrSingleSlash {
            get {
              parameters('test ?, 'loose ?, 'wOrm ? "both") { (isTestOpt, isLooseOpt, waveOrMesa) =>
                val isTest = checkIfIsTest(isTestOpt)
                complete {
                  Future {
                    val tq = TimeQuery("2001-01-01T00:00:00", "2001-01-07T00:00:00")

                    val query = ECQL.toFilter(CQLUtils.toBBOXquery("the_geom", France.regions.head.envelope) + " AND " + tq.toCQL("day"))

                    val (mesa, wave) =
                      if(waveOrMesa == "wm") {
                        val mesa: TestResult = captureGeoMesaQuery(query, checkIfIsLoose(isLooseOpt))
                        val wave: TestResult = captureGeoWaveQuery(query)
                        (Some(mesa), Some(wave))
                      } else if (waveOrMesa == "w") {
                        val wave: TestResult = captureGeoWaveQuery(query)
                        (None, Some(wave))
                      } else {
                        val mesa: TestResult = captureGeoMesaQuery(query, checkIfIsLoose(isLooseOpt))
                        (Some(mesa), None)
                      }

                    val result = RunResult(s"${queryName}${looseSuffix(isLooseOpt)}", mesa, wave, isTest)
                    DynamoDB.saveResult(result)
                    result
                  }
                }
              }
            }
          }
        } ~
        pathPrefix("in-france-bbox-six-months") {
          val queryName = "GDELT-IN-FRANCE-BBOX-SIX-MONTHS"

          pathEndOrSingleSlash {
            get {
              parameters('year ? "all", 'test ?, 'loose ?) { (year, isTestOpt, isLooseOpt) =>
                val isTest = checkIfIsTest(isTestOpt)
                complete {
                  Future {
                    val timeQueries = {
                      val years: Seq[Int] =
                        if(year != "all") {
                          Seq(year.toInt)
                        } else {
                          (1980 to 2015)
                        }
                      (for(y <- years) yield {
                        Seq(
                          (s"$y-firsthalf", TimeQuery(s"$y-01-01T00:00:00", s"$y-06-01T00:00:00")),
                          (s"$y-lasthalf", TimeQuery(s"$y-06-01T00:00:00", s"${y+1}-01-01T00:00:00"))
                        )
                      }).flatten
                    }

                    (for((suffix, tq) <- timeQueries) yield {
                      val query = ECQL.toFilter(France.CQL.inBoundingBox + " AND " + tq.toCQL("day"))

                      val mesa: TestResult = captureGeoMesaQuery(query, checkIfIsLoose(isLooseOpt))
                      val wave: TestResult = captureGeoWaveQuery(query)

                      val result = RunResult(s"${queryName}-${suffix}${looseSuffix(isLooseOpt)}", mesa, wave, isTest)
                      DynamoDB.saveResult(result)
                      result
                    }).toArray
                  }
                }
              }
            }
          }
        } ~
        pathPrefix("in-france-six-months") {
          val queryName = "GDELT-IN-FRANCE-SIX-MONTHS"

          pathEndOrSingleSlash {
            get {
              parameters('year ? "all", 'test ?) { (year, isTestOpt) =>
                val isTest = checkIfIsTest(isTestOpt)
                complete {
                  Future {
                    val timeQueries = {
                      val years: Seq[Int] =
                        if(year != "all") {
                          Seq(year.toInt)
                        } else {
                          (1980 to 2015)
                        }
                      (for(y <- years) yield {
                        Seq(
                          (s"$y-firsthalf", TimeQuery(s"$y-01-01T00:00:00", s"$y-06-01T00:00:00")),
                          (s"$y-lasthalf", TimeQuery(s"$y-06-01T00:00:00", s"${y+1}-01-01T00:00:00"))
                        )
                      }).flatten
                    }

                    (for((suffix, tq) <- timeQueries) yield {
                      val query = ECQL.toFilter(France.CQL.inFrance + " AND " + tq.toCQL("day"))

                      val mesa: TestResult = captureGeoMesaQuery(query)
                      val wave: TestResult = captureGeoWaveQuery(query)

                      val result = RunResult(s"${queryName}-${suffix}", mesa, wave, isTest)
                      DynamoDB.saveResult(result)
                      result
                    }).toArray
                  }
                }
              }
            }
          }
        } ~
        pathPrefix("in-france-bbox-one-month") {
          val queryName = "GDELT-IN-FRANCE-BBOX-ONE-MONTH"

          pathEndOrSingleSlash {
            get {
              parameters('year ? "all", 'test ?, 'loose ?) { (year, isTestOpt, isLooseOpt) =>
                val isTest = checkIfIsTest(isTestOpt)
                complete {
                  Future {
                    val timeQueries = {
                      val years: Seq[Int] =
                        if(year != "all") {
                          Seq(year.toInt)
                        } else {
                          (1980 to 2015)
                        }
                      (for(y <- years) yield {
                        Seq(
                          (s"$y-JAN", TimeQuery(s"$y-01-01T00:00:00", s"$y-02-01T00:00:00")),
                          (s"$y-FEB", TimeQuery(s"$y-02-01T00:00:00", s"$y-03-01T00:00:00")),
                          (s"$y-MAR", TimeQuery(s"$y-03-01T00:00:00", s"$y-04-01T00:00:00")),
                          (s"$y-APR", TimeQuery(s"$y-04-01T00:00:00", s"$y-05-01T00:00:00")),
                          (s"$y-MAY", TimeQuery(s"$y-05-01T00:00:00", s"$y-06-01T00:00:00")),
                          (s"$y-JUN", TimeQuery(s"$y-06-01T00:00:00", s"$y-07-01T00:00:00")),
                          (s"$y-JUL", TimeQuery(s"$y-07-01T00:00:00", s"$y-08-01T00:00:00")),
                          (s"$y-AUG", TimeQuery(s"$y-08-01T00:00:00", s"$y-09-01T00:00:00")),
                          (s"$y-SEP", TimeQuery(s"$y-09-01T00:00:00", s"$y-10-01T00:00:00")),
                          (s"$y-OCT", TimeQuery(s"$y-10-01T00:00:00", s"$y-11-01T00:00:00")),
                          (s"$y-NOV", TimeQuery(s"$y-11-01T00:00:00", s"$y-12-01T00:00:00")),
                          (s"$y-DEC", TimeQuery(s"$y-12-01T00:00:00", s"${y+1}-01-01T00:00:00"))
                        )
                      }).flatten
                    }

                    (for((suffix, tq) <- timeQueries) yield {
                      val query = ECQL.toFilter(France.CQL.inBoundingBox + " AND " + tq.toCQL("day"))

                      val mesa: TestResult = captureGeoMesaQuery(query, checkIfIsLoose(isLooseOpt))
                      val wave: TestResult = captureGeoWaveQuery(query)

                      val result = RunResult(s"${queryName}-${suffix}${looseSuffix(isLooseOpt)}", mesa, wave, isTest)
                      DynamoDB.saveResult(result)
                      result
                    }).toArray
                  }
                }
              }
            }
          }
        } ~
        pathPrefix("in-france-one-month") {
          val queryName = "GDELT-IN-FRANCE-ONE-MONTH"

          pathEndOrSingleSlash {
            get {
              parameters('year ? "all", 'test ?) { (year, isTestOpt) =>
                val isTest = checkIfIsTest(isTestOpt)
                complete {
                  Future {
                    val timeQueries = {
                      val years: Seq[Int] =
                        if(year != "all") {
                          Seq(year.toInt)
                        } else {
                          (1980 to 2015)
                        }
                      (for(y <- years) yield {
                        Seq(
                          (s"$y-JAN", TimeQuery(s"$y-01-01T00:00:00", s"$y-02-01T00:00:00")),
                          (s"$y-FEB", TimeQuery(s"$y-02-01T00:00:00", s"$y-03-01T00:00:00")),
                          (s"$y-MAR", TimeQuery(s"$y-03-01T00:00:00", s"$y-04-01T00:00:00")),
                          (s"$y-APR", TimeQuery(s"$y-04-01T00:00:00", s"$y-05-01T00:00:00")),
                          (s"$y-MAY", TimeQuery(s"$y-05-01T00:00:00", s"$y-06-01T00:00:00")),
                          (s"$y-JUN", TimeQuery(s"$y-06-01T00:00:00", s"$y-07-01T00:00:00")),
                          (s"$y-JUL", TimeQuery(s"$y-07-01T00:00:00", s"$y-08-01T00:00:00")),
                          (s"$y-AUG", TimeQuery(s"$y-08-01T00:00:00", s"$y-09-01T00:00:00")),
                          (s"$y-SEP", TimeQuery(s"$y-09-01T00:00:00", s"$y-10-01T00:00:00")),
                          (s"$y-OCT", TimeQuery(s"$y-10-01T00:00:00", s"$y-11-01T00:00:00")),
                          (s"$y-NOV", TimeQuery(s"$y-11-01T00:00:00", s"$y-12-01T00:00:00")),
                          (s"$y-DEC", TimeQuery(s"$y-12-01T00:00:00", s"${y+1}-01-01T00:00:00"))
                        )
                      }).flatten
                    }

                    (for((suffix, tq) <- timeQueries) yield {
                      val query = ECQL.toFilter(France.CQL.inFrance + " AND " + tq.toCQL("day"))

                      val mesa: TestResult = captureGeoMesaQuery(query)
                      val wave: TestResult = captureGeoWaveQuery(query)

                      val result = RunResult(s"${queryName}-${suffix}", mesa, wave, isTest)
                      DynamoDB.saveResult(result)
                      result
                    }).toArray
                  }
                }
              }
            }
          }
        }
      }
    }
}
