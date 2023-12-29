# OPCDA Component

This component does the following

* Initializes an OPCDA client object using the [OPen OPC Library](https://openopc.sourceforge.net/api.html)
* APIs to read & write OPCDA tags data
* Initializes a Greengrass IPC V2 client to subscribe, Get, update named shadows. [Learn more](https://docs.aws.amazon.com/greengrass/v2/developerguide/ipc-local-shadows.html)
* Subscribes to the changes in `opc` shadow delta topic - `$aws/things/NodeRedCore/shadow/name/opc/update/delta`
* Posts log information to Greengrass Nucleus
