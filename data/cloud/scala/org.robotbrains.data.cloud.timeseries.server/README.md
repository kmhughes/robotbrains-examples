# The Data Relay

The Data Relay is used to listen for data coming in from sensors and then store that data in a time series database.

This implementation uses KairosDB has the database interface.

Data is transfered from the local sensors to the relay using the Paho MQTT pub-sub library.

##Building the Data Relay

Building the Database Relay is super simple. You will need Java installed.

To build the relay, go into the root folder of the project and type

```
./gradlew distZip
```

The first time you run this command, it will download the excellent Gradle build tool. It will not download Gradle again
unless the Gradle version is changed.

The `distZip` target will build a distribution
for you that contains all needed dependencies and shell scripts for running the relay.

You will find the distribution artifacts in `build/distributions`.

