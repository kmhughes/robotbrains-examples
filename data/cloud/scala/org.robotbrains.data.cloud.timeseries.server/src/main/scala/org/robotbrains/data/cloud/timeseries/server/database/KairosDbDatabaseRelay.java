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

package org.robotbrains.data.cloud.timeseries.server.database;

import io.smartspaces.SmartSpacesException;

import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.DataPoint;
import org.kairosdb.client.builder.MetricBuilder;
import org.kairosdb.client.builder.QueryBuilder;
import org.kairosdb.client.response.GetResponse;
import org.kairosdb.client.response.Queries;
import org.kairosdb.client.response.QueryResponse;
import org.kairosdb.client.response.Response;
import org.kairosdb.client.response.Results;
import org.robotbrains.data.cloud.timeseries.server.comm.remote.mqtt.RemoteDataRelay;
import org.robotbrains.data.cloud.timeseries.server.data.SensorData;
import org.robotbrains.data.cloud.timeseries.server.data.SensorDataQuery;
import org.robotbrains.data.cloud.timeseries.server.data.SensorDataSample;

import rx.Subscription;

/**
 * Data database relay that uses KairosDB as a backend.
 * 
 * @author Keith M. Hughes
 */
public class KairosDbDatabaseRelay implements DatabaseRelay {

  /**
   * The configuration name for the hostname for the machine where KairosDB is
   * running.
   */
  public static final String CONFIGURATION_DATABASE_KAIROSDB_CONNECTION_HOST =
      "database.kairosdb.connection.host";

  /**
   * The configuration name for the KairosDB port on the machine where KairosDB
   * is running.
   */
  public static final String CONFIGURATION_DATABASE_KAIROSDB_CONNECTION_PORT =
      "database.kairosdb.connection.port";

  /**
   * The separator character between components in the event key.
   */
  private static final String EVENT_KEY_COMPONENT_SEPARATOR = "_";

  /**
   * The URL for connecting to the Kairos database.
   */

  /**
   * The data relay supplying data to this database relay.
   */
  private RemoteDataRelay remoteDataRelay;

  /**
   * The configuration for the relay.
   */
  private Map<String, String> configuration;

  /**
   * The client for communicating with KairosDB.
   */
  private HttpClient kairosdbClient;

  /**
   * The event subscription for receiving data from the remote data relay.
   */
  private Subscription remoteDataRelaySubscription;

  /**
   * The logger.
   */
  private Logger log;

  /**
   * Construct a new relay.
   * 
   * @param remoteDataRelay
   *          the remote data relay
   * @param configuration
   *          configuration for the relay
   * @param log
   *          the log to use
   */
  public KairosDbDatabaseRelay(RemoteDataRelay remoteDataRelay, Map<String, String> configuration,
      Logger log) {
    this.remoteDataRelay = remoteDataRelay;
    this.configuration = configuration;
    this.log = log;
  }

  @Override
  public void startup() {
    try {
      String kairosConnectionUrl =
          String.format("http://%s:%s",
              configuration.get(CONFIGURATION_DATABASE_KAIROSDB_CONNECTION_HOST),
              configuration.get(CONFIGURATION_DATABASE_KAIROSDB_CONNECTION_PORT));
      kairosdbClient = new HttpClient(kairosConnectionUrl);

      testQueryDatabase();

      remoteDataRelaySubscription =
          remoteDataRelay.getSensorDataObservable().subscribe(
              sensorData -> processIncomingSensorData(sensorData));

      log.info("Database relay for KairosDB at %s running", kairosConnectionUrl);
    } catch (Throwable e) {
      throw SmartSpacesException.newFormattedException(e,
          "Could not start up KairosDB database relay");
    }
  }

  @Override
  public void shutdown() {
    remoteDataRelaySubscription.unsubscribe();

    try {
      kairosdbClient.shutdown();
    } catch (Throwable e) {
      throw SmartSpacesException.newFormattedException(e,
          "Could not shut down KairosDB database relay");
    }
  }

  @Override
  public SensorData querySensorData(SensorDataQuery query) throws Exception {
    SensorData data = new SensorData(query.source(), query.sensingUnit());

    performMetricQuery(query, data);

    return data;
  }

  /**
   * Process sensor data that has come in.
   * 
   * @param sensorData
   *          the sensor data
   */
  private void processIncomingSensorData(SensorData sensorData) {
    String eventKeyPrefix = createEventKeyPrefix(sensorData.source(), sensorData.sensingUnit());

    for (SensorDataSample sample : sensorData.samples()) {
      String eventKey = eventKeyPrefix + sample.sensor();
      MetricBuilder builder = MetricBuilder.getInstance();

      builder.addMetric(eventKey).addTag("sensor", "snapshot")
          .addDataPoint(sample.timestamp(), sample.value());

      try {
        Response pushResponse = kairosdbClient.pushMetrics(builder);

        // This should come in as a 204.
        log.debug("Push Response Code =" + pushResponse.getStatusCode());
      } catch (Throwable e) {
        log.error(String.format("Failed while writing data point %s", eventKey), e);
      }
    }
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
  private String createEventKeyPrefix(String source, String sensingUnit) {
    return source + EVENT_KEY_COMPONENT_SEPARATOR + sensingUnit + EVENT_KEY_COMPONENT_SEPARATOR;
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
  private String createEventKey(String source, String sensingUnit, String sensor) {
    return createEventKeyPrefix(source, sensingUnit) + sensor;
  }

  /**
   * Perform some test queries on the database.
   * 
   * @throws Exception
   *           something bad happened when querying the database
   */
  private void testQueryDatabase() throws Exception {
    GetResponse metricResponse = kairosdbClient.getMetricNames();

    log.info("Metric Response Code =" + metricResponse.getStatusCode());
    for (String name : metricResponse.getResults()) {
      log.info(name);
    }
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
  private void performMetricQuery(SensorDataQuery query, SensorData data) throws Exception {
    String sensor = query.sensor();
    String metricName = createEventKey(query.source(), query.sensingUnit(), sensor);

    log.info("Getting metric %s from time %s to time %s", metricName, query.startDate(),
        query.endDate());

    QueryBuilder builder = QueryBuilder.getInstance();
    builder.setStart(query.startDate().toDate()).setEnd(query.endDate().toDate());

    builder.addMetric(metricName);

    QueryResponse response = kairosdbClient.query(builder);
    for (Queries queries : response.getQueries()) {
      for (Results results : queries.getResults()) {
        for (DataPoint dataPoint : results.getDataPoints()) {
          SensorDataSample sample =
              new SensorDataSample(sensor, dataPoint.doubleValue(), dataPoint.getTimestamp());

          data.addSample(sample);
        }
      }
    }
  }
}
