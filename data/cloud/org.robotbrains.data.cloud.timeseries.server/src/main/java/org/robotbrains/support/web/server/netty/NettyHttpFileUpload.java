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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostMultipartRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import org.apache.logging.log4j.Logger;
import org.robotbrains.support.web.server.HttpFileUpload;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A Netty-based {@link HttpFileUpload}.
 *
 * @author Keith M. Hughes
 */
public class NettyHttpFileUpload implements HttpFileUpload {

  /**
   * The handler for the POST request. It can be {@code null}.
   */
  private NettyHttpPostRequestHandler handler;

  /**
   * The parameters that were part of the post.
   */
  private Map<String, String> parameters = new HashMap<>();

  /**
   * The decoder.
   */
  private HttpPostMultipartRequestDecoder decoder;

  /**
   * The request that started everything off.
   */
  private HttpRequest nettyHttpRequest;

  /**
   * The web server handler handling the request.
   */
  private NettyWebServerHandler webServerHandler;

  /**
   * FileUpload container for this particular upload.
   */
  private FileUpload fileUpload;

  /**
   * The cookies to add.
   */
  private Set<HttpCookie> cookies;

  /**
   * Create a new instance.
   *
   * @param nettyHttpRequest
   *          incoming Netty HTTP request
   * @param decoder
   *          decoder to use
   * @param handler
   *          the HTTP POST handler, can be {@code null}
   * @param webServerHandler
   *          underlying web server handler
   * @param cookies
   *          any cookies to add to responses
   */
  public NettyHttpFileUpload(HttpRequest nettyHttpRequest, HttpPostMultipartRequestDecoder decoder,
      NettyHttpPostRequestHandler handler, NettyWebServerHandler webServerHandler,
      Set<HttpCookie> cookies) {
    this.nettyHttpRequest = nettyHttpRequest;
    this.decoder = decoder;
    this.handler = handler;
    this.webServerHandler = webServerHandler;
    this.cookies = cookies;
  }

  /**
   * Get the original Netty HTTP request.
   *
   * @return the original Netty HTTP request
   */
  public HttpRequest getNettyHttpRequest() {
    return nettyHttpRequest;
  }

  /**
   * Add a new chunk of data to the upload.
   *
   * @param ctx
   *          the context for the channel handling
   * @param chunk
   *          the chunked data
   *
   * @throws Exception
   *           problem adding chunk
   *
   */
  public void addChunk(ChannelHandlerContext ctx, HttpChunkedInput chunk) throws Exception {
    decoder.offer(chunk.readChunk(ctx));
    try {
      while (decoder.hasNext()) {
        InterfaceHttpData data = decoder.next();
        if (data != null) {
          processHttpData(data);
        }
        if (chunk.isEndOfInput()) {
          break;
        }
      }
    } catch (EndOfDataDecoderException e) {
      getLog().error("Error while adding HTTP chunked POST data", e);
    }
  }

  /**
   * Clean up anything from the upload.
   */
  public void clean() {
    decoder.cleanFiles();
  }

  /**
   * Complete processing of the file upload.
   */
  public void completeNonChunked() {
    try {
      for (InterfaceHttpData data : decoder.getBodyHttpDatas()) {
        processHttpData(data);
      }
    } catch (Exception e) {
      getLog().error("Error while completing HTTP chunked POST data", e);
    }
  }

  /**
   * The file upload is complete. Handle it as needed.
   *
   * @param context
   *          the context for the channel handler
   */
  public void fileUploadComplete(ChannelHandlerContext context) {
    try {
      handler.handleWebRequest(context, nettyHttpRequest, this, cookies);
      getLog().debug("HTTP [%s] %s --> (File Upload)", HttpResponseStatus.OK,
          nettyHttpRequest.getUri());
    } catch (Exception e) {
      getLog().error(
          String.format("Exception when handling web request %s", nettyHttpRequest.getUri()), e);
      webServerHandler.sendError(context, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    } finally {
      clean();
    }
  }

  /**
   * Process a clump of of the HTTP data.
   *
   * @param data
   *          the data
   */
  private void processHttpData(InterfaceHttpData data) {
    if (data.getHttpDataType() == HttpDataType.Attribute) {
      Attribute attribute = (Attribute) data;
      try {
        parameters.put(attribute.getName(), attribute.getValue());
      } catch (IOException e1) {
        // Error while reading data from File, only print name and error
        getLog().error("Form post BODY Attribute: " + attribute.getHttpDataType().name() + ": "
            + attribute.getName() + " Error while reading value:", e1);
      }
    } else if (data.getHttpDataType() == HttpDataType.FileUpload) {
      fileUpload = (FileUpload) data;
      if (fileUpload.isCompleted()) {
        getLog().info("File %s uploaded", fileUpload.getFilename());
      } else {
        getLog().error("File to be continued but should not!");
      }
    } else {
      getLog().warn("Unprocessed form post data type %s", data.getHttpDataType().name());
    }
  }

  @Override
  public boolean hasFile() {
    return fileUpload != null;
  }

  @Override
  public boolean moveTo(File destination) {
    if (hasFile()) {
      try {
        fileUpload.renameTo(destination);

        return true;
      } catch (Exception e) {
        throw InteractiveSpacesException.newFormattedException(e,
            "Unable to save uploaded file to %s", destination);
      }
    } else {
      return false;
    }
  }

  @Override
  public boolean copyTo(OutputStream destination) {
    if (hasFile()) {
      try {
        ByteBuf buffer = fileUpload.content();
        buffer.getBytes(0, destination, buffer.readableBytes());

        return true;
      } catch (Exception e) {
        throw InteractiveSpacesException.newFormattedException(e,
            "Unable to save uploaded file to output stream");
      }
    } else {
      return false;
    }
  }

  @Override
  public String getFormName() {
    return fileUpload.getName();
  }

  @Override
  public String getFilename() {
    if (hasFile()) {
      return fileUpload.getFilename();
    } else {
      return null;
    }
  }

  @Override
  public Map<String, String> getParameters() {
    return parameters;
  }

  /**
   * Get the log for the uploader.
   *
   * @return the log
   */
  private Logger getLog() {
    return webServerHandler.getWebServer().getLog();
  }
}
