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

import jprops
import paho.mqtt.client as mqtt
import json

# The callback for when the MQTT client gets an acknowledgement from the MQTT
# server.
def on_connect(client, userdata, rc):
    print("Connected with result code "+str(rc))

    # Only subscribe if the connection was successful
    if rc eq 0:
      # Subscribing in on_connect() means that if we lose the connection and
      # reconnect then subscriptions will be renewed.
      client.subscribe("/greeting")
      data = { 'foo' : 1}
      client.publish("/greeting", json.dumps(data))

# The callback for when te MQTT client receives a publiched message for a
# topic it is subscribed to
def on_message(client, userdata, msg):
    print(msg.topic+" "+str(msg.payload))

# Read the properties that define user names and passwords.
#
# A Java properties file is being used so that the same properties can
# be used in both languages.
with open('/Users/keith/mqtt.properties') as fp:
  properties = jprops.load_properties(fp)

# Create the client.
client = mqtt.Client()

# Set the methods to use for connection and message receiving
client.on_connect = on_connect
client.on_message = on_message

# Set the user name and password from the properties
client.username_pw_set(properties['mqtt.username'], properties['mqtt.password'])

# Connect to the server.
client.connect("smartspaces.io", 1883, 60)

# This method will not return and will continually loop to receive network
# traffic.
client.loop_forever()
