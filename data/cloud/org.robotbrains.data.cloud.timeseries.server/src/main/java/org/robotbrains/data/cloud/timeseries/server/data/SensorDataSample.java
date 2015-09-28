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
public class SensorDataSample {

  /**
   * The sensor that gave the data sample.
   */
  private String sensor;

  /**
   * The value of the sample.
   */
  private Double value;

  /**
   * The timestamp of when the the sample was taken.
   */
  private long timestamp;

  /**
   * Construct a new sample.
   * 
   * @param sensor
   *          the sensor that provided the sample
   * @param value
   *          the value the sensor gave
   * @param timestamp
   *          the timestamp of when the the sample was taken
   */
  public SensorDataSample(String sensor, Double value, long timestamp) {
    this.sensor = sensor;
    this.value = value;
    this.timestamp = timestamp;
  }

  /**
   * Get the sensor that gave the data.
   * 
   * @return the sensor
   */
  public String getSensor() {
    return sensor;
  }

  /**
   * Get the value of the sample.
   * 
   * @return the value
   */
  public Double getValue() {
    return value;
  }

  /**
   * Get the timestamp of when the data sample was taken.
   * 
   * @return the timestamp
   */
  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public String toString() {
    return "SensorDataSample [sensor=" + sensor + ", value=" + value + ", timestamp=" + timestamp
        + "]";
  }
}
