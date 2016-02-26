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

import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.nio.channels.SocketChannel;
import java.util.List;

/**
 * A Netty server channel that supplies a channel has a unique ID for the request.
 * 
 * <p>
 * This class to go away once channel IDs are placed back in the Netty API.
 * 
 * @author Keith M. Hughes
 */
public class ServerChannelWithId extends NioServerSocketChannel {
  @Override
  protected int doReadMessages(List<Object> buf) throws Exception {
    SocketChannel ch = javaChannel().accept();

    try {
      if (ch != null) {
        buf.add(new ChannelWithId(this, ch));
        return 1;
      }
    } catch (Throwable t) {

      try {
        ch.close();
      } catch (Throwable t2) {
      }
    }

    return 0;
  }
}