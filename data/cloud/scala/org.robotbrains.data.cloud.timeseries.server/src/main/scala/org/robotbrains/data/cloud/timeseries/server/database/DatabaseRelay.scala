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

package org.robotbrains.data.cloud.timeseries.server.database;

import io.smartspaces.util.resource.ManagedResource;

import org.robotbrains.data.cloud.timeseries.server.data.SensorData;
import org.robotbrains.data.cloud.timeseries.server.data.SensorDataQuery;

/**
 * The relay for interfacing to the time series database.
 *
 * @author Keith M. Hughes
 */
trait DatabaseRelay extends ManagedResource {

  /**
   * Query the database for sensor data.
   *
   * @param query
   *          the data query
   *
   * @return the sensor data for the query
   *
   * @throws Exception
   *           something happened during the query
   */
  @throws(classOf[Exception])
  def querySensorData(query: SensorDataQuery): SensorData
}