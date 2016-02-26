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

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.smartspaces.SmartSpacesException;

import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.robotbrains.support.web.server.HttpRequest;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * An HTTP request that proxies the Netty HTTP request.
 *
 * @author Keith M. Hughes
 */
public class NettyHttpRequest implements HttpRequest {

  /**
   * The proxied request.
   */
  private io.netty.handler.codec.http.HttpRequest request;

  /**
   * The logger for this request.
   */
  private Logger log;

  /**
   * The headers for the request.
   */
  private Multimap<String, String> headers;

  /**
   * Construct a new request.
   *
   * @param request
   *          the Netty HTTP request
   * @param log
   *          the logger for the request
   */
  public NettyHttpRequest(io.netty.handler.codec.http.HttpRequest request, Logger log) {
    this.request = request;
    this.log = log;
    headers = null;
  }

  @Override
  public URI getUri() {
    try {
      return new URI(request.getUri());
    } catch (URISyntaxException e) {
      // Should never, ever happen
      throw new SmartSpacesException(String.format("Illegal URI syntax %s", request.getUri()), e);
    }
  }

  @Override
  public Map<String, String> getUriQueryParameters() {
    Map<String, String> params = Maps.newHashMap();

    String rawQuery = getUri().getRawQuery();
    if (rawQuery != null && !rawQuery.isEmpty()) {
      String[] components = rawQuery.split("\\&");
      for (String component : components) {
        int pos = component.indexOf('=');
        if (pos != -1) {
          String decode = component.substring(pos + 1);
          try {
            decode = URLDecoder.decode(decode, "UTF-8");
          } catch (Exception e) {
            // Don't care
          }
          params.put(component.substring(0, pos).trim(), decode);
        } else {
          params.put(component.trim(), "");
        }
      }
    }

    return params;
  }

  @Override
  public Logger getLog() {
    return log;
  }

  /**
   * Return the map of key:value pairs of strings making up the header for this
   * request.
   *
   * @return the map of all header key:value pairs
   */
  @Override
  public Multimap<String, String> getHeaders() {
    if (headers == null) {
      buildHeaders();
    }
    return headers;
  }

  /**
   * Get the values of the header string associated with the given key.
   *
   * @param key
   *          the key for the desired headers
   *
   * @return the set of values for the header, or {@code null} if the key isn't
   *         present
   */
  @Override
  public Set<String> getHeader(String key) {
    if (headers == null) {
      buildHeaders();
    }
    if (headers.containsKey(key)) {
      return Sets.newHashSet(headers.get(key));
    }

    return null;
  }

  /**
   * Build up the request headers.
   */
  private void buildHeaders() {
    headers = HashMultimap.create();

    for (Entry<String, String> header : request.headers()) {
      headers.put(header.getKey(), header.getValue());
    }
  }

  @Override
  public HttpCookie getCookie(String key) {
    Collection<HttpCookie> cookies = getCookies();
    for (HttpCookie cookie : cookies) {
      if (key.equals(cookie.getName())) {
        return cookie;
      }
    }

    return null;
  }

  @Override
  public Set<HttpCookie> getCookies() {
    Collection<String> cookieHeader = getHeader("Cookie");
    if (cookieHeader == null) {
      return Sets.newHashSet();
    }
    Set<HttpCookie> cookies = Sets.newHashSet();
    for (String cookie : cookieHeader) {
      cookies.addAll(Collections2.transform(ServerCookieDecoder.STRICT.decode(cookie),
          new Function<Cookie, HttpCookie>() {
            @Override
            public HttpCookie apply(Cookie cookie) {
              return convertFromNettyCookie(cookie);
            }
          }));
    }

    return cookies;
  }

  /**
   * Convert a Netty cookie to a Java HTTP cookie.
   *
   * @param cookie
   *          the Netty cookie
   *
   * @return the Java cookie
   */
  private HttpCookie convertFromNettyCookie(Cookie cookie) {
    HttpCookie httpCookie = new HttpCookie(cookie.name(), cookie.value());
    httpCookie.setDomain(cookie.domain());
    httpCookie.setMaxAge(cookie.maxAge());
    httpCookie.setPath(cookie.path());
    httpCookie.setSecure(cookie.isSecure());

    return httpCookie;
  }
}
