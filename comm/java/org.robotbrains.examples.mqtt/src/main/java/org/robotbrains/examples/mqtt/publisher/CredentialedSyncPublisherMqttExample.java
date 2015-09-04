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

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * An MQTT client example that shows a publisher that logs into the broker for
 * message delivery.
 * 
 * @author Keith M. Hughes
 */
public class CredentialedSyncPublisherMqttExample {
  public static void main(String[] args) {
    // The quality of service for the sent message.
    int qualityOfService = 1;

    // Memory persistence for client message deliver. Store it in a file in case
    // lose connections to the broker.
    MqttClientPersistence persistence = new MqttDefaultFilePersistence();

    try {
      Properties credentials = new Properties(); 
      credentials.load(new FileInputStream(new File("/Users/keith/mqtt.properties")));
      
      MqttClient client =
          new MqttClient("tcp://smartspaces.io:1883", "/mqtt/publisher/credentialed", persistence);
      
      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(true);
      options.setUserName(credentials.getProperty("mqtt.username"));
      options.setPassword(credentials.getProperty("mqtt.password").toCharArray());

      System.out.println("Connecting to broker: " + client.getServerURI());
      client.connect(options);
      System.out.println("Connected");

      byte[] content = "Hello, world!".getBytes();
      MqttMessage message = new MqttMessage(content);
      message.setQos(qualityOfService);
      client.publish("/greeting", message);
      System.out.println("Message published");

      client.disconnect();
      System.out.println("Disconnected");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
