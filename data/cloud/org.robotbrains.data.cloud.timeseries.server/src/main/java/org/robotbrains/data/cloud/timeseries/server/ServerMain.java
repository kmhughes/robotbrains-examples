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

import org.apache.logging.log4j.Logger;
import org.robotbrains.data.cloud.timeseries.server.comm.remote.mqtt.PahoMqttRemoteDataRelay;
import org.robotbrains.data.cloud.timeseries.server.comm.remote.mqtt.RemoteDataRelay;
import org.robotbrains.data.cloud.timeseries.server.logging.Log4jLoggingProvider;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * The main driver for the time series cloud.
 * 
 * @author Keith M. Hughes
 */
public class ServerMain {

  public static void main(String[] args) throws Exception {
    final ServerMain main = new ServerMain(args[0]);
    main.startup();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        System.out.println("Shutdown hook ran!");
        main.shutdown();
      }
    });
  }

  /**
   * Location of the configuration file.
   */
  private String configFileLocation;

  /**
   * The remote data relay.
   */
  private RemoteDataRelay remoteDataRelay;

  public ServerMain(String configFileLocation) {
    this.configFileLocation = configFileLocation;
  }

  /**
   * Start up the application.
   * 
   * @throws Exception
   *           the application was unable to start
   */
  public void startup() throws Exception {
    Properties configuration = new Properties();
    configuration.load(new FileInputStream(new File(configFileLocation)));

    Log4jLoggingProvider loggingProvider = new Log4jLoggingProvider();
    loggingProvider.startup();
    Logger log = loggingProvider.getLog();

    remoteDataRelay = new PahoMqttRemoteDataRelay(configuration, log);
    remoteDataRelay.startup();
  }

  /**
   * Shut down the server.
   */
  public void shutdown() {
    remoteDataRelay.shutdown();
  }
}
