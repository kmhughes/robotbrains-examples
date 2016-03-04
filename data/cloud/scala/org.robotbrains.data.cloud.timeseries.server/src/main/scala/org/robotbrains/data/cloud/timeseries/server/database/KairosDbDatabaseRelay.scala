/*
 * Copyright (C) 2015 Keith M. Hughes.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.robotbrains.data.cloud.timeseries.server.database

import io.smartspaces.SmartSpacesException

import java.util.Map
import scala.collection.JavaConversions._
import org.apache.logging.log4j.Logger
import org.kairosdb.client.HttpClient
import org.kairosdb.client.builder.DataPoint
import org.kairosdb.client.builder.MetricBuilder
import org.kairosdb.client.builder.QueryBuilder
import org.kairosdb.client.response.GetResponse
import org.kairosdb.client.response.Queries
import org.kairosdb.client.response.QueryResponse
import org.kairosdb.client.response.Response
import org.kairosdb.client.response.Results
import org.robotbrains.data.cloud.timeseries.server.comm.remote.mqtt.RemoteDataRelay
import org.robotbrains.data.cloud.timeseries.server.data.SensorData
import org.robotbrains.data.cloud.timeseries.server.data.SensorDataQuery
import org.robotbrains.data.cloud.timeseries.server.data.SensorDataSample

import rx.lang.scala.Subscription;

/**
 * Data database relay that uses KairosDB as a backend.
 *
 * @author Keith M. Hughes
 */
class KairosDbDatabaseRelay(remoteDataRelay: RemoteDataRelay, configuration: Map[String, String],
    log: Logger) extends DatabaseRelay {

  /**
   * The configuration name for the hostname for the machine where KairosDB is
   * running.
   */
  val CONFIGURATION_DATABASE_KAIROSDB_CONNECTION_HOST = "database.kairosdb.connection.host"

  /**
   * The configuration name for the KairosDB port on the machine where KairosDB
   * is running.
   */
  val CONFIGURATION_DATABASE_KAIROSDB_CONNECTION_PORT = "database.kairosdb.connection.port"

  /**
   * The separator character between components in the event key.
   */
  val EVENT_KEY_COMPONENT_SEPARATOR = "_"

  /**
   * The URL for connecting to the Kairos database.
   */

  /**
   * The client for communicating with KairosDB.
   */
  var kairosdbClient: HttpClient = null

  /**
   * The event subscription for receiving data from the remote data relay.
   */
  var remoteDataRelaySubscription: Subscription = null

  override def startup(): Unit = {
    try {
//      val kairosConnectionUrl =
//        String.format("http://%s:%s",
//          configuration.get(CONFIGURATION_DATABASE_KAIROSDB_CONNECTION_HOST),
//          configuration.get(CONFIGURATION_DATABASE_KAIROSDB_CONNECTION_PORT))
//      kairosdbClient = new HttpClient(kairosConnectionUrl)
//
//      testQueryDatabase()

      remoteDataRelaySubscription =
        remoteDataRelay.getSensorDataObservable().subscribe(
          sensorData => processIncomingSensorData(sensorData))

 //     log.info("Database relay for KairosDB at %s running", kairosConnectionUrl)
    } catch {
      case e: Throwable => throw SmartSpacesException.newFormattedException(e,
        "Could not start up KairosDB database relay")
    }
  }

  override def shutdown(): Unit = {
    remoteDataRelaySubscription.unsubscribe()

    try {
      kairosdbClient.shutdown()
    } catch {
      case e: Throwable => throw SmartSpacesException.newFormattedException(e,
        "Could not shut down KairosDB database relay");
    }
  }

  override def querySensorData(query: SensorDataQuery): SensorData = {
    val data = new SensorData(query.source, query.sensingUnit);

    performMetricQuery(query, data);

    return data;
  }

  /**
   * Process sensor data that has come in.
   *
   * @param sensorData
   *          the sensor data
   */
  def processIncomingSensorData(sensorData: SensorData): Unit = {
    val eventKeyPrefix = createEventKeyPrefix(sensorData.source, sensorData.sensingUnit)

    sensorData.samples.foreach(sample => {
      val eventKey = eventKeyPrefix + sample.sensor
      val builder = MetricBuilder.getInstance

      builder.addMetric(eventKey).addTag("sensor", "snapshot")
        .addDataPoint(sample.timestamp, sample.value)

      try {
        val pushResponse = kairosdbClient.pushMetrics(builder)

        // This should come in as a 204.
        log.debug("Push Response Code =" + pushResponse.getStatusCode())
      } catch {
        case e: Throwable =>
          log.error(String.format("Failed while writing data point %s", eventKey), e);
      }
    })
  }

  /**
   * Create the prefix for an event key.
   *
   * @param source
   *          the source of the event
   * @param sensingUnit
   *          the sensing unit at the source
   *
   * @return the event key prefix, just append the sensor to the end
   */
  def createEventKeyPrefix(source: String, sensingUnit: String): String = {
    return source + EVENT_KEY_COMPONENT_SEPARATOR + sensingUnit + EVENT_KEY_COMPONENT_SEPARATOR
  }

  /**
   * Create an event key.
   *
   * @param source
   *          the source of the event
   * @param sensingUnit
   *          the sensing unit at the source
   * @param sensor
   *          the sensor on the sensing unit
   *
   * @return the event key
   */
  def createEventKey(source: String, sensingUnit: String, sensor: String): String = {
    return createEventKeyPrefix(source, sensingUnit) + sensor
  }

  /**
   * Perform some test queries on the database.
   *
   * @throws Exception
   *           something bad happened when querying the database
   */
  def testQueryDatabase(): Unit = {
    val metricResponse = kairosdbClient.getMetricNames()

    log.info("Metric Response Code =" + metricResponse.getStatusCode())
    metricResponse.getResults().foreach(name => log.info(name))
  }

  /**
   * Perform a metric query on the database.
   *
   * @param metricName
   *          the name of the metric
   * @param startTime
   *          the start of the time range for the query
   * @param endTime
   *          the start of the time range for the query
   *
   * @throws Exception
   *           something bad happened when querying the database
   */
  def performMetricQuery(query: SensorDataQuery, data: SensorData): Unit = {
    val sensor = query.sensor;
    val metricName = createEventKey(query.source, query.sensingUnit, sensor)

    log.info("Getting metric %s from time %s to time %s", metricName, query.startDate,
      query.endDate)

    val builder = QueryBuilder.getInstance()
    builder.setStart(query.startDate.toDate).setEnd(query.endDate.toDate)

    builder.addMetric(metricName);

    val response = kairosdbClient.query(builder)
    for (queries <- response.getQueries()) {
      for (results <- queries.getResults()) {
        for (dataPoint <- results.getDataPoints()) {
          val sample =
            new SensorDataSample(sensor, dataPoint.doubleValue, dataPoint.getTimestamp)

          data.addSample(sample)
        }
      }
    }
  }
}
