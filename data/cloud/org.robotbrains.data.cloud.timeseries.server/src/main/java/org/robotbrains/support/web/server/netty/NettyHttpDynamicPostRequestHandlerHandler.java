/*
 * Copyright (C) 2015 Google Inc.
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

import static io.netty.handler.codec.http.HttpHeaders.addHeader;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.robotbrains.support.web.server.HttpDynamicPostRequestHandler;
import org.robotbrains.support.web.server.HttpFileUpload;

/**
 * A Netty handler for {@link NettyHttpPostRequestHandler}.
 *
 * @author Keith M. Hughes
 */
public class NettyHttpDynamicPostRequestHandlerHandler implements NettyHttpPostRequestHandler {

  /**
   * The handler which will handle the requests.
   */
  private HttpDynamicPostRequestHandler requestHandler;

  /**
   * The parent content handler for this handler.
   */
  private NettyWebServerHandler parentHandler;

  /**
   * The URI prefix to be handled by this handler.
   */
  private String uriPrefix;

  /**
   * Extra headers to add to the response.
   */
  private Map<String, String> extraHttpContentHeaders = new HashMap<>();

  /**
   * Construct a dynamic request handler.
   *
   * @param parentHandler
   *          the parent web server handler
   * @param uriPrefixBase
   *          the base of the URI prefix that this handler is triggered by
   * @param usePath
   *          {@code true} if the path should be fully used for the match
   * @param requestHandler
   *          the handler which handles the dynamic request
   * @param extraHttpContentHeaders
   *          any extra HTTP content headers to be added to the response
   */
  public NettyHttpDynamicPostRequestHandlerHandler(NettyWebServerHandler parentHandler,
      String uriPrefixBase, boolean usePath, HttpDynamicPostRequestHandler requestHandler,
      Map<String, String> extraHttpContentHeaders) {
    this.parentHandler = parentHandler;

    if (extraHttpContentHeaders != null) {
      this.extraHttpContentHeaders.putAll(extraHttpContentHeaders);
    }

    StringBuilder uriPrefix = new StringBuilder();
    if (!uriPrefixBase.startsWith("/")) {
      uriPrefix.append('/');
    }
    uriPrefix.append(uriPrefixBase);
    if (usePath && !uriPrefixBase.endsWith("/")) {
      uriPrefix.append('/');
    }
    this.uriPrefix = uriPrefix.toString();

    this.requestHandler = requestHandler;
  }

  @Override
  public boolean isHandledBy(HttpRequest req) {
    return req.getUri().startsWith(uriPrefix);
  }

  @Override
  public void handleWebRequest(ChannelHandlerContext ctx, HttpRequest nettyRequest,
      HttpFileUpload upload, Set<HttpCookie> cookiesToAdd) throws IOException {
    NettyHttpRequest request =
        new NettyHttpRequest(nettyRequest, parentHandler.getWebServer().getLog());
    NettyHttpResponse response = new NettyHttpResponse(ctx, extraHttpContentHeaders);
    response.addCookies(cookiesToAdd);

    DefaultFullHttpResponse res;
    try {
      requestHandler.handle(request, upload, response);

      res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
          HttpResponseStatus.valueOf(response.getResponseCode()), response.getChannelBuffer());

      String contentType = response.getContentType();
      if (contentType != null) {
        addHeader(res, HttpHeaders.Names.CONTENT_TYPE, contentType);
      }

      parentHandler.addHttpResponseHeaders(res, response.getContentHeaders());
      parentHandler.sendHttpResponse(ctx, nettyRequest, res, true, false);

      parentHandler.getWebServer().getLog().debug("Dynamic content handler for %s completed",
          uriPrefix);
    } catch (Throwable e) {
      parentHandler.getWebServer().getLog().error(String
          .format("Error while handling dynamic web server request %s", nettyRequest.getUri()), e);

      parentHandler.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
