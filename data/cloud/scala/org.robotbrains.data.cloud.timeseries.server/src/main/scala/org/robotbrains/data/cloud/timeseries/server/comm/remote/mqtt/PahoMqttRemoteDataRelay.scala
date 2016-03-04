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
package org.robotbrains.data.cloud.timeseries.server.comm.remote.mqtt

import java.util.List
import java.util.Map
import java.util.concurrent.CopyOnWriteArrayList
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.iterableAsScalaIterable
import org.apache.logging.log4j.Logger
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.robotbrains.data.cloud.timeseries.server.data.SensorData
import org.robotbrains.data.cloud.timeseries.server.data.SensorDataSample
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import rx.lang.scala.Observable
import rx.lang.scala.Subscriber
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import io.smartspaces.util.data.dynamic.DynamicObject
import io.smartspaces.util.data.dynamic.StandardDynamicObjectNavigator

/**
 * A Paho based MQTT remote data relay.
 *
 * @author Keith M. Hughes
 */
class PahoMqttRemoteDataRelay(configuration: Map[String, String], log: Logger) extends RemoteDataRelay {

  val MESSAGE_FIELD_DATA = "data"

  val MESSAGE_FIELD_VALUE_TYPE_DATA_SENSOR = "data.sensor"

  val MESSAGE_FIELD_TYPE = "type"

  /**
   * The configuration name for the MQTT topic for incoming data.
   */
  val CONFIGURATION_NAME_TOPIC_INCOMING =
    "smartspaces.cloud.timeseries.topic.incoming"

  /**
   * The configuration name for the MQTT node name for this client.
   */
  val CONFIGURATION_NAME_NODE_NAME =
    "smartspaces.cloud.timeseries.node.name"

  /**
   * The configuration name for the MQTT password.
   */
  val CONFIGURATION_NAME_MQTT_PASSWORD = "mqtt.password"

  /**
   * The configuration name for the MQTT username.
   */
  val CONFIGURATION_NAME_MQTT_USERNAME = "mqtt.username"

  /**
   * The configuration name for the MQTT server host.
   */
  val CONFIGURATION_NAME_MQTT_SERVER_HOST = "mqtt.server.host"

  /**
   * The configuration name for the MQTT server port.
   */
  val CONFIGURATION_NAME_MQTT_SERVER_PORT = "mqtt.server.port"

  /**
   * The JSON mapper.
   *
   * <p>
   * This object is thread safe.
   */
  val MAPPER = new ObjectMapper() with ScalaObjectMapper
  MAPPER.registerModule(DefaultScalaModule)
  MAPPER.getFactory().enable(JsonGenerator.Feature.ESCAPE_NON_ASCII)

  /**
   * Memory persistence client. Since not publishing, memory persistence should
   * be fine.
   */
  val persistence = new MemoryPersistence()

  /**
   * The MQTT client.
   */
  var mqttClient: MqttClient = null

  /**
   * The remote data relay listeners.
   */
  val subscribers: List[Subscriber[SensorData]] = new CopyOnWriteArrayList[Subscriber[SensorData]]()

  /**
   * The observable
   */
  val observable: Observable[SensorData] = Observable(
    subscriber => {
      subscribers.add(subscriber)
    })
    

  override def startup(): Unit = {
    try {
      val serverUri =
        String.format("tcp://%s:%s", configuration.get(CONFIGURATION_NAME_MQTT_SERVER_HOST),
          configuration.get(CONFIGURATION_NAME_MQTT_SERVER_PORT))
      mqttClient =
        new MqttClient(serverUri, configuration.get(CONFIGURATION_NAME_NODE_NAME), persistence)

      mqttClient.setCallback(new MqttCallback() {
        override def connectionLost(cause: Throwable): Unit = {
          handleConnectionLost(cause)
        }

        override def deliveryComplete(token: IMqttDeliveryToken): Unit = {
          // Not used since this node isn't publishing
        }

        override def messageArrived(topic: String, message: MqttMessage): Unit = {
          handleMessageArrived(topic, message)
        }
      })

      val options = new MqttConnectOptions()
      options.setCleanSession(true)
      options.setUserName(configuration.get(CONFIGURATION_NAME_MQTT_USERNAME))
      options.setPassword(configuration.get(CONFIGURATION_NAME_MQTT_PASSWORD).toCharArray())

      log.info("Connecting to MQTT broker %s with client %s", mqttClient.getServerURI(),
        mqttClient.getClientId())
      mqttClient.connect(options)

      log.info("Connected to MQTT broker")

      val topicIncoming = configuration.get(CONFIGURATION_NAME_TOPIC_INCOMING)
      mqttClient.subscribe(topicIncoming)
    } catch {
      case e: Exception => log.error("Error during MQTT connect", e);
    }
  }

  override def shutdown(): Unit = {
    if (mqttClient != null && mqttClient.isConnected()) {
      try {
        mqttClient.disconnect()
      } catch {
        case e: MqttException => log.error("Error during MQTT disconnect", e)
      }
    }
  }

  override def getSensorDataObservable(): Observable[SensorData] = {
    return observable
  }

  /**
   * Handle a lost connection to the MQTT broker.
   *
   * @param cause
   *          cause for the lost connection
   */
  def handleConnectionLost(cause: Throwable): Unit = {
    log.warn("Connection to MQTT broker lost", cause)
  }

  /**
   * Handle a message that has arrived.
   *
   * @param topic
   *          the topic that the message came in on
   * @param message
   *          the message
   */
  def handleMessageArrived(topic: String, message: MqttMessage): Unit = {
    val messagePaylog = new String(message.getPayload())
    val messageData = parseJsonObject(messagePaylog)

    log.debug("Got message on topic %s with content %s", topic, messageData)

    processIncomingMessage(messageData)
  }

  /**
   * Process an incoming message.
   *
   * @param messageData
   *          the message data
   */
  def processIncomingMessage(messageData: Map[String, Object]): Unit = {
    val obj: DynamicObject = new StandardDynamicObjectNavigator(messageData)
    val messageType = obj.getRequiredString(MESSAGE_FIELD_TYPE);
    messageType match {
      case MESSAGE_FIELD_VALUE_TYPE_DATA_SENSOR =>
        processSensorDataMessage(obj)
      case whoa =>
        log.warn("Got unknown message type %s", whoa)
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
  def processSensorDataMessage(obj: DynamicObject): Unit = {
    obj.down(MESSAGE_FIELD_DATA);

    val source = obj.getString("source")
    val sensingUnit = obj.getString("sensingunit")

    val sensorData = new SensorData(source, sensingUnit)

    obj.down("sensor.data")

    obj.getArrayEntries().foreach(sensorDataEntry => {
      val data = sensorDataEntry.down()
      val sensor = data.getRequiredString("sensor")
      val value = data.getDouble("value")
      val timestamp = data.getDouble("timestamp").longValue()

      val sample = new SensorDataSample(sensor, value, timestamp)
      sensorData.addSample(sample)

      log.debug("Got sample %s", sample)

      notifySubscribersOfNewData(sensorData)
    })
  }

  /**
   * Parse an incoming object.
   *
   * @param object
   *          the object to parse
   *
   * @return the map of data
   */
  def parseJsonObject(obj: String): Map[String, Object] = {
    try {
      val map: Map[String, Object] = MAPPER.readValue(obj, classOf[java.util.Map[String, Object]]);
      return map;
    } catch {
      case e: Throwable => throw new RuntimeException("Could not parse JSON string", e)
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
  def toJsonString(data: AnyRef): String = {
    try {
      return MAPPER.writeValueAsString(data);
    } catch {
      case e: Throwable => throw new RuntimeException("Could not serialize JSON object as string", e)
    }
  }

  /**
   * Notify all listeners of new sensor data.
   *
   * @param data
   *          the new sensor data
   */
  def notifySubscribersOfNewData(data: SensorData): Unit = {
    subscribers.foreach(subscriber => {
      if (!subscriber.isUnsubscribed) {
        try {
          subscriber.onNext(data)
        } catch {
          case e: Throwable => log.error("Error while running remote data subscriber", e)
        }
      }
    })
  }
}
