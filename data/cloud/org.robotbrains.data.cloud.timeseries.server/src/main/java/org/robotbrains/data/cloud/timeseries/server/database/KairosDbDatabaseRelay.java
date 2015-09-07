/**
 * 
 */
package org.robotbrains.data.cloud.timeseries.server.database;

import org.kairosdb.client.HttpClient;
import org.kairosdb.client.response.GetResponse;

/**
 * @author Keith M. Hughes
 *
 */
public class KairosDbDatabaseRelay {
  private static final String KAIROS_CONNECTION_URL = "http://localhost:8090";

  public void startup() throws Exception {
    HttpClient client = new HttpClient(KAIROS_CONNECTION_URL);
    GetResponse response = client.getMetricNames();

    System.out.println("Response Code =" + response.getStatusCode());
    for (String name : response.getResults()) {
      System.out.println(name);
    }
    client.shutdown();
  }
}
