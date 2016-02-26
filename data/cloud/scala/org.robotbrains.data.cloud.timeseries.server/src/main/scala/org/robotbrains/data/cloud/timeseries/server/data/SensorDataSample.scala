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

/**
 * A data sample for a sensor.
 *
 * @author Keith M. Hughes
 */
class SensorDataSample(_sensor: String, _value: Double, _timestamp: Long) {
  
  def sensor = _sensor
  
  def value = _value
  
  def timestamp = _timestamp
  
  override def toString: String =
    "SensorDataSample [sensor=" + sensor + ", value=" + value + ", timestamp=" + timestamp +
      "]";
}
