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

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

/**
 * An MQTT client example that shows a publisher that uses synchronous calls for
 * connection and for message delivery.
 * 
 * @author Keith M. Hughes
 */
public class SyncPublisherMqttExample {

  public static void main(String[] args) {

    // The quality of service for the sent message.
    int qualityOfService = 1;

    // Memory persistence for client message deliver. Store it in a file in case
    // lose connections to the broker.
    MqttClientPersistence persistence = new MqttDefaultFilePersistence();

    try {
      MqttClient client =
          new MqttClient("tcp://smartspaces.io:1883", "/mqtt/publisher/plain", persistence);

      client.setCallback(new MqttCallback() {
        @Override
        public void connectionLost(Throwable cause) {
          System.out.println("Lost connection");
          cause.printStackTrace();
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
          System.out.println("Got delvery token " + token.getResponse());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
          // Not needed since not subscribing.
        }
      });

      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(false);

      System.out.println("Connecting to broker: " + client.getServerURI());
      client.connect(options);

      System.out.println("Connected");

      byte[] content = "Hello, world!".getBytes();
      MqttMessage message = new MqttMessage(content);
      message.setQos(qualityOfService);
      client.publish("/greeting", message);

      Thread.sleep(10000);

      client.disconnect();
      System.out.println("Disconnected");
      System.exit(0);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
