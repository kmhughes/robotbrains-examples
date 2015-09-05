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

import java.util.Map;

/**
 * A listener for messages from a remote data relay.
 * 
 * @author Keith M. Hughes
 */
public interface RemoteDataRelayListener {

  /**
   * New data has come in.
   * 
   * @param data
   *          the new data
   */
   void onNewData(Map<String, Object> data);
}
