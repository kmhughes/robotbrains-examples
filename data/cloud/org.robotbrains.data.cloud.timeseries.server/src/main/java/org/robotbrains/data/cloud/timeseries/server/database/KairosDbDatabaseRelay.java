/**
 * 
 */
package org.robotbrains.data.cloud.timeseries.server.database;

import org.apache.logging.log4j.Logger;
import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.MetricBuilder;
import org.kairosdb.client.response.GetResponse;
import org.kairosdb.client.response.Response;

import java.util.Map;

/**
 * @author Keith M. Hughes
 *
 */
public class KairosDbDatabaseRelay {
  private static final String KAIROS_CONNECTION_URL = "http://localhost:8090";

  /**
   * The configuration for the relay.
   */
  private Map<String, String> configuration;

  /**
   * The logger.
   */
  private Logger log;

  /**
   * Construct a new relay.
   * 
   * @param configuration
   *          configuration for the relay
   * @param log
   *          the log to use
   */
  public KairosDbDatabaseRelay(Map<String, String> configuration, Logger log) {
    this.configuration = configuration;
    this.log = log;
  }

  /**
   * Start up the database relay.
   * 
   * @throws Exception
   */
  public void startup() throws Exception {
    HttpClient client = new HttpClient(KAIROS_CONNECTION_URL);

    MetricBuilder builder = MetricBuilder.getInstance();
    builder.addMetric("experimental.metric1").addTag("host", "server1").addTag("customer", "Acme")
        .addDataPoint(System.currentTimeMillis(), 10).addDataPoint(System.currentTimeMillis(), 30L);

    Response pushResponse = client.pushMetrics(builder);
    log.info("Push Response Code =" + pushResponse.getStatusCode());

    GetResponse metricResponse = client.getMetricNames();

    log.info("Metric Response Code =" + metricResponse.getStatusCode());
    for (String name : metricResponse.getResults()) {
      log.info(name);
    }
    client.shutdown();
  }

  /**
   * Shut the relay down.
   */
  public void shutdown() {
    // TODO(keith): Shut down the connection to Kairos.
  }
}
