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
package org.robotbrains.data.cloud.timeseries.comm.remote.mqtt;

import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Properties;

/**
 * The MQTT remote data relay.
 * 
 * @author Keith M. Hughes
 */
public class MqttRemoteDataRelay {
  private static final String CONFIGURATION_NAME_TOPIC_INCOMING = "smartspaces.cloud.timeseries.topic.incoming";
  private static final String MQTT_CLIENT_ID = "/smartspaces/cloud/timseries/relay";
  public static final String CONFIGURATION_NAME_MQTT_PASSWORD = "mqtt.password";
  public static final String CONFIGURATION_NAME_MQTT_USERNAME = "mqtt.username";
  public static final String CONFIGURATION_NAME_MQTT_SERVER_HOST = "mqtt.server.host";
  public static final String CONFIGURATION_NAME_MQTT_SERVER_PORT = "mqtt.server.port";

  /**
   * Memory persistence client. Since not publishing, memory persistence should
   * be fine.
   */
  private MemoryPersistence persistence = new MemoryPersistence();
  
  /**
   * The MQTT client.
   */
  private MqttClient mqttClient;
  
  /**
   * The configuration for the application.
   */
  private Properties configuration;
  
  /**
   * The logger to use.
   */
  private Logger log;

  public MqttRemoteDataRelay(Properties configuration, Logger log) {
    this.configuration = configuration;
    this.log = log;
  }

  public void startup() {
    try {
      String serverUri = String.format("tcp://%s:%s", configuration.get(CONFIGURATION_NAME_MQTT_SERVER_HOST), configuration.get(CONFIGURATION_NAME_MQTT_SERVER_PORT));
      mqttClient = new MqttClient(serverUri, MQTT_CLIENT_ID, persistence);

      mqttClient.setCallback(new MqttCallback() {
        @Override
        public void connectionLost(Throwable cause) {
          handleConnectionLost(cause);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
          // Not used since this node isn't publishing
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
          handleMessageArrived(topic, message);
        }
      });

      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(true);
      options.setUserName(configuration.getProperty(CONFIGURATION_NAME_MQTT_USERNAME));
      options.setPassword(configuration.getProperty(CONFIGURATION_NAME_MQTT_PASSWORD).toCharArray());

      log.info("Connecting to broker: %s", mqttClient.getServerURI());
      mqttClient.connect(options);

      log.info("Connected");

      String topicIncoming = configuration.getProperty(CONFIGURATION_NAME_TOPIC_INCOMING);
      mqttClient.subscribe(topicIncoming);
    } catch (Exception e) {
      log.error("Error during MQTT connect", e);
    }
  }

  public void shutdown() {
    try {
      mqttClient.disconnect();
    } catch (MqttException e) {
      log.error("Error during MQTT disconnect", e);
    }
  }
  
  private void handleConnectionLost(Throwable cause) {
    log.warn("Connection lost");
    cause.printStackTrace();
  }

  private void handleMessageArrived(String topic, MqttMessage message) {
    log.info("Got message on topic %s with content %s", topic,
        new String(message.getPayload()));
  }
}
