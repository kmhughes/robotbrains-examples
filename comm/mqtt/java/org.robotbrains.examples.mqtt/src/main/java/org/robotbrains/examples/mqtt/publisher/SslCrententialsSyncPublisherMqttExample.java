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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Properties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * An MQTT client example that shows a publisher that logs into the MQTT broker
 * over a secure connection.
 * 
 * @author Keith M. Hughes
 */
public class SslCrententialsSyncPublisherMqttExample {
  public static void main(String[] args) {
    // The quality of service for the sent message.
    int qualityOfService = 1;

    // Memory persistence for client message deliver. Store it in a file in case
    // lose connections to the broker.
    MqttClientPersistence persistence = new MqttDefaultFilePersistence();

    try {
      Properties credentials = new Properties();
      credentials.load(new FileInputStream(new File("/Users/keith/mqtt.properties")));

      MqttClient client = new MqttClient("ssl://smartspaces.io:8883",
          "/mqtt/publisher/credentialed/ssl", persistence);

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
      options.setUserName(credentials.getProperty("mqtt.username"));
      options.setPassword(credentials.getProperty("mqtt.password").toCharArray());
      options.setSocketFactory(configureSSLSocketFactory(credentials));

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

  /**
   * Create an SSL socket factory.
   * 
   * @param credentials
   *          the security credentials
   * 
   * @return the socket factory.
   * 
   * @throws Exception
   *           something bad happened
   */
  public static SSLSocketFactory configureSSLSocketFactory(Properties credentials)
      throws Exception {
    KeyStore ks = KeyStore.getInstance("JKS");
    InputStream jksInputStream = new FileInputStream(credentials.getProperty("keystore.file"));
    char[] keystorePassword = credentials.getProperty("keystore.password").toCharArray();
    ks.load(jksInputStream, keystorePassword);

    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(ks, keystorePassword);

    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(ks);

    SSLContext sc = SSLContext.getInstance("TLS");
    TrustManager[] trustManagers = tmf.getTrustManagers();
    sc.init(kmf.getKeyManagers(), trustManagers, null);

    return sc.getSocketFactory();
  }
}
