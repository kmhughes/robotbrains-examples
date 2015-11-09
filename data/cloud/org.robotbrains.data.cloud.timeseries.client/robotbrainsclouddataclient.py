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

# 1 if the script should connect to the MQTT broker
# 0 if the script should not connect to the MQTT broker
CONNECT = 0

# 0 if no debug, 1 if debug
DEBUG = 1

# The number of seconds between sampling periods
SAMPLING_PERIOD=5

# The source of the data.
#
# This is a string that identifies the collection of sensors, for example, everything in
# your home.
DATA_SOURCE = 'keith.test'

# The actual sensing unit that is collecting the data.
#
# The thought here is that you would have multiple sensing units per data source.
SENSING_UNIT = 'pi2'

#
# Set which pins on the MCP3008 are connected to which sensors.
#
     
# The light sensor is connected to ADC #0
LIGHT_SENSOR = 0;

# The temperature sensor is connected to ADC #1
TEMPERATURE_SENSOR = 1

#
# Setup for GPIO on Raspberry Pi
#

# The GPIO pins on the Raspberry Pi to be used for SPI for talking to the
# MCP3008.
SPI_CLK = 21
SPI_MISO = 20
SPI_MOSI = 16
SPI_CS = 12

# Prepare the Raspberry Pi GPIO pins for the MCP3008 ADC chip.
 
GPIO.setmode(GPIO.BCM)

# set up the SPI interface pins
GPIO.setwarnings(False)
GPIO.setup(SPI_MOSI, GPIO.OUT)
GPIO.setup(SPI_MISO, GPIO.IN)
GPIO.setup(SPI_CLK, GPIO.OUT)
GPIO.setup(SPI_CS, GPIO.OUT)

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

#
# Various MQTT routines.
#

# The callback for when the MQTT client gets an acknowledgement from the MQTT
# broker.
def on_connect(client, userdata, rc):
  if DEBUG:
    print("Connected to message broker with result code "+str(rc))

# Signal handler for sigint.
#
# This is used to catch ^C to the client and will do any needed cleanup, for
# example, shut down the connection to the MQTT broker.
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

# Only create the MQTT client if we are supposed to connect to the MQTT broker.
if CONNECT:
  # Create the outgoing topic for data
  topicOutgoing = properties['smartspaces.cloud.timeseries.topic.incoming']

  # Create the MQTT client.
  mqttClient = mqtt.Client()

  # Set the methods to use for connection
  mqttClient.on_connect = on_connect

  # Set the user name and password from the properties
  mqttClient.username_pw_set(properties['mqtt.username'], properties['mqtt.password'])

  # Connect to the broker.
  mqttClient.connect(properties['mqtt.server.host'], int(properties['mqtt.server.port']), 60)

  # This method will not return and will continually loop to receive network
  # traffic.
  mqttClient.loop_start()

#
# The sensor reading loop.
#

# This loop reads the sensors.
# If DEBUG is 1, will print out the data on the console.
# If CONNECT is 1, it will send data to the MQTT broker.

while True:
  # read the sensors
  timestamp = int(round(time.time() * 1000))
  lightValue = readadc(LIGHT_SENSOR, SPI_CLK, SPI_MOSI, SPI_MISO, SPI_CS)
  temperatureValue = readadc(TEMPERATURE_SENSOR, SPI_CLK, SPI_MOSI, SPI_MISO, SPI_CS)
 
  # If supposed to, write the sensor values on the console.
  if DEBUG:
    print "lightValue:", lightValue
    print "temperatureValue:", temperatureValue

  # Write data to the MQTT broker if supposed to.
  if CONNECT:
    message = {
      'type': 'data.sensor',
      'data': {
        'source': DATA_SOURCE,
        'sensingunit': SENSING_UNIT,
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
