#!/usr/bin/env python

##
# Copyright (C) 2015 Keith M. Hughes.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
##

import yaml
import paho.mqtt.client as mqtt
import json
import sys
import time
import signal
import RPi.GPIO as GPIO

# 0 if no debug, 1 if debug
DEBUG = 1

# The number of seconds between sampling periods
SAMPLING_PERIOD=5

# Raspberry Pi GPIO bit-banging code
# Written by Limor "Ladyada" Fried for Adafruit Industries, (c) 2015
# This code is released into the public domain

# read SPI data from MCP3008 chip, 8 possible adc's (0 thru 7)
def readadc(adcnum, clockpin, mosipin, misopin, cspin):
  if ((adcnum > 7) or (adcnum < 0)):
    return -1
  GPIO.output(cspin, True)

  GPIO.output(clockpin, False)  # start clock low
  GPIO.output(cspin, False)     # bring CS low

  commandout = adcnum
  commandout |= 0x18  # start bit + single-ended bit
  commandout <<= 3    # we only need to send 5 bits here
  for i in range(5):
    if (commandout & 0x80):
      GPIO.output(mosipin, True)
    else:
      GPIO.output(mosipin, False)
    commandout <<= 1
    GPIO.output(clockpin, True)
    GPIO.output(clockpin, False)

  adcout = 0
  # read in one empty bit, one null bit and 10 ADC bits
  for i in range(12):
    GPIO.output(clockpin, True)
    GPIO.output(clockpin, False)
    adcout <<= 1
    if (GPIO.input(misopin)):
      adcout |= 0x1

  GPIO.output(cspin, True)

  adcout >>= 1       # first bit is 'null' so drop it
  return adcout

# The callback for when the MQTT client gets an acknowledgement from the MQTT
# server.
def on_connect(client, userdata, rc):
  if DEBUG:
    print("Connected to message broker with result code "+str(rc))

  # Only subscribe if the connection was successful
  if rc == 0:
    if DEBUG:
      # Subscribing in on_connect() means that if we lose the connection and
      # reconnect then subscriptions will be renewed.
      client.subscribe(topicOutgoing)

# The callback for when te MQTT client receives a publiched message for a
# topic it is subscribed to
def on_message(client, userdata, msg):
    print(msg.topic+" "+str(msg.payload))

# Signal handler for sigint
def signal_handler(signal, frame):
  mqttClient.loop_stop()
  sys.exit(0)

signal.signal(signal.SIGINT, signal_handler)

# Read the properties that define user names and passwords.
#
# The configuration file is in YAML.
# be used in both languages.
with open(sys.argv[1]) as fp:
  properties = yaml.safe_load(fp)

# Prepare the Raspberry Pi GPIO pins for the MCP3008 ADC chip.
 
GPIO.setmode(GPIO.BCM)

# change these as desired - they're the pins connected from the
# SPI port on the ADC to the Cobbler
SPICLK = 21
SPIMISO = 20
SPIMOSI = 16
SPICS = 12

# set up the SPI interface pins
GPIO.setwarnings(False)
GPIO.setup(SPIMOSI, GPIO.OUT)
GPIO.setup(SPIMISO, GPIO.IN)
GPIO.setup(SPICLK, GPIO.OUT)
GPIO.setup(SPICS, GPIO.OUT)

# Create the outgoing topic for data
topicOutgoing = properties['smartspaces.cloud.timeseries.topic.incoming']

# Create the MQTT client.
mqttClient = mqtt.Client()

# Set the methods to use for connection and message receiving
mqttClient.on_connect = on_connect
mqttClient.on_message = on_message

# Set the user name and password from the properties
mqttClient.username_pw_set(properties['mqtt.username'], properties['mqtt.password'])

# Connect to the server.
mqttClient.connect(properties['mqtt.server.host'], int(properties['mqtt.server.port']), 60)

# This method will not return and will continually loop to receive network
# traffic.
mqttClient.loop_start()
     
# Light sensor connected to adc #0
light_sensor = 0;

# Temperature sensor connected to adc #1
temperature_sensor = 1

while True:
  # read the sensors
  timestamp = int(round(time.time() * 1000))
  lightValue = readadc(light_sensor, SPICLK, SPIMOSI, SPIMISO, SPICS)
  temperatureValue = readadc(temperature_sensor, SPICLK, SPIMOSI, SPIMISO, SPICS)
 
  if DEBUG:
    print "lightValue:", lightValue
    print "temperatureValue:", temperatureValue

  message = {
    'type': 'data.sensor',
    'data': {
      'source': 'keith.test',
      'sensingunit': 'pi2',
      'sensor.data': [
        {
          'sensor': 'temperature',
          'value': temperatureValue,
          'timestamp': timestamp
          },
        {
          'sensor': 'light',
          'value' : lightValue,
          'timestamp': timestamp
          }
        ]
      }
    }

  mqttClient.publish(topicOutgoing, json.dumps(message))

  # Wait the sampling period before sampling again
  time.sleep(SAMPLING_PERIOD)
