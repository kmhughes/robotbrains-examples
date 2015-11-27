/*
 * Copyright (C) 2012 Google Inc.
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

import interactivespaces.InteractiveSpacesException;
import interactivespaces.SimpleInteractiveSpacesException;
import interactivespaces.util.web.MimeResolver;

import com.google.common.collect.Lists;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.robotbrains.support.web.server.HttpAuthProvider;
import org.robotbrains.support.web.server.HttpDynamicPostRequestHandler;
import org.robotbrains.support.web.server.HttpDynamicRequestHandler;
import org.robotbrains.support.web.server.HttpRequest;
import org.robotbrains.support.web.server.HttpResponse;
import org.robotbrains.support.web.server.HttpStaticContentRequestHandler;
import org.robotbrains.support.web.server.WebResourceAccessManager;
import org.robotbrains.support.web.server.WebServer;
import org.robotbrains.support.web.server.WebServerWebSocketHandlerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A web server based on Netty.
 * 
 * @author Keith M. Hughes
 */
public class NettyWebServer implements WebServer {

  static final boolean SSL = false;
  static final int PORT = Integer.parseInt(System.getProperty("port", SSL ? "8443" : "8080"));

  public static void main(String[] args) throws Exception {
    Configurator.initialize(null, new ConfigurationSource(
        NettyWebServer.class.getClassLoader().getResourceAsStream("log4j.xml")));

    NettyWebServer server = new NettyWebServer(8095, LogManager.getFormatterLogger("HelloWorld"));
    server.startup();

    server.addDynamicContentHandler("/foo", true, new HttpDynamicRequestHandler() {

      @Override
      public void handle(HttpRequest request, HttpResponse response) {
        try {
          response.getOutputStream().write("Hello, world".getBytes());
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    });
  }

  /**
   * Name of the server.
   */
  private String serverName;

  /**
   * The port for the server.
   */
  private int port;

  /**
   * HTTP headers to be sent on all responses.
   */
  private Map<String, String> globalHttpContentHeaders = new HashMap<>();

  /**
   * The handler for all web server requests.
   */
  private NettyWebServerHandler serverHandler;

  /**
   * The group for handling boss events.
   */
  private EventLoopGroup bossGroup;

  /**
   * The group for handling worker events.
   */
  private EventLoopGroup workerGroup;

  /**
   * {@code true} if running in debug mode.
   */
  private boolean debugMode;

  /**
   * {@code true} if support being a secure server.
   */
  private boolean secureServer;

  /**
   * The certificate chain file for an SSL connection. Can be {@code null}.
   */
  private File sslCertChainFile;

  /**
   * The key file for an SSL connection. Can be {@code null}.
   */
  private File sslKeyFile;

  /**
   * The SSL context for HTTPS connections. Will be {@code null} if the server
   * is not labeled secure.
   */
  private SslContext sslContext;

  /**
   * The default MIME resolver to use.
   */
  private MimeResolver defaultMimeResolver;

  /**
   * The complete collection of static content handlers.
   */
  private List<HttpStaticContentRequestHandler> staticContentRequestHandlers = new ArrayList<>();

  /**
   * The complete collection of dynamic GET request handlers.
   */
  private List<HttpDynamicRequestHandler> dynamicGetRequestHandlers = new ArrayList<>();

  /**
   * The complete collection of dynamic POST request handlers.
   */
  private List<HttpDynamicPostRequestHandler> dynamicPostRequestHandlers = new ArrayList<>();

  /**
   * All GET request handlers handled by this instance.
   */
  private List<NettyHttpGetRequestHandler> httpGetRequestHandlers = new ArrayList<>();

  /**
   * All POST request handlers handled by this instance.
   */
  private List<NettyHttpPostRequestHandler> httpPostRequestHandlers = new ArrayList<>();

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
  public NettyWebServer(int port, Logger log) {
    this.log = log;
    this.port = port;
  }

  @Override
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

      serverHandler = new NettyWebServerHandler(this);

      bossGroup = new NioEventLoopGroup(1);
      workerGroup = new NioEventLoopGroup();

      ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, workerGroup).channel(ServerChannelWithId.class)
          .childHandler(new NettyWebServerInitializer(sslCtx, this, serverHandler));

      b.bind(port).sync();
    } catch (Throwable e) {
      throw InteractiveSpacesException.newFormattedException(e, "Could not create web server");
    }
  }

  @Override
  public void shutdown() {
    bossGroup.shutdownGracefully();
    workerGroup.shutdownGracefully();
  }

  @Override
  public void addStaticContentHandler(String uriPrefix, File baseDir) {
    addStaticContentHandler(uriPrefix, baseDir, null);
  }

  @Override
  public void addStaticContentHandler(String uriPrefix, File baseDir,
      Map<String, String> extraHttpContentHeaders) {
    addStaticContentHandler(uriPrefix, baseDir, extraHttpContentHeaders, null);
  }

  @Override
  public void addStaticContentHandler(String uriPrefix, File baseDir,
      Map<String, String> extraHttpContentHeaders, HttpDynamicRequestHandler fallbackHandler) {
    if (!baseDir.exists()) {
      throw new InteractiveSpacesException(
          String.format("Cannot find web folder %s", baseDir.getAbsolutePath()));
    }

    NettyHttpDynamicGetRequestHandlerHandler fallbackNettyHandler = fallbackHandler == null ? null
        : new NettyHttpDynamicGetRequestHandlerHandler(serverHandler, uriPrefix, false,
            fallbackHandler, extraHttpContentHeaders);

    NettyStaticContentHandler staticContentHandler = new NettyStaticContentHandler(serverHandler,
        uriPrefix, baseDir, extraHttpContentHeaders, fallbackNettyHandler);
    staticContentHandler.setAllowLinks(isDebugMode());
    if (isDebugMode()) {
      getLog().warn("Enabling web-server link following because of debug mode -- not secure.");
    }

    staticContentHandler.setMimeResolver(defaultMimeResolver);
    addHttpGetRequestHandler(staticContentHandler);

    staticContentRequestHandlers.add(staticContentHandler);
  }

  @Override
  public void addDynamicContentHandler(String uriPrefix, boolean usePath,
      HttpDynamicRequestHandler handler) {
    addDynamicContentHandler(uriPrefix, usePath, handler, null);
  }

  @Override
  public void addDynamicContentHandler(String uriPrefix, boolean usePath,
      HttpDynamicRequestHandler handler, Map<String, String> extraHttpContentHeaders) {
    addHttpGetRequestHandler(new NettyHttpDynamicGetRequestHandlerHandler(serverHandler, uriPrefix,
        usePath, handler, extraHttpContentHeaders));
    dynamicGetRequestHandlers.add(handler);
  }

  @Override
  public void addDynamicPostRequestHandler(String uriPrefix, boolean usePath,
      HttpDynamicPostRequestHandler handler) {
    addDynamicPostRequestHandler(uriPrefix, usePath, handler, null);
  }

  @Override
  public void addDynamicPostRequestHandler(String uriPrefix, boolean usePath,
      HttpDynamicPostRequestHandler handler, Map<String, String> extraHttpContentHeaders) {
    addHttpPostRequestHandler(new NettyHttpDynamicPostRequestHandlerHandler(serverHandler,
        uriPrefix, usePath, handler, extraHttpContentHeaders));
    dynamicPostRequestHandlers.add(handler);
  }

  @Override
  public List<HttpStaticContentRequestHandler> getStaticContentRequestHandlers() {
    return Lists.newArrayList(staticContentRequestHandlers);
  }

  @Override
  public List<HttpDynamicRequestHandler> getDynamicRequestHandlers() {
    return Lists.newArrayList(dynamicGetRequestHandlers);
  }

  @Override
  public List<HttpDynamicPostRequestHandler> getDynamicPostRequestHandlers() {
    return Lists.newArrayList(dynamicPostRequestHandlers);
  }

  @Override
  public void setWebSocketHandlerFactory(String webSocketUriPrefix,
      WebServerWebSocketHandlerFactory webSocketHandlerFactory) {
    serverHandler.setWebSocketHandlerFactory(webSocketUriPrefix, webSocketHandlerFactory);
  }

  @Override
  public String getServerName() {
    return serverName;
  }

  @Override
  public void setServerName(String serverName) {
    this.serverName = serverName;
  }

  @Override
  public void setPort(int port) {
    this.port = port;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public void addContentHeader(String name, String value) {
    globalHttpContentHeaders.put(name, value);
  }

  @Override
  public void addContentHeaders(Map<String, String> headers) {
    globalHttpContentHeaders.putAll(headers);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends MimeResolver> T getDefaultMimeResolver() {
    return (T) defaultMimeResolver;
  }

  @Override
  public void setDefaultMimeResolver(MimeResolver resolver) {
    defaultMimeResolver = resolver;
  }

  @Override
  public void setDebugMode(boolean debugMode) {
    this.debugMode = debugMode;
  }

  /**
   * Check if this server is running in debug mode.
   *
   * @return {@code true} if this server is running in debug mode
   */
  public boolean isDebugMode() {
    return debugMode;
  }

  @Override
  public boolean isSecureServer() {
    return secureServer;
  }

  @Override
  public void setSecureServer(boolean secureServer) {
    this.secureServer = secureServer;
  }

  @Override
  public void setSslCertificates(File sslCertChainFile, File sslKeyFile) {
    if (((sslCertChainFile == null) && (sslKeyFile != null))
        || (sslCertChainFile != null) && (sslKeyFile == null)) {
      throw new SimpleInteractiveSpacesException(
          "Both a certificate chain file and a private key file must be supplied");

    }

    if (sslCertChainFile != null && !sslCertChainFile.isFile()) {
      throw new SimpleInteractiveSpacesException(String.format(
          "The certificate chain file %s does not exist", sslCertChainFile.getAbsolutePath()));
    }

    if (sslKeyFile != null && !sslKeyFile.isFile()) {
      throw new SimpleInteractiveSpacesException(
          String.format("The private key file %s does not exist", sslKeyFile.getAbsolutePath()));
    }

    this.sslCertChainFile = sslCertChainFile;
    this.sslKeyFile = sslKeyFile;
  }

  @Override
  public void setAuthProvider(HttpAuthProvider authProvider) {
    serverHandler.setAuthProvider(authProvider);

  }

  @Override
  public void setAccessManager(WebResourceAccessManager accessManager) {
    serverHandler.setAccessManager(accessManager);
  }

  /**
   * Get the content headers which should go onto every HTTP response.
   *
   * @return the globalHttpContentHeaders
   */
  public Map<String, String> getGlobalHttpContentHeaders() {
    return globalHttpContentHeaders;
  }

  /**
   * Get the logger for the web server.
   * 
   * @return the logger
   */
  public Logger getLog() {
    return log;
  }

  /**
   * Register a new GET request handler to the server.
   *
   * @param handler
   *          the handler to add
   */
  private void addHttpGetRequestHandler(NettyHttpGetRequestHandler handler) {
    httpGetRequestHandlers.add(handler);
  }

  /**
   * Locate a registered GET request handler that can handle the given request.
   *
   * @param nettyRequest
   *          the Netty request
   *
   * @return the first handler that handles the request, or {@code null} if none
   */
      NettyHttpGetRequestHandler
          locateGetRequestHandler(io.netty.handler.codec.http.HttpRequest nettyRequest) {
    for (NettyHttpGetRequestHandler handler : httpGetRequestHandlers) {
      if (handler.isHandledBy(nettyRequest)) {
        return handler;
      }
    }

    return null;
  }

  /**
   * Register a new POST request handler to the server.
   *
   * @param handler
   *          the handler to add
   */
  private void addHttpPostRequestHandler(NettyHttpPostRequestHandler handler) {
    httpPostRequestHandlers.add(handler);
  }

  /**
   * Locate a registered POST request handler that can handle the given request.
   *
   * @param nettyRequest
   *          the Netty request
   *
   * @return the first handler that handles the request, or {@code null} if none
   */
      NettyHttpPostRequestHandler
          locatePostRequestHandler(io.netty.handler.codec.http.HttpRequest nettyRequest) {
    for (NettyHttpPostRequestHandler handler : httpPostRequestHandlers) {
      if (handler.isHandledBy(nettyRequest)) {
        return handler;
      }
    }

    return null;
  }
}