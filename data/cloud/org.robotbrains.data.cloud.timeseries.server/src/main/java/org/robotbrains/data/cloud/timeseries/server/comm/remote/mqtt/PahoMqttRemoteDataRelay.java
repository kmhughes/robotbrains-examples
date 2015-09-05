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
package org.robotbrains.data.cloud.timeseries.server.comm.remote.mqtt;

import org.apache.logging.log4j.Logger;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A Paho based MQTT remote data relay.
 * 
 * @author Keith M. Hughes
 */
public class PahoMqttRemoteDataRelay implements RemoteDataRelay {

  /**
   * The configuration name for the MQTT topic for incoming data.
   */
  public static final String CONFIGURATION_NAME_TOPIC_INCOMING =
      "smartspaces.cloud.timeseries.topic.incoming";

  /**
   * The client ID for this MQTT relay.
   */
  public static final String MQTT_CLIENT_ID = "/smartspaces/cloud/timseries/relay";

  /**
   * The configuration name for the MQTT password.
   */
  public static final String CONFIGURATION_NAME_MQTT_PASSWORD = "mqtt.password";

  /**
   * The configuration name for the MQTT username.
   */
  public static final String CONFIGURATION_NAME_MQTT_USERNAME = "mqtt.username";

  /**
   * The configuration name for the MQTT server host.
   */
  public static final String CONFIGURATION_NAME_MQTT_SERVER_HOST = "mqtt.server.host";

  /**
   * The configuration name for the MQTT server port.
   */
  public static final String CONFIGURATION_NAME_MQTT_SERVER_PORT = "mqtt.server.port";

  /**
   * The JSON mapper.
   * 
   * <p>
   * This object is thread safe.
   */
  private static final ObjectMapper MAPPER;

  static {
    MAPPER = new ObjectMapper();
    MAPPER.getJsonFactory().enable(JsonGenerator.Feature.ESCAPE_NON_ASCII);
  }

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
   * The remote data relay listeners.
   */
  private List<RemoteDataRelayListener> listeners = new CopyOnWriteArrayList<>();

  /**
   * The logger to use.
   */
  private Logger log;

  public PahoMqttRemoteDataRelay(Properties configuration, Logger log) {
    this.configuration = configuration;
    this.log = log;
  }

  @Override
  public void addRemoteDataRelayListener(RemoteDataRelayListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeRemoteDataRelayListener(RemoteDataRelayListener listener) {
    listeners.add(listener);
  }

  @Override
  public void startup() {
    try {
      String serverUri =
          String.format("tcp://%s:%s", configuration.get(CONFIGURATION_NAME_MQTT_SERVER_HOST),
              configuration.get(CONFIGURATION_NAME_MQTT_SERVER_PORT));
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
      options
          .setPassword(configuration.getProperty(CONFIGURATION_NAME_MQTT_PASSWORD).toCharArray());

      log.info("Connecting to broker: %s", mqttClient.getServerURI());
      mqttClient.connect(options);

      log.info("Connected to MQTT broker");

      String topicIncoming = configuration.getProperty(CONFIGURATION_NAME_TOPIC_INCOMING);
      mqttClient.subscribe(topicIncoming);
    } catch (Exception e) {
      log.error("Error during MQTT connect", e);
    }
  }

  @Override
  public void shutdown() {
    if (mqttClient != null && mqttClient.isConnected()) {
      try {
        mqttClient.disconnect();
      } catch (MqttException e) {
        log.error("Error during MQTT disconnect", e);
      }
    }
  }

  /**
   * Handle a lost connection to the MQTT broker.
   * 
   * @param cause
   *          cause for the lost connection
   */
  private void handleConnectionLost(Throwable cause) {
    log.warn("Connection to MQTT broker lost", cause);
  }

  /**
   * Handle a message that has arrived.
   * 
   * @param topic
   *          the topic that the message came in on
   * @param message
   *          the message
   */
  private void handleMessageArrived(String topic, MqttMessage message) {
    String messagePaylog = new String(message.getPayload());
    Map<String, Object> data = parseJsonObject(messagePaylog);

    log.info("Got message on topic %s with content %s", topic, data);
    
    notifyListenersOfNewData(data);
  }

  /**
   * Parse an incoming object.
   * 
   * @param object
   *          the object to parse
   * 
   * @return the map of data
   */
  private Map<String, Object> parseJsonObject(String object) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = MAPPER.readValue(object, Map.class);
      return map;
    } catch (Throwable e) {
      throw new RuntimeException("Could not parse JSON string", e);
    }
  }

  /**
   * Get a JSON string for data.
   * 
   * @param data
   *          the data to be encoded
   * 
   * @return the JSON-encoded string
   */
  private String toJsonString(Object data) {
    try {
      return MAPPER.writeValueAsString(data);
    } catch (Throwable e) {
      throw new RuntimeException("Could not serialize JSON object as string", e);
    }
  }

  /**
   * Notify all listeners of new data.
   * 
   * @param data
   */
  private void notifyListenersOfNewData(Map<String, Object> data) {
    for (RemoteDataRelayListener listener : listeners) {
      try {
        listener.onNewData(data);
      } catch (Throwable e) {
        log.error("Error while running remote data listener handler", e);
      }
    }
  }
}
