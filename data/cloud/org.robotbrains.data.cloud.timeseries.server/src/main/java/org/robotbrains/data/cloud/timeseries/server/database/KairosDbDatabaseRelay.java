/**
 * 
 */
package org.robotbrains.data.cloud.timeseries.server.database;

import org.apache.logging.log4j.Logger;
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

import java.util.Date;
import java.util.Map;

/**
 * @author Keith M. Hughes
 *
 */
public class KairosDbDatabaseRelay {
  private static final String KAIROS_CONNECTION_URL = "http://localhost:8090";

  /**
   * The data relay supplying data to this database relay.
   */
  private RemoteDataRelay remoteDataRelay;

  /**
   * The configuration for the relay.
   */
  private Map<String, String> configuration;

  /**
   * The logger.
   */
  private Logger log;

  private HttpClient kairosdbClient;

  private Subscription remoteDataRelaySubscription;

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

  /**
   * Start up the database relay.
   * 
   * @throws Exception
   */
  public void startup() throws Exception {
    kairosdbClient = new HttpClient(KAIROS_CONNECTION_URL);

    GetResponse metricResponse = kairosdbClient.getMetricNames();

    log.info("Metric Response Code =" + metricResponse.getStatusCode());
    for (String name : metricResponse.getResults()) {
      log.info(name);
    }

    QueryBuilder builder = QueryBuilder.getInstance();
    builder.setStart(new Date(0)).addMetric("experimental.metric1");
    QueryResponse response = kairosdbClient.query(builder);
    for (Queries queries : response.getQueries()) {
      for (Results results : queries.getResults()) {
        log.info(results.getName() + results.getDataPoints());
      }
    }

    remoteDataRelaySubscription = remoteDataRelay.getSensorDataObservable()
        .subscribe(sensorData -> processSensorData(sensorData));
  }

  /**
   * Shut the relay down.
   */
  public void shutdown() throws Exception {
    remoteDataRelaySubscription.unsubscribe();

    kairosdbClient.shutdown();
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

      builder.addMetric(eventKey).addDataPoint(sample.getTimestamp(), sample.getValue());

      try {
        Response pushResponse = kairosdbClient.pushMetrics(builder);
        log.info("Push Response Code =" + pushResponse.getStatusCode());
      } catch (Throwable e) {
        log.error(String.format("Failed while writing data point %s", eventKey), e);
      }
    }
  }
}
