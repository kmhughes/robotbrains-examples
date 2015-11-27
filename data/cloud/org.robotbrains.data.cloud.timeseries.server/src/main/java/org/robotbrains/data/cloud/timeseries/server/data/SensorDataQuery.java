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

import org.joda.time.DateTime;

/**
 * A query against the sensor data.
 * 
 * @author Keith M. Hughes
 *
 */
public class SensorDataQuery {

  /**
   * The source for the query.
   */
  private String source;

  /**
   * The sensing unit of the source for the query.
   */
  private String sensingUnit;

  /**
   * The sensor on the sensing unit.
   */
  private String sensor;

  /**
   * The starting date of the query range.
   */
  private DateTime startDate;

  /**
   * The ending date of the query range.
   */
  private DateTime endDate;

  /**
   * 
   * @param source
   *          the source for the query
   *
   * @param sensingUnit
   *          the sensing unit of the source for the query
   * 
   * @param sensor
   *          the sensor on the sensing unit
   * 
   * @param startDate
   *          the starting date of the query range
   *
   * @param endDate
   *          the ending date of the query range
   */
  public SensorDataQuery(String source, String sensingUnit, String sensor, DateTime startDate,
      DateTime endDate) {
    this.source = source;
    this.sensingUnit = sensingUnit;
    this.sensor = sensor;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  /**
   * Get the source for the query.
   * 
   * @return the source
   */
  public String getSource() {
    return source;
  }

  /**
   * Get the sensing unit for the query.
   * 
   * @return the sensing unit
   */
  public String getSensingUnit() {
    return sensingUnit;
  }

  /**
   * Get the sensor for the query.
   * 
   * @return the sensor
   */
  public String getSensor() {
    return sensor;
  }

  /**
   * Get the date of the start of the query range .
   * 
   * @return the date at the start of the query range
   */
  public DateTime getStartDate() {
    return startDate;
  }

  /**
   * Get the date of the end of the query range .
   * 
   * @return the date at the end of the query range
   */
  public DateTime getEndDate() {
    return endDate;
  }

  @Override
  public String toString() {
    return "SensorDataQuery [source=" + source + ", sensingUnit=" + sensingUnit + ", sensor="
        + sensor + ", startDate=" + startDate + ", endDate=" + endDate + "]";
  }
}
