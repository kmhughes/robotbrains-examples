/*
 * Copyright (C) 2013 Google Inc.
 * Copyright (C) 2015 Keith M. Hughes
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.smartspaces.SmartSpacesException;
import io.smartspaces.service.web.HttpResponseCode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.util.Map;
import java.util.Set;

import org.robotbrains.support.web.server.HttpResponse;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * A Netty-based HttpResponse
 *
 * @author Keith M. Hughes
 */
public class NettyHttpResponse implements HttpResponse {

  /**
   * Create a Netty representation of a cookie.
   *
   * @param cookie
   *          the standard Java cookie
   *
   * @return the Netty cookie
   */
  public static Cookie createNettyCookie(HttpCookie cookie) {
    Cookie nettyCookie = new DefaultCookie(cookie.getName(), cookie.getValue());
    nettyCookie.setDomain(cookie.getDomain());
    nettyCookie.setMaxAge((int) cookie.getMaxAge());
    nettyCookie.setPath(cookie.getPath());
    nettyCookie.setSecure(cookie.getSecure());

    return nettyCookie;
  }

  /**
   * The Netty handler context.
   */
  private ChannelHandlerContext ctx;

  /**
   * The channel buffer for writing content.
   */
  private ByteBuf channelBuffer;

  /**
   * The output stream for writing on the channel buffer.
   */
  private ByteBufOutputStream outputStream;

  /**
   * Content headers to add to the response.
   */
  private Multimap<String, String> contentHeaders = HashMultimap.create();

  /**
   * The HTTP response code to be used for the response.
   */
  private int responseCode = HttpResponseCode.OK;

  /**
   * The content type of the response.
   */
  private String contentType;

  /**
   * Construct a new response.
   *
   * @param ctx
   *          the Netty context for the connection to the client
   * @param extraHttpContentHeaders
   *          a map of content headers, can be {@code null}
   */
  public NettyHttpResponse(ChannelHandlerContext ctx, Map<String, String> extraHttpContentHeaders) {
    this.ctx = ctx;
    channelBuffer = Unpooled.buffer();

    if (extraHttpContentHeaders != null) {
      for (String key : extraHttpContentHeaders.keySet()) {
        contentHeaders.put(key, extraHttpContentHeaders.get(key));
      }
    }
  }

  /**
   * Construct a new response.
   *
   * @param ctx
   *          the Netty context for the connection to the client
   * @param extraHttpContentHeaders
   *          a multimap of content headers, can be {@code null}
   */
  public NettyHttpResponse(ChannelHandlerContext ctx,
      Multimap<String, String> extraHttpContentHeaders) {
    this.ctx = ctx;
    channelBuffer = Unpooled.buffer();

    if (extraHttpContentHeaders != null) {
      contentHeaders.putAll(extraHttpContentHeaders);
    }
  }

  @Override
  public void setResponseCode(int responseCode) {
    this.responseCode = responseCode;
  }

  @Override
  public int getResponseCode() {
    return responseCode;
  }

  @Override
  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public OutputStream getOutputStream() {
    if (outputStream == null) {
      outputStream = new ByteBufOutputStream(channelBuffer);
    }

    return outputStream;
  }

  @Override
  public void addContentHeader(String name, String value) {
    contentHeaders.put(name, value);
  }

  @Override
  public void addContentHeaders(Multimap<String, String> headers) {
    contentHeaders.putAll(headers);
  }

  @Override
  public Multimap<String, String> getContentHeaders() {
    return contentHeaders;
  }

  /**
   * Get the channel buffer containing the response.
   *
   * <p>
   * Once this is called, the output stream used for writing the response is
   * closed.
   *
   * @return the channel buffer
   */
  public ByteBuf getChannelBuffer() {
    try {
      if (outputStream != null) {
        outputStream.flush();
      }
    } catch (IOException e) {
      throw new SmartSpacesException("Could not flush HTTP dynamic output stream", e);
    } finally {
      if (outputStream != null) {
        try {
          outputStream.close();
        } catch (IOException e) {
          throw new SmartSpacesException("Could not close HTTP dynamic output stream", e);
        }
      }
    }

    return channelBuffer;
  }

  @Override
  public void addCookie(HttpCookie cookie) {
    contentHeaders.put("Set-Cookie", ServerCookieEncoder.STRICT.encode(createNettyCookie(cookie)));
  }

  @Override
  public void addCookies(Set<HttpCookie> newCookies) {
    if (newCookies == null) {
      return;
    }

    contentHeaders.putAll("Set-Cookie", ServerCookieEncoder.STRICT.encode(Collections2.transform(
        newCookies, new Function<HttpCookie, Cookie>() {
          @Override
          public Cookie apply(HttpCookie cookie) {
            return createNettyCookie(cookie);
          }
        })));
  }
}
