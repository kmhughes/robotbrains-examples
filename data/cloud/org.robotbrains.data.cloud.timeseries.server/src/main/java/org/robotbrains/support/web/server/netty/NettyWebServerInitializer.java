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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslContext;

/**
 * A Netty channel initialize for setting up a web server.
 * 
 * @author Keith M. Hughes
 */
public class NettyWebServerInitializer extends ChannelInitializer<SocketChannel> {

  private final SslContext sslCtx;
  
  private final NettyWebServer webServer;
  
  private final NettyWebServerHandler serverHandler;

  public NettyWebServerInitializer(SslContext sslCtx, NettyWebServer webServer,  NettyWebServerHandler serverHandler) {
    this.sslCtx = sslCtx;
    this.webServer = webServer;
    this.serverHandler = serverHandler;
  }

  @Override
  public void initChannel(SocketChannel ch) {
    ChannelPipeline p = ch.pipeline();
    if (sslCtx != null) {
      p.addLast(sslCtx.newHandler(ch.alloc()));
    }
    p.addLast(new HttpRequestDecoder());
    // Uncomment the following line if you don't want to handle HttpChunks.
    // p.addLast(new HttpObjectAggregator(1048576));
    p.addLast(new HttpResponseEncoder());
    // Remove the following line if you don't want automatic content
    // compression.
    // p.addLast(new HttpContentCompressor());
    p.addLast(serverHandler);
  }
}