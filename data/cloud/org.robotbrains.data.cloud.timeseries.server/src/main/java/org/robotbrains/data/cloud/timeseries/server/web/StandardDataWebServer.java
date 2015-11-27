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

package org.robotbrains.data.cloud.timeseries.server.web;

import interactivespaces.util.web.CommonMimeTypes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.SeriesException;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.robotbrains.data.cloud.timeseries.server.data.SensorData;
import org.robotbrains.data.cloud.timeseries.server.data.SensorDataQuery;
import org.robotbrains.data.cloud.timeseries.server.data.SensorDataSample;
import org.robotbrains.data.cloud.timeseries.server.database.DatabaseRelay;
import org.robotbrains.support.web.server.HttpDynamicRequestHandler;
import org.robotbrains.support.web.server.HttpRequest;
import org.robotbrains.support.web.server.HttpResponse;
import org.robotbrains.support.web.server.WebServer;
import org.robotbrains.support.web.server.netty.NettyWebServer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * The standard web server that provides access to the time series data.
 * 
 * @author Keith M. Hughes
 */
public class StandardDataWebServer implements DataWebServer {

  public static void main(String[] args) throws Exception {
    Configurator.initialize(null, new ConfigurationSource(
        StandardDataWebServer.class.getClassLoader().getResourceAsStream("log4j.xml")));

    StandardDataWebServer server =
        new StandardDataWebServer(null, null, LogManager.getFormatterLogger("HelloWorld"));
    server.startup();
  }

  /**
   * The pattern for date, times.
   */
  private static final String DATE_TIME_FORMAT_PATTERN = "yyyy/MM/dd@HH:mm:ss";

  /**
   * The database relay.
   */
  private DatabaseRelay databaseRelay;

  /**
   * The actual web server that will serve the data.
   */
  private WebServer webServer;

  /**
   * The configuration for the web server.
   */
  private Map<String, String> configuration;

  /**
   * The logger.
   */
  private Logger log;

  /**
   * The port the web server will be exposed on.
   */
  private int webServerPort = 8095;

  /**
   * The date parser for query dates.
   */
  private DateTimeFormatter dateTimeFormat;

  /**
   * Construct a new web server.
   * 
   * @param databaseRelay
   *          the relay for interfacing to the database
   * @param configuration
   *          the configuration for the web server
   * @param log
   *          logger for the web server
   */
  public StandardDataWebServer(DatabaseRelay databaseRelay, Map<String, String> configuration,
      Logger log) {
    this.databaseRelay = databaseRelay;
    this.configuration = configuration;
    this.log = log;
    dateTimeFormat = DateTimeFormat.forPattern(DATE_TIME_FORMAT_PATTERN);
  }

  @Override
  public void startup() {
    webServer = new NettyWebServer(webServerPort, log);
    webServer.startup();

    webServer.addDynamicContentHandler("graph", true, new HttpDynamicRequestHandler() {

      @Override
      public void handle(HttpRequest request, HttpResponse response) {
        handleGraphRequest(request, response);
      }
    });
  }

  @Override
  public void shutdown() {
    if (webServer != null) {
      webServer.shutdown();
      webServer = null;
    }
  }

  /**
   * Handle a request for a graph.
   * 
   * @param request
   *          the HTTP request
   * @param response
   *          the HTTP response
   */
  private void handleGraphRequest(HttpRequest request, HttpResponse response) {
    try {
      SensorDataQuery query = getDataQueryFromRequest(request);

      log.info("Graphing query is %s", query);

      SensorData data = databaseRelay.querySensorData(query);

      JFreeChart chart = renderChart(query, data);
      writeChartResponse(response, chart);
    } catch (Exception e) {
      log.error("Could not plot time series graph", e);
    }
  }

  /**
   * Render a chart from the sensor data.
   * 
   * @param query
   *          the data query
   * @param data
   *          the query result
   * 
   * @return a chart of the data
   */
  private JFreeChart renderChart(SensorDataQuery query, SensorData data) {
    return createChart(query, createDataset(data));
  }

  /**
   * Write out the chart as an HTTP resonse.
   * 
   * @param response
   *          the HTTP response
   * @param chart
   *          the chart to be written
   * 
   * @throws IOException
   *           something bad happened
   */
  private void writeChartResponse(HttpResponse response, JFreeChart chart) throws IOException {
    BufferedImage chartImage = chart.createBufferedImage(560, 370, null);
    ImageIO.write(chartImage, "png", response.getOutputStream());
    response.setContentType(CommonMimeTypes.MIME_TYPE_IMAGE_PNG);
  }

  /**
   * Get the data query from the hTTP request.
   * 
   * @param request
   *          the HTTP request for the query
   * 
   * @return the query
   */
  private SensorDataQuery getDataQueryFromRequest(HttpRequest request) {
    String[] pathComponents = request.getUri().getPath().split("/");

    Map<String, String> queryParameters = request.getUriQueryParameters();
    String startDateString = queryParameters.get("start");
    DateTime startDate = new DateTime(0);
    if (startDateString != null) {
      startDate = dateTimeFormat.parseDateTime(startDateString);
    }

    String endDateString = queryParameters.get("end");
    DateTime endDate = new DateTime();
    if (endDateString != null) {
      endDate = dateTimeFormat.parseDateTime(endDateString);
    }

    String source = pathComponents[2];
    String sensingUnit = pathComponents[3];
    String sensor = pathComponents[4];

    SensorDataQuery query = new SensorDataQuery(source, sensingUnit, sensor, startDate, endDate);

    return query;
  }

  /**
   * Create a data set from the sensor data.
   * 
   * @param data
   *          the sensor data
   * 
   * @return the dataset representing the sensordata
   */
  private XYDataset createDataset(SensorData data) {
    XYSeries series = new XYSeries("Fun Data");
    for (SensorDataSample sample : data.getSamples()) {
      try {
        series.add(sample.getTimestamp(), sample.getValue());
      } catch (SeriesException e) {
        log.error("Error adding to series graph", e);
      }
    }

    return new XYSeriesCollection(series);
  }

  /**
   * Create a chart for the given query and data.
   * 
   * @param query
   *          the query that gave the data set
   * @param dataset
   *          the data set that came from the query
   * 
   * @return a chart with the data
   */
  private JFreeChart createChart(SensorDataQuery query, XYDataset dataset) {
    return ChartFactory.createTimeSeriesChart(String.format("Sample Data: %s - %s - %s",
        query.getSource(), query.getSensingUnit(), query.getSensor()), "Time", "Value", dataset,
        false, false, false);
  }
}
