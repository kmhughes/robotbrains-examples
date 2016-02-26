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

package org.robotbrains.data.cloud.timeseries.server.comm.remote.mqtt;

import io.smartspaces.util.resource.ManagedResource;

import org.robotbrains.data.cloud.timeseries.server.data.SensorData;

import rx.Observable;

/**
 * The remote data relay transfers data in and out of the server.
 * 
 * @author Keith M. Hughes
 */
public interface RemoteDataRelay extends ManagedResource {

  /**
   * Get an observable for the data relay.
   * 
   * @return the observable
   */
  Observable<SensorData> getSensorDataObservable();
}