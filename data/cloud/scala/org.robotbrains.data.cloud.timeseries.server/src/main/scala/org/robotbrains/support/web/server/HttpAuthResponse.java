/*
 * Copyright (C) 2013 Google Inc.
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

package org.robotbrains.support.web.server;

import java.net.HttpCookie;
import java.util.Set;

/**
 * Response for an HTTP authorization request.
 *
 * @author Dustin Barnard
 */
public interface HttpAuthResponse {

  /**
   * Returns an identifier for the user who completed this authorization
   * request.
   *
   * @return the user behind the http request which generated this response
   */
  public String getUser();

  /**
   * Was the authentication successful?
   * 
   * @return {@code true} if authorization was successful, {@code false} if
   *         otherwise
   */
  public boolean authSuccessful();

  /**
   * If completing authorization requires redirecting the user to another url,
   * this should be set. This should only be followed if authorization was not
   * successful.
   *
   * @return the redirect url to send the user to.
   */
  public String redirectUrl();

  /**
   * In order to persist identification of the user, the auth provider may wish
   * to set a number of cookies on the eventual HTTP response.
   *
   * @return the set of cookies resulting from this authorization.
   */
  public Set<HttpCookie> getCookies();
}
