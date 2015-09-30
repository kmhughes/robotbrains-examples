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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.robotbrains.data.cloud.timeseries.server.data.SensorData;
import org.robotbrains.data.cloud.timeseries.server.data.SensorDataSample;
import org.robotbrains.interactivespaces.util.data.dynamic.DynamicObject;
import org.robotbrains.interactivespaces.util.data.dynamic.DynamicObject.ArrayDynamicObjectEntry;
import org.robotbrains.interactivespaces.util.data.dynamic.StandardDynamicObjectNavigator;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.observers.Subscribers;

import java.util.List;
import java.util.Map;
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
    MAPPER.getFactory().enable(JsonGenerator.Feature.ESCAPE_NON_ASCII);
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
  private Map<String, String> configuration;

  /**
   * The remote data relay listeners.
   */
  private List<Subscriber<SensorData>> subscribers = new CopyOnWriteArrayList<>();

  /**
   * The observable
   */
  private Observable<SensorData> observable = Observable.create(new OnSubscribe<SensorData>() {
    @SuppressWarnings("unchecked")
    @Override
    public void call(Subscriber<? super SensorData> t) {
      subscribers.add((Subscriber<SensorData>) t);
    }
  });

  /**
   * The logger to use.
   */
  private Logger log;

  public PahoMqttRemoteDataRelay(Map<String, String> configuration, Logger log) {
    this.configuration = configuration;
    this.log = log;
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
      options.setUserName(configuration.get(CONFIGURATION_NAME_MQTT_USERNAME));
      options.setPassword(configuration.get(CONFIGURATION_NAME_MQTT_PASSWORD).toCharArray());

      log.info("Connecting to broker: %s", mqttClient.getServerURI());
      mqttClient.connect(options);

      log.info("Connected to MQTT broker");

      String topicIncoming = configuration.get(CONFIGURATION_NAME_TOPIC_INCOMING);
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

  @Override
  public Observable<SensorData> getSensorDataObservable() {
    return observable;
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
    Map<String, Object> messageData = parseJsonObject(messagePaylog);

    log.debug("Got message on topic %s with content %s", topic, messageData);

    processIncomingMessage(messageData);

  }

  /**
   * Process an incoming message.
   * 
   * @param messageData
   *          the message data
   */
  private void processIncomingMessage(Map<String, Object> messageData) {
    DynamicObject object = new StandardDynamicObjectNavigator(messageData);
    String messageType = object.getRequiredString("type");
    switch (messageType) {
      case "data.sensor":
        processSensorDataMessage(object);
        break;
      default:
        log.info("Got unknown message type %s", messageType);
    }
  }

  /**
   * Process a sensor data message.
   * 
   * <p>
   * The data object is in the envelope data, not the data area itself.
   * 
   * @param object
   *          the dynamic object containing the data
   */
  private void processSensorDataMessage(DynamicObject object) {
    object.down("data");

    String source = object.getString("source");
    String sensingUnit = object.getString("sensingunit");

    SensorData sensorData = new SensorData(source, sensingUnit);

    object.down("sensor.data");

    for (ArrayDynamicObjectEntry sensorDataEntry : object.getArrayEntries()) {
      DynamicObject data = sensorDataEntry.down();
      String sensor = data.getRequiredString("sensor");
      Double value = data.getDouble("value");
      long timestamp = data.getDouble("timestamp").longValue();

      SensorDataSample sample = new SensorDataSample(sensor, value, timestamp);
      sensorData.addSample(sample);

      log.info("Got sample %s", sample);

      notifySubscribersOfNewData(sensorData);
    }
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
   * Notify all listeners of new sensor data.
   * 
   * @param data
   *          the new sensor data
   */
  private void notifySubscribersOfNewData(SensorData data) {
    for (Subscriber<SensorData> subscriber : subscribers) {
      if (!subscriber.isUnsubscribed()) {
        try {
          subscriber.onNext(data);
        } catch (Throwable e) {
          log.error("Error while running remote data subscriber", e);
        }
      }
    }
  }
}
