# OPCUA Component

This component does the following

* Initializes an OPCUA client object using the [Eclipse Milo Client](https://github.com/eclipse/milo/tree/master)
* APIs to read & write OPCUA node ID data
* Subscribe to changes to OPCUA node Id data
* Initializes a Greengrass IPC V2 client to subscribe, Get, update named shadows. [Learn more](https://docs.aws.amazon.com/greengrass/v2/developerguide/ipc-local-shadows.html)
* Subscribes to the changes in `opc` shadow delta topic - `$aws/things/NodeRedCore/shadow/name/opc/update/delta`
* Posts log information to Greengrass Nucleus
