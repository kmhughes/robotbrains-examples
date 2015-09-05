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
package org.robotbrains.data.cloud.timeseries;

import org.apache.logging.log4j.Logger;
import org.robotbrains.data.cloud.timeseries.comm.remote.mqtt.MqttRemoteDataRelay;
import org.robotbrains.data.cloud.timeseries.logging.Log4jLoggingProvider;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * The main driver for the time series cloud.
 * 
 * @author Keith M. Hughes
 */
public class Main {

  public static void main(String[] args) throws Exception {
    final Main main = new Main();
    main.startup();
    
    Runtime.getRuntime().addShutdownHook(new Thread()
    {
        @Override
        public void run()
        {
            System.out.println("Shutdown hook ran!");
            main.shutdown();
        }
    });
   }

  /**
   * The remote data relay.
   */
  private MqttRemoteDataRelay remoteDataRelay;

  /**
   * Start up the application.
   * 
   * @throws Exception
   *        the application was unable to start
   */
  public void startup() throws Exception {
    
    Properties configuration = new Properties();
    configuration.load(new FileInputStream(new File(System.getProperty("configuration.file"))));

    Log4jLoggingProvider loggingProvider = new Log4jLoggingProvider();
    loggingProvider.startup();
    Logger log = loggingProvider.getLog();
    
    remoteDataRelay = new MqttRemoteDataRelay(configuration, log);
    remoteDataRelay.startup();
  }

  public void shutdown() {
    remoteDataRelay.shutdown();
  }
}
