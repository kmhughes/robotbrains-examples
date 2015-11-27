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
package org.robotbrains.data.cloud.timeseries.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.Logger;
import org.robotbrains.data.cloud.timeseries.server.comm.remote.mqtt.PahoMqttRemoteDataRelay;
import org.robotbrains.data.cloud.timeseries.server.comm.remote.mqtt.RemoteDataRelay;
import org.robotbrains.data.cloud.timeseries.server.data.SensorDataSample;
import org.robotbrains.data.cloud.timeseries.server.database.KairosDbDatabaseRelay;
import org.robotbrains.data.cloud.timeseries.server.logging.Log4jLoggingProvider;
import org.robotbrains.data.cloud.timeseries.server.web.DataWebServer;
import org.robotbrains.data.cloud.timeseries.server.web.StandardDataWebServer;
import org.robotbrains.support.ManagedResources;
import rx.Subscription;

import java.io.File;
import java.util.Map;

/**
 * The main driver for the time series cloud.
 * 
 * @author Keith M. Hughes
 */
public class ServerMain {

  /**
   * The command line argument for specifying the directory for log files.
   */
  private static final String COMMAND_ARG_LOG_FILEPATH = "l";

  /**
   * The command line argument for determining if the time series database should be connected to.
   */
  private static final String COMMAND_ARG_DATABASE = "d";

  public static void main(String[] args) throws Exception {
    final ServerMain main = new ServerMain(args);
    main.startup();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        System.out.println("Shutdown hook ran!");
        main.shutdown();
      }
    });
  }

  private static final String COMMAND_ARG_CONFIG = "c";

  /**
   * The arguments from the command line.
   */
  private String[] args;

  /**
   * The remote data relay.
   */
  private RemoteDataRelay remoteDataRelay;

  /**
   * The reources the server maintains, such as data base connections, etc.
   */
  private ManagedResources managedResources;

  /**
   * The connection to the timeseries database.
   */
  private KairosDbDatabaseRelay databaseRelay;

  /**
   * The web server that provides data access.
   */
  private DataWebServer dataWebServer;

  /**
   * Construct a server.
   * 
   * @param configFileLocation
   *          the location of the configuration file
   */
  public ServerMain(String[] args) {
    this.args = args;
  }

  /**
   * Start up the application.
   * 
   * @throws Exception
   *           the application was unable to start
   */
  public void startup() throws Exception {
    Options options = new Options();
    options.addOption(COMMAND_ARG_DATABASE, false, "do not enable the database transfer");
    options.addOption(COMMAND_ARG_CONFIG, true, "the configuration file");
    options.addOption(COMMAND_ARG_LOG_FILEPATH, true, "path to the log configuration file");

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);

    Map<String, String> configuration = readConfiguration(cmd);

    Log4jLoggingProvider loggingProvider =
        new Log4jLoggingProvider(cmd.getOptionValue(COMMAND_ARG_LOG_FILEPATH));
    loggingProvider.startup();
    Logger log = loggingProvider.getLog();

    managedResources = new ManagedResources(log);

    remoteDataRelay = new PahoMqttRemoteDataRelay(configuration, log);
    managedResources.addResource(remoteDataRelay);

    Subscription subscription = remoteDataRelay.getSensorDataObservable().subscribe(sensorData -> {
      log.debug("Got data from source %s, sensing unit %s", sensorData.getSource(),
          sensorData.getSensingUnit());
      for (SensorDataSample sample : sensorData.getSamples()) {
        log.debug("\tData %s %f %d", sample.getSensor(), sample.getValue(), sample.getTimestamp());
      }
    });

    if (!cmd.hasOption(COMMAND_ARG_DATABASE)) {
      databaseRelay = new KairosDbDatabaseRelay(remoteDataRelay, configuration, log);
      managedResources.addResource(databaseRelay);
    } else {
      // TODO(keith): Add fake data source
    }
    
    dataWebServer = new StandardDataWebServer(databaseRelay, configuration, log);
    managedResources.addResource(dataWebServer);

    managedResources.startupResources();
  }

  /**
   * Shut down the server.
   */
  public void shutdown() {
    managedResources.shutdownResourcesAndClear();
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
  private Map<String, String> readConfiguration(CommandLine cmd) throws Exception {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @SuppressWarnings("unchecked")
    Map<String, String> configuration =
        mapper.readValue(new File(cmd.getOptionValue(COMMAND_ARG_CONFIG)), Map.class);
    return configuration;
  }
}
