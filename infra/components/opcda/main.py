import sys
import os
import traceback
import signal
import time
import json

import OpenOPC
import pywintypes

from awsiot.greengrasscoreipc.clientv2 import GreengrassCoreIPCClientV2
from awsiot.greengrasscoreipc.model import (
    QOS,
    SubscriptionResponseMessage,
    GetThingShadowRequest,
    UnauthorizedError,
    InvalidArgumentsError
)

import threading
import traceback


pywintypes.datetime = pywintypes.TimeType
qos = QOS.AT_LEAST_ONCE
TIMEOUT = 10
ipc_client = None
stop_event = threading.Event()
flag_previous_value = 0


def handler(signum, frame):
    msg = "Exiting OPCDA Component"
    stop_event.set()
    try:
        print(msg, end="", flush=True)
        # opc.close()
    except RuntimeError:
     # print('Handling run-time error:', err)
        pass
    sys.exit(0)


def opc_scan_thread():
    print("Starting OPCDA scanning thread")
    opc = OpenOPC.client()
    opc.connect('Matrikon.OPC.Simulation')
    print("connected to Matrikon OPCDA server")
    while not stop_event.is_set():
        try:
            opc_raw = opc.read(opc.list('TurbineSensors.*', flat=True))
            turbine_sensor_data = []
            for t in opc_raw:
                turbine_sensor_data.append(
                    {"name": t[0], "value": t[1], "status": t[2]})
            shadow_payload = {"state": {"reported": {
                "opcda": turbine_sensor_data}}}
            print(shadow_payload)

            # update shadow
            ipc_client.update_thing_shadow(
                thing_name=os.environ['AWS_IOT_THING_NAME'],
                shadow_name=shadow_name,
                payload=json.dumps(shadow_payload))
        except Exception as e:
            print(e)
            traceback.print_exc()
        # sleep in  seconds
        time.sleep(10)
    # disconnect the client
    opc.close()


def main():
    # handle program exit
    signal.signal(signal.SIGINT, handler)

    args = sys.argv[1:]
    global shadow_name
    shadow_name = args[0]
    print("Welcome to OPCDA Component ")
    thing_name = os.environ['AWS_IOT_THING_NAME']
    opc_shadow_topic_base = "$aws/things/" + \
        thing_name + "/shadow/name/" + shadow_name + "/update/delta"

    print("opc_shadow_topic_base: ", opc_shadow_topic_base)

    try:
        global ipc_client
        ipc_client = GreengrassCoreIPCClientV2()
        # Subscription operations return a tuple with the response and the operation.
        _, operation = ipc_client.subscribe_to_topic(topic=opc_shadow_topic_base, on_stream_event=on_stream_event,
                                                     on_stream_error=on_stream_error, on_stream_closed=on_stream_closed)
        print('Successfully subscribed to topic: ' + opc_shadow_topic_base)

        # OPCDA scan thread
        th = threading.Thread(target=opc_scan_thread)
        th.start()
        th.join()

        # Keep the main thread alive, or the process will exit.
        # try:
        #     while True:
        #         time.sleep(10)
        # except InterruptedError:
        #     print('Subscribe interrupted.')

        # To stop subscribing, close the stream.
        print("Exiting OPCDA Component")
        operation.close()
    except UnauthorizedError:
        print('Unauthorized error while subscribing to topic: ' +
              opc_shadow_topic_base, file=sys.stderr)
        traceback.print_exc()
        sys.exit(1)
    except Exception:
        print('Exception occurred', file=sys.stderr)
        traceback.print_exc()
        sys.exit(1)


def on_stream_event(event: SubscriptionResponseMessage) -> None:
    try:
        message = str(event.binary_message.message, 'utf-8')
        topic = event.binary_message.context.topic
        print('Received new message on topic %s: %s' % (topic, message))
        # retrieve the GetThingShadow response after sending the request to the IPC server
        result = ipc_client.get_thing_shadow(
            thing_name=os.environ['AWS_IOT_THING_NAME'], shadow_name=shadow_name)
        print(result.payload)
        payload = json.loads(result.payload)
        desired_value = payload["state"]["desired"]["opcda"]["flag"]
        print("opc_flag_desired_value: ", desired_value)
        global flag_previous_value
        if (desired_value != flag_previous_value):
            # create a new client
            opc = OpenOPC.client()
            opc.connect('Matrikon.OPC.Simulation')
            print("connected to Matrikon OPCDA server to write -->", desired_value)
            print(opc.write([('TurbineSensors.Flag',  desired_value)]))
            opc.close()
            # update previous value
            flag_previous_value = desired_value

    except InvalidArgumentsError:
        print("Invalid arguments error on stream event: ", file=sys.stderr)
        traceback.print_exc()
    except:
        print("Exception on stream event: ", file=sys.stderr)
        traceback.print_exc()


def on_stream_error(error: Exception) -> bool:
    print('Received a stream error.', file=sys.stderr)
    traceback.print_exc()
    return False  # Return True to close stream, False to keep stream open.


def on_stream_closed() -> None:
    print('Subscribe to topic stream closed.')


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print('Interrupted')
        try:
            sys.exit(130)
        except SystemExit:
            os._exit(130)
