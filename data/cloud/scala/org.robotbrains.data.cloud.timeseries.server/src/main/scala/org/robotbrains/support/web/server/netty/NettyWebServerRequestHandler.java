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

import io.netty.handler.codec.http.HttpRequest;

import org.robotbrains.support.web.server.HttpResponse;

/**
 * A handler for requests to the web server.
 * 
 * @author Keith M. Hughes
 */
public interface NettyWebServerRequestHandler {

  /**
   * Does the handler handle the request?
   * 
   * @param request
   *          the request
   * 
   * @return {@code true} if the request is handled by this handler
   */
      boolean isHandledBy(HttpRequest request);

  /**
   * Handle the request.
   * 
   * @param request
   *          the request
   * @param response
   *          the response
   */
      void handle(HttpRequest request, HttpResponse response);
}
