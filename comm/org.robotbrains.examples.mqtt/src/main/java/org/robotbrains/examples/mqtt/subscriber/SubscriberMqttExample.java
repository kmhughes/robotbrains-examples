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
package org.robotbrains.examples.mqtt.subscriber;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * An MQTT example that subscribes on a topic. Connects anonymously to the broker.
 * 
 * @author Keith M. Hughes
 */
public class SubscriberMqttExample {
  public static void main(String[] args) {
    // Memory persistence client. Since not publishing, memory persistence
    // should be fine.
    MemoryPersistence persistence = new MemoryPersistence();

    try {
      MqttClient client =
          new MqttClient("tcp://smartspaces.io:1883", "/mqtt/subscriber/plain", persistence);
      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(true);

      client.setCallback(new MqttCallback() {

        @Override
        public void connectionLost(Throwable cause) {
          System.out.println("Connection lost");
          cause.printStackTrace();
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
          // Not used since this node isn't publishing
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
          System.out.format("Got message on topic %s with content %s\n", topic,
              new String(message.getPayload()));
        }
      });

      System.out.println("Connecting to broker: " + client.getServerURI());
      client.connect(options);

      System.out.println("Connected");

      client.subscribe("/greeting");

      Thread.sleep(10000);
      
      client.disconnect();
      System.out.println("Disconnected");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
