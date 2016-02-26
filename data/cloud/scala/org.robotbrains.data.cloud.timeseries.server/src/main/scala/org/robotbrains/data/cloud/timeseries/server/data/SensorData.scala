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
class SensorData(_source: String, _sensingUnit: String) {

  /**
   * The data samples.
   */
  val _samples: java.util.List[SensorDataSample] = new java.util.ArrayList()

  /**
   * Get the source of the data.
   * 
   * @return the source
   */
  def source = _source

  /**
   * Get the sensing unit at the source.
   * 
   * @return the sensing unit
   */
  def sensingUnit = _sensingUnit

  /**
   * Get the data samples.
   * 
   * @return the data samples
   */
  def samples: java.util.List[SensorDataSample] =  _samples

  /**
   * Add a new data sample.
   * 
   * @param sample
   *        the new data sample
   *        
   * @return this objects
   */
   def addSample(sample: SensorDataSample): SensorData = {
    samples.add(sample);
    
    return this;
  }
}
