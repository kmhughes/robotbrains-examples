/*
 * Copyright (C) 2017 Keith M. Hughes.
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Properties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * An MQTT example that subscribes on a topic. It logs into the broker over a
 * secure connection using a certificate.
 * 
 * @author Keith M. Hughes
 */
public class SslCertificateSubscriberMqttExample {
  public static void main(String[] args) {

    // Memory persistence client. Since not publishing, memory persistence
    // should be fine.
    MemoryPersistence persistence = new MemoryPersistence();

    try {
      Properties credentials = new Properties();
      credentials.load(new FileInputStream(new File("/Users/keith/mqtt.properties")));

      MqttClient client = new MqttClient("ssl://smartspaces.io:8883",
          "/mqtt/subscriber/credentialed/ssl", persistence);

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

      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(true);
      // options.setUserName(credentials.getProperty("mqtt.username"));
      // options.setPassword(credentials.getProperty("mqtt.password").toCharArray());
      options.setSocketFactory(configureSSLSocketFactory(credentials));

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
    Security.addProvider(new BouncyCastleProvider());

    JcaX509CertificateConverter certificateConverter =
        new JcaX509CertificateConverter().setProvider("BC");

    String caCrtFile = credentials.getProperty("mqtt.ca.crt");
    // load CA certificate
    PEMParser reader = new PEMParser(
        new InputStreamReader(new ByteArrayInputStream(Files.readAllBytes(Paths.get(caCrtFile)))));
    X509Certificate caCert =
        certificateConverter.getCertificate((X509CertificateHolder) reader.readObject());
    reader.close();

    // load client certificate
    String crtFile = credentials.getProperty("mqtt.client.crt");
    reader = new PEMParser(
        new InputStreamReader(new ByteArrayInputStream(Files.readAllBytes(Paths.get(crtFile)))));
    X509Certificate cert =
        certificateConverter.getCertificate((X509CertificateHolder) reader.readObject());
    reader.close();

    // load client private key
    JcaPEMKeyConverter keyConverter = new JcaPEMKeyConverter().setProvider("BC");
    String keyFile = credentials.getProperty("mqtt.client.key");
    reader = new PEMParser(
        new InputStreamReader(new ByteArrayInputStream(Files.readAllBytes(Paths.get(keyFile)))));
    KeyPair key = keyConverter.getKeyPair((PEMKeyPair) reader.readObject());
    reader.close();

    // CA certificate is used to authenticate server
    KeyStore caKs = KeyStore.getInstance(KeyStore.getDefaultType());
    caKs.load(null, null);
    caKs.setCertificateEntry("ca-certificate", caCert);
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(caKs);

    // client key and certificates are sent to server so it can authenticate
    // us
    KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
    ks.load(null, null);
    ks.setCertificateEntry("certificate", cert);

    // This assumes that the client key is not password protected. We need a
    // password, but it could be anything.
    char[] password = "password".toCharArray();
    ks.setKeyEntry("private-key", key.getPrivate(), password,
        new java.security.cert.Certificate[] { cert });
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(ks, password);

    // finally, create SSL socket factory
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

    return context.getSocketFactory();
  }
}
