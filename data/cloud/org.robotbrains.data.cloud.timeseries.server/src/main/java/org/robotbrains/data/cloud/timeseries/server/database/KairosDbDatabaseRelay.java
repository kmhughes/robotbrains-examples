/**
 * 
 */
package org.robotbrains.data.cloud.timeseries.server.database;

import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.MetricBuilder;
import org.kairosdb.client.response.GetResponse;
import org.kairosdb.client.response.Response;

/**
 * @author Keith M. Hughes
 *
 */
public class KairosDbDatabaseRelay {
  private static final String KAIROS_CONNECTION_URL = "http://localhost:8090";

  public void startup() throws Exception {
    HttpClient client = new HttpClient(KAIROS_CONNECTION_URL);
    
    MetricBuilder builder = MetricBuilder.getInstance();
    builder.addMetric("experimental.metric1")
            .addTag("host", "server1")
            .addTag("customer", "Acme")
            .addDataPoint(System.currentTimeMillis(), 10)
            .addDataPoint(System.currentTimeMillis(), 30L);
    
    Response pushResponse = client.pushMetrics(builder);
    System.out.println("Push Response Code =" + pushResponse.getStatusCode());
   
    GetResponse metricResponse = client.getMetricNames();

    System.out.println("Metric Response Code =" + metricResponse.getStatusCode());
    for (String name : metricResponse.getResults()) {
      System.out.println(name);
    }
    client.shutdown();
  }
}
