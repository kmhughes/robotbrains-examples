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

package org.robotbrains.data.cloud.timeseries.server.logging;

import com.google.common.io.Closeables;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A logging provider that uses Log4J
 * 
 * @author Keith M. Hughes
 */
public class Log4jLoggingProvider {

  /**
   * Path to the configuration file.
   * 
   * <p>
   * Can be {@code null}.
   */
  private String configurationFilePath;

  /**
   * Construct a new logging provider.
   * 
   * @param configurationFilePath
   *          path to the configuration file, can be {@code null}.
   */
  public Log4jLoggingProvider(String configurationFilePath) {
    this.configurationFilePath = configurationFilePath;
  }

  /**
   * Start the logging provider.
   */
  public void startup() {
    InputStream configurationStream = null;
    try {
      ConfigurationSource source;
      if (configurationFilePath != null) {
        File configurationFile = new File(configurationFilePath);
        configurationStream = new FileInputStream(configurationFile);
        source = new ConfigurationSource(configurationStream, configurationFile);
      } else {
        configurationStream = getClass().getClassLoader().getResourceAsStream("log4j.xml");
        source = new ConfigurationSource(configurationStream);
      }
      Configurator.initialize(null, source);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } finally {
      try {
        Closeables.close(configurationStream, true);
      } catch (IOException e) {
        // Won't happen
      }
    }
  }

  /**
   * Get the logger for the application.
   * 
   * @return the logger
   */
  public Logger getLog() {
    return LogManager.getFormatterLogger("foo");
  }
}
