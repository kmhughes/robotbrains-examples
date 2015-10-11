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
package org.robotbrains.examples.mqtt.publisher;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * An MQTT client example that shows a publisher that uses asynchronous
 * callbacks for connection and for message delivery.
 * 
 * @author Keith M. Hughes
 */
public class AsyncPublisherMqttExample {
  public static void main(String[] args) {

    // The quality of service for the sent message.
    int qualityOfService = 1;

    // Memory persistence for client message deliver. Store it in a file in case
    // lose connections to the broker.
    MqttClientPersistence persistence = new MqttDefaultFilePersistence();

    try {
      MqttAsyncClient client =
          new MqttAsyncClient("tcp://smartspaces.io:1883", "/mqtt/publisher/async", persistence);

      client.setCallback(new MqttCallback() {
        @Override
        public void connectionLost(Throwable cause) {
          System.out.println("Lost connection");
          cause.printStackTrace();
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
          System.out.println("Got delivery token " + token.getResponse());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
          // Not needed since not subscribing.
        }
      });

      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(true);

      System.out.println("Connecting to broker: " + client.getServerURI());
      final CountDownLatch connectHappened = new CountDownLatch(1);
      client.connect(options, new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken token) {
          System.out.println("Connect Listener has success on token " + token);
          connectHappened.countDown();
        }

        @Override
        public void onFailure(IMqttToken token, Throwable cause) {
          System.out.println("Connect Listener has failure on token " + token);
          cause.printStackTrace();
          connectHappened.countDown();
        }
      });

      if (connectHappened.await(10000, TimeUnit.MILLISECONDS)) {
        System.out.println("Connect async call came back in time");
        handleSuccessfulConnect("/greeting", "Hello, world!", qualityOfService, client);
      } else {
        System.out.println("Connect async call did not come back in time");
      }

      client.disconnect();
      System.out.println("Disconnected");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Handle a successful connection to the broker.
   * 
   * @param topic
   *          the topic to write on
   * @param content
   *          the content to write, as a string
   * @param qualityOfService
   *          the quality of service for the message
   * @param client
   *          the MQTT client
   * 
   * @throws Exception
   *           something bad happened
   */
  private static void handleSuccessfulConnect(String topic, String content, int qualityOfService,
      MqttAsyncClient client) throws Exception {
    System.out.println("Connected");

    MqttMessage message = new MqttMessage(content.getBytes());
    message.setQos(qualityOfService);

    final CountDownLatch publishHappened = new CountDownLatch(1);
    client.publish(topic, message, null, new IMqttActionListener() {
      @Override
      public void onSuccess(IMqttToken token) {
        System.out.println("Publish listener has success on token " + token);
        publishHappened.countDown();
      }

      @Override
      public void onFailure(IMqttToken token, Throwable throwable) {
        System.out.println("Publish listener has failure on token " + token);
        throwable.printStackTrace();
        publishHappened.countDown();
      }
    });
    System.out.println("Message published");

    if (publishHappened.await(10000, TimeUnit.MILLISECONDS)) {
      System.out.println("Async call came back in time");
    } else {
      System.out.println("Async call did not come back in time");
    }
  }
}
