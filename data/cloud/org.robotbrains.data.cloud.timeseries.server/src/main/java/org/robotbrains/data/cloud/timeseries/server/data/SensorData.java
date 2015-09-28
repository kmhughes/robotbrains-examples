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

package org.robotbrains.data.cloud.timeseries.server.data;

import java.util.ArrayList;
import java.util.List;

/**
 * A collection of sensor data and its source.
 * 
 * @author Keith M. Hughes
 */
public class SensorData {

  /**
   * The source of the sensor data.
   */
  private String source;

  /**
   * The sensing unit at the source.
   */
  private String sensingUnit;

  /**
   * The data samples.
   */
  List<SensorDataSample> samples = new ArrayList<>();

  /**
   * Construct a new sensor data object.
   * 
   * @param source
   *          the source of the data
   * @param sensingUnit
   *          the sensing unit at the source
   */
  public SensorData(String source, String sensingUnit) {
    this.source = source;
    this.sensingUnit = sensingUnit;
  }

  /**
   * Get the source of the data.
   * 
   * @return the source
   */
  public String getSource() {
    return source;
  }

  /**
   * Get the sensing unit at the source.
   * 
   * @return the sensing unit
   */
  public String getSensingUnit() {
    return sensingUnit;
  }

  /**
   * Get the data samples.
   * 
   * @return the data samples
   */
  public List<SensorDataSample> getSamples() {
    return samples;
  }

  /**
   * Add a new data sample.
   * 
   * @param sample
   *        the new data sample
   *        
   * @return this objects
   */
  public SensorData addSample(SensorDataSample sample) {
    samples.add(sample);
    
    return this;
  }
}
