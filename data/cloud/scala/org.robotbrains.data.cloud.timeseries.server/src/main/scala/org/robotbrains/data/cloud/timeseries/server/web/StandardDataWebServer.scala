/*
 * Copyright (C) 2016 Keith M. Hughes.
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

package org.robotbrains.data.cloud.timeseries.server.web

import java.awt.image.BufferedImage
import java.io.OutputStream
import java.util.ArrayList
import java.util.HashMap
import java.util.List
import java.util.Map

import scala.collection.JavaConversions._

import org.apache.logging.log4j.Logger
import org.jfree.chart.ChartFactory
import org.jfree.chart.JFreeChart
import org.jfree.data.general.SeriesException
import org.jfree.data.xy.XYDataset
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.robotbrains.data.cloud.timeseries.server.data.SensorData
import org.robotbrains.data.cloud.timeseries.server.data.SensorDataQuery
import org.robotbrains.data.cloud.timeseries.server.data.SensorDataSample
import org.robotbrains.data.cloud.timeseries.server.database.DatabaseRelay
import org.robotbrains.support.web.server.HttpDynamicRequestHandler
import org.robotbrains.support.web.server.HttpRequest
import org.robotbrains.support.web.server.HttpResponse
import org.robotbrains.support.web.server.WebServer
import org.robotbrains.support.web.server.netty.NettyWebServer

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.google.common.base.Charsets

import io.smartspaces.util.web.CommonMimeTypes
import javax.imageio.ImageIO

/**
 * The standard web server that provides access to the time series data.
 *
 * @author Keith M. Hughes
 */

class StandardDataWebServer(databaseRelay: DatabaseRelay, configuration: Map[String, String],
    log: Logger) extends DataWebServer {

  /**
   * The URI prefix for the endpoint for getting the raw timeseries data.
   */
  val WEB_SERVER_URI_PREFIX_ENDPOINT_DATA = "data"

  /**
   * The URI prefix for the endpoint for getting the timeseries data as a graph.
   */
  val WEB_SERVER_URI_PREFIX_ENDPOINT_GRAPH = "graph"

  /**
   * The pattern for date, times.
   */
  val DATE_TIME_FORMAT_PATTERN = "yyyy/MM/dd@HH:mm:ss"

  /**
   * The field name in a data result giving the source of the data.
   */
  val DATA_RESULT_FIELD_SOURCE = "source"

  /**
   * The field name in a data result giving the sensing unit of the data.
   */
  val DATA_RESULT_FIELD_SENSING_UNIT = "sensingUnit"

  /**
   * The field name in a data result giving the sensor map of the data.
   */
  val DATA_RESULT_FIELD_SENSOR_MAP = "sensors"

  /**
   * The field name in a data result giving the samples in an individual
   * sensor's data.
   */
  val DATA_RESULT_FIELD_SENSOR_MAP_SAMPLES = "samples"

  /**
   * The field name in a data result giving the value of a sample.
   */
  val DATA_RESULT_FIELD_SAMPLE_VALUE = "value"

  /**
   * The field name in a data result giving the timestamp of a sample.
   */
  val DATA_RESULT_FIELD_SAMPLE_TIMESTAMP = "timestamp"

  /**
   * The JSON mapper.
   *
   * <p>
   * This object is thread safe.
   */
  val MAPPER = new ObjectMapper() with ScalaObjectMapper
  MAPPER.registerModule(DefaultScalaModule)
  MAPPER.getFactory().enable(JsonGenerator.Feature.ESCAPE_NON_ASCII)

  /**
   * The actual web server that will serve the data.
   */
  var webServer: WebServer = null

  /**
   * The port the web server will be exposed on.
   */
  val webServerPort = 8095

  /**
   * The date parser for query dates.
   */
  val dateTimeFormat = DateTimeFormat.forPattern(DATE_TIME_FORMAT_PATTERN)

  override def startup(): Unit = {
    webServer = new NettyWebServer(webServerPort, log)
    webServer.startup()

    webServer.addDynamicContentHandler(WEB_SERVER_URI_PREFIX_ENDPOINT_GRAPH, true, new HttpDynamicRequestHandler() {
      def handle(request: HttpRequest, response: HttpResponse): Unit = {
        handleGraphRequest(request, response)
      }
    })

    webServer.addDynamicContentHandler(WEB_SERVER_URI_PREFIX_ENDPOINT_DATA, true, new HttpDynamicRequestHandler() {
      def handle(request: HttpRequest, response: HttpResponse): Unit = {
        handleDataRequest(request, response)
      }
    })
  }

  override def shutdown(): Unit = {
    if (webServer != null) {
      webServer.shutdown()
      webServer = null
    }
  }

  /**
   * Handle a request for data.
   *
   * @param request
   *          the HTTP request
   * @param response
   *          the HTTP response
   */
  def handleDataRequest(request: HttpRequest, response: HttpResponse): Unit = {
    try {
      val query = getDataQueryFromRequest(request)

      log.info("Data query is %s", query)

      val data = databaseRelay.querySensorData(query)

      val sensorMap = new HashMap[String, Object]()
      val sampleList = new ArrayList[Map[String, Object]]()
      var sensorName: String = null
      data.samples.foreach(sample => {
        val sampleMap = new HashMap[String, Object]()
        sampleMap.put(DATA_RESULT_FIELD_SAMPLE_TIMESTAMP, sample.timestamp.asInstanceOf[AnyRef])
        sampleMap.put(DATA_RESULT_FIELD_SAMPLE_VALUE, sample.value.asInstanceOf[AnyRef])

        sampleList.add(sampleMap)

        sensorName = sample.sensor
      })

      if (sensorName != null) {
        val sensorData = new HashMap[String, Object]()
        sensorData.put(DATA_RESULT_FIELD_SENSOR_MAP_SAMPLES, sampleList)

        sensorMap.put(sensorName, sensorData)
      }

      // TODO(keith): Consider placing all this in the sensor data map.
      val resultData = new HashMap[String, Object]()
      resultData.put(DATA_RESULT_FIELD_SOURCE, data.source)
      resultData.put(DATA_RESULT_FIELD_SENSING_UNIT, data.sensingUnit)
      resultData.put(DATA_RESULT_FIELD_SENSOR_MAP, sensorMap)

      val resultContent = MAPPER.writeValueAsString(resultData)
      response.setContentType(CommonMimeTypes.MIME_TYPE_APPLICATION_JSON)
      val outputStream = response.getOutputStream()
      outputStream.write(resultContent.getBytes(Charsets.UTF_8))
      outputStream.flush()
    } catch {
      case e: Exception =>
        log.error("Could not get time series data", e)
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
  def handleGraphRequest(request: HttpRequest, response: HttpResponse): Unit = {
    try {
      val query = getDataQueryFromRequest(request)

      log.info("Graphing query is %s", query)

      val data = databaseRelay.querySensorData(query)

      val chart = renderChart(query, data)
      writeChartResponse(response, chart)
    } catch {
      case e: Exception =>
        log.error("Could not plot time series graph", e)
    }
  }

  /**
   * Get the data query from the hTTP request.
   *
   * @param request
   *          the HTTP request for the query
   *
   * @return the query
   */
  def getDataQueryFromRequest(request: HttpRequest): SensorDataQuery = {
    val pathComponents = request.getUri().getPath().split("/")

    val queryParameters = request.getUriQueryParameters()
    val startDateString = queryParameters.get("start")
    var startDate = new DateTime(0)
    if (startDateString != null) {
      startDate = dateTimeFormat.parseDateTime(startDateString)
    }

    val endDateString = queryParameters.get("end")
    var endDate = new DateTime()
    if (endDateString != null) {
      endDate = dateTimeFormat.parseDateTime(endDateString)
    }

    val source = pathComponents(2)
    val sensingUnit = pathComponents(3)
    val sensor = pathComponents(4)

    val query = new SensorDataQuery(source, sensingUnit, sensor, startDate, endDate)

    return query
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
  private def renderChart(query: SensorDataQuery, data: SensorData): JFreeChart = {
    return createChart(query, createDataset(data))
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
  private def writeChartResponse(response: HttpResponse, chart: JFreeChart): Unit = {
    val chartImage = chart.createBufferedImage(560, 370, null)
    ImageIO.write(chartImage, "png", response.getOutputStream())
    response.setContentType(CommonMimeTypes.MIME_TYPE_IMAGE_PNG)
  }

  /**
   * Create a data set from the sensor data.
   *
   * @param data
   *          the sensor data
   *
   * @return the dataset representing the sensordata
   */
  private def createDataset(data: SensorData): XYDataset = {
    val series = new XYSeries("Fun Data")
    data.samples.foreach(sample => {
      try {
        series.add(sample.timestamp, sample.value)
      } catch {
        case e: SeriesException =>
          log.error("Error adding to series graph", e)
      }
    })

    return new XYSeriesCollection(series)
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
  private def createChart(query: SensorDataQuery, dataset: XYDataset): JFreeChart = {
    return ChartFactory.createTimeSeriesChart(
      String.format("Sample Data: %s - %s - %s", query.source, query.sensingUnit,
        query.sensor), "Time", "Value", dataset, false, false, false)
  }
}
