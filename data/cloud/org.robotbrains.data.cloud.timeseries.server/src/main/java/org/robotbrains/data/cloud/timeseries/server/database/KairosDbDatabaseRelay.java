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

import interactivespaces.InteractiveSpacesException;
import interactivespaces.util.resource.ManagedResource;

import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.MetricBuilder;
import org.kairosdb.client.builder.QueryBuilder;
import org.kairosdb.client.response.GetResponse;
import org.kairosdb.client.response.Queries;
import org.kairosdb.client.response.QueryResponse;
import org.kairosdb.client.response.Response;
import org.kairosdb.client.response.Results;
import org.robotbrains.data.cloud.timeseries.server.comm.remote.mqtt.RemoteDataRelay;
import org.robotbrains.data.cloud.timeseries.server.data.SensorData;
import org.robotbrains.data.cloud.timeseries.server.data.SensorDataSample;
import rx.Subscription;

import java.util.Map;

/**
 * Data database relay that uses KairosDB as a backend.
 * 
 * @author Keith M. Hughes
 */
public class KairosDbDatabaseRelay implements ManagedResource {

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
      String kairosConnectionUrl = String.format("http://%s:%s",
          configuration.get(CONFIGURATION_DATABASE_KAIROSDB_CONNECTION_HOST),
          configuration.get(CONFIGURATION_DATABASE_KAIROSDB_CONNECTION_PORT));
      kairosdbClient = new HttpClient(kairosConnectionUrl);

      testQueryDatabase();

      remoteDataRelaySubscription = remoteDataRelay.getSensorDataObservable()
          .subscribe(sensorData -> processSensorData(sensorData));
      
      log.info("Database relay for KairosDB at %s running", kairosConnectionUrl);
    } catch (Throwable e) {
      throw InteractiveSpacesException.newFormattedException(e,
          "Could not start up KairosDB database relay");
    }
  }

  @Override
  public void shutdown() {
    remoteDataRelaySubscription.unsubscribe();

    try {
      kairosdbClient.shutdown();
    } catch (Throwable e) {
      throw InteractiveSpacesException.newFormattedException(e,
          "Could not shut down KairosDB database relay");
    }
  }

  /**
   * Process sensor data that has come in.
   * 
   * @param sensorData
   *          the sensor data
   */
  private void processSensorData(SensorData sensorData) {
    String eventKeyPrefix = sensorData.getSource() + "_" + sensorData.getSensingUnit() + "_";

    for (SensorDataSample sample : sensorData.getSamples()) {
      String eventKey = eventKeyPrefix + sample.getSensor();
      MetricBuilder builder = MetricBuilder.getInstance();

      builder.addMetric(eventKey).addTag("sensor", "snapshot").addDataPoint(sample.getTimestamp(),
          sample.getValue());

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

    DateTime startTime = new DateTime(0);
    DateTime endTime = DateTime.now();

    performMetricQuery("keith.test_pi2_light", startTime, endTime);
    performMetricQuery("keith.test_pi2_temperature", startTime, endTime);
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
  private void performMetricQuery(String metricName, DateTime startTime, DateTime endTime)
      throws Exception {
    log.info("Getting metric %s from time %s to time %s", metricName, startTime, endTime);

    QueryBuilder builder = QueryBuilder.getInstance();
    builder.setStart(startTime.toDate()).setEnd(endTime.toDate());

    builder.addMetric(metricName);
    QueryResponse response = kairosdbClient.query(builder);
    for (Queries queries : response.getQueries()) {
      for (Results results : queries.getResults()) {
        log.info(results.getName() + results.getDataPoints());
      }
    }
  }
}
