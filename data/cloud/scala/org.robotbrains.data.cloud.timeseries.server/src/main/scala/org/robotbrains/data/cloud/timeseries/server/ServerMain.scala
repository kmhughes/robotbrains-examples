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
package org.robotbrains.data.cloud.timeseries.server

import java.io.File
import java.util.Map
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.CommandLineParser
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.apache.logging.log4j.Logger
import org.robotbrains.data.cloud.timeseries.server.comm.remote.mqtt.PahoMqttRemoteDataRelay
import org.robotbrains.data.cloud.timeseries.server.comm.remote.mqtt.RemoteDataRelay
import org.robotbrains.data.cloud.timeseries.server.data.SensorDataSample
import org.robotbrains.data.cloud.timeseries.server.database.KairosDbDatabaseRelay
import org.robotbrains.data.cloud.timeseries.server.logging.Log4jLoggingProvider
import org.robotbrains.data.cloud.timeseries.server.web.DataWebServer
import org.robotbrains.data.cloud.timeseries.server.web.StandardDataWebServer
import org.robotbrains.support.ManagedResources
import rx.Subscription
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

/**
 * The main driver for the time series cloud.
 *
 * @author Keith M. Hughes
 */
object ServerMain {

  /**
   * The command line argument for specifying the directory for log files.
   */
  val COMMAND_ARG_LOG_FILEPATH = "l"

  /**
   * The command line argument for determining if the time series database
   * should be connected to.
   */
  val COMMAND_ARG_DATABASE = "d"

  val COMMAND_ARG_CONFIG = "c"
  
  var myargs: Array[String] = null

  /**
   * The remote data relay.
   */
  var remoteDataRelay: RemoteDataRelay = null

  /**
   * The reources the server maintains, such as data base connections, etc.
   */
  var managedResources: ManagedResources = null

  /**
   * The connection to the timeseries database.
   */
  var databaseRelay: KairosDbDatabaseRelay = null

  /**
   * The web server that provides data access.
   */
  var dataWebServer: DataWebServer = null
  
  def main(args: Array[String]): Unit = {
    myargs = args;
    startup()

    sys addShutdownHook {
      System.out.println("Shutdown hook ran!")
      shutdown()
    }
  }

  /**
   * Start up the application.
   *
   * @throws Exception
   *           the application was unable to start
   */
  def startup(): Unit = {
    val options = new Options()
    options.addOption(COMMAND_ARG_DATABASE, false, "do not enable the database transfer")
    options.addOption(COMMAND_ARG_CONFIG, true, "the configuration file")
    options.addOption(COMMAND_ARG_LOG_FILEPATH, true, "path to the log configuration file")

    val parser = new DefaultParser()
    val cmd = parser.parse(options, myargs)

    val configuration = readConfiguration(cmd)

    val loggingProvider =
      new Log4jLoggingProvider(cmd.getOptionValue(COMMAND_ARG_LOG_FILEPATH))
    loggingProvider.startup()
    val log = loggingProvider.getLog()

    managedResources = new ManagedResources(log)

    remoteDataRelay = new PahoMqttRemoteDataRelay(configuration, log)
    managedResources.addResource(remoteDataRelay)

    //    Subscription subscription =
    //        remoteDataRelay.getSensorDataObservable().subscribe(
    //            sensorData -> {
    //              log.debug("Got data from source %s, sensing unit %s", sensorData.source(),
    //                  sensorData.sensingUnit())
    //              for (SensorDataSample sample : sensorData.samples()) {
    //                log.debug("\tData %s %f %d", sample.sensor(), sample.value(), sample.timestamp())
    //              }
    //            });

    if (!cmd.hasOption(COMMAND_ARG_DATABASE)) {
      databaseRelay = new KairosDbDatabaseRelay(remoteDataRelay, configuration, log)
      managedResources.addResource(databaseRelay)
    } else {
      // TODO(keith): Add fake data source
    }

    dataWebServer = new StandardDataWebServer(databaseRelay, configuration, log)
    managedResources.addResource(dataWebServer)

    managedResources.startupResources()
  }

  /**
   * Shut down the server.
   */
  def shutdown(): Unit = {
    managedResources.shutdownResourcesAndClear()
    println("Shut down!")
  }

  /**
   * Read the configuration.
   *
   * @return the configuration
   *
   * @throws Exception
   *           was unable to read the configuration file or could not parse the
   *           configuration
   */
  def readConfiguration(cmd: CommandLine): Map[String, String] = {
    val mapper = new ObjectMapper(new YAMLFactory()) with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    val configuration =
      mapper.readValue(new File(cmd.getOptionValue(COMMAND_ARG_CONFIG)), classOf[java.util.Map[String,String]])
    return configuration
  }
}
