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
package org.robotbrains.support.server.web;

import interactivespaces.InteractiveSpacesException;
import interactivespaces.util.resource.ManagedResource;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A web server based on Netty.
 * 
 * @author Keith M. Hughes
 */
public class NettyWebServer implements ManagedResource {

  static final boolean SSL = false;
  static final int PORT = Integer.parseInt(System.getProperty("port", SSL ? "8443" : "8080"));

  public static void main(String[] args) {
    NettyWebServer server = new NettyWebServer(LogManager.getLogger("HelloWorld"));
    server.startup();
  }

  /**
   * The group for handling boss events.
   */
  private EventLoopGroup bossGroup;

  /**
   * The group for handling worker events.
   */
  private EventLoopGroup workerGroup;

  /**
   * The logger to use.
   */
  private Logger log;

  /**
   * Construct a new web server.
   * 
   * @param log
   *          the logger to use
   */
  public NettyWebServer(Logger log) {
    this.log = log;
  }

  public void startup() {
    try {
      // Configure SSL.
      SslContext sslCtx;
      if (SSL) {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
      } else {
        sslCtx = null;
      }

      bossGroup = new NioEventLoopGroup(1);
      workerGroup = new NioEventLoopGroup();

      ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
          .childHandler(new NettyWebServerInitializer(sslCtx, this));

      Channel ch = b.bind(PORT).sync().channel();

      ch.closeFuture().sync();
    } catch (Throwable e) {
      throw InteractiveSpacesException.newFormattedException(e, "Could not create web server");
    }
  }

  public void shutdown() {
    bossGroup.shutdownGracefully();
    workerGroup.shutdownGracefully();
  }
}