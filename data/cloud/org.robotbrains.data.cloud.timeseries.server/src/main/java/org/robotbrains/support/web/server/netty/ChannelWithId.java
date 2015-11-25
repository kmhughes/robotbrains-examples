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
package org.robotbrains.support.web.server.netty;

import io.netty.channel.Channel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A Netty channel that also has a unique ID for the request.
 * 
 * <p>
 * This class to go away once channel IDs are placed back in the Netty API.
 * 
 * @author Keith M. Hughes
 */
public class ChannelWithId extends NioSocketChannel {
  
  /**
   * The generator for channel IDs.
   */
  private static final AtomicLong CHANNEL_ID_GENERATOR = new AtomicLong(0);

  /**
   * The ID for the channel.
   */
  private long id = CHANNEL_ID_GENERATOR.getAndIncrement();

  public ChannelWithId() {
  }

  public ChannelWithId(SocketChannel socket) {
      super(socket);
  }

  public ChannelWithId(Channel parent, SocketChannel socket) {
      super(parent, socket);
  }

  /**
   * Get the ID of the channel.
   * 
   * @return the ID of the channel
   */
  public long getId() {
      return id;
  }
}