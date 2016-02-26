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
class SensorDataQuery(_source: String, _sensingUnit: String, _sensor: String, _startDate: DateTime, _endDate: DateTime) {

  /**
   * Get the source for the query.
   * 
   * @return the source
   */
  def source = _source

  /**
   * Get the sensing unit for the query.
   * 
   * @return the sensing unit
   */
  def sensingUnit = _sensingUnit

  /**
   * Get the sensor for the query.
   * 
   * @return the sensor
   */
  def sensor = _sensor

  /**
   * Get the date of the start of the query range .
   * 
   * @return the date at the start of the query range
   */
  def startDate = _startDate

  /**
   * Get the date of the end of the query range .
   * 
   * @return the date at the end of the query range
   */
  def endDate = _endDate

  override
  def toString = 
    "SensorDataQuery [source=" + source + ", sensingUnit=" + sensingUnit + ", sensor=" +
        sensor + ", startDate=" + startDate + ", endDate=" + endDate + "]"
}
