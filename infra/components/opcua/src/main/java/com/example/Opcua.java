
/*
* // Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
* // SPDX-License-Identifier: MIT-0
*/

package com.example;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.structured.ServerStatusDataType;
import org.json.JSONObject;
// logger
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import software.amazon.awssdk.aws.greengrass.model.BinaryMessage;
import software.amazon.awssdk.aws.greengrass.model.SubscriptionResponseMessage;
// read namespaces
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerTypeNode;
import org.eclipse.milo.opcua.sdk.client.model.nodes.variables.ServerStatusTypeNode;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;

// browse 
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.toList;
import org.eclipse.milo.opcua.sdk.client.nodes.UaVariableNode;
import java.util.List;
import java.util.Arrays;
import static com.google.common.collect.Lists.newArrayList;

public class Opcua implements ClientConfig {
    // must be configured in nucleus by enabling
    // interpolateComponentConfiguration refer
    // https://docs.aws.amazon.com/greengrass/v2/developerguide/greengrass-nucleus-component.html#greengrass-nucleus-component-configuration-interpolate-component-configuration
    public static final String THING_NAME = System.getenv("AWS_IOT_THING_NAME");
    public static final String OPC_SHADOW_TOPIC_BASE = "$aws/things/" + THING_NAME + "/shadow/name/opc";
    private static final Logger logger = LoggerFactory.getLogger(Opcua.class);
    private static final String TURBINE_STATUS_STRING = "TurbineStatus";
    private static final String TURBINE_SPEED_STRING = "TurbineSpeed";

    private static OpcUaClient opcUaClient;

    public static void main(String[] args) {
        String opcuaEndpointUrl = args[0];

        logger.debug("Welcome to OPCUA Component with OPCUA endpointURL: {}", opcuaEndpointUrl);

        // IPC massage interface callbacks
        IPCMessageInterface ipcMessageInterface = new IPCMessageInterface() {

            @Override
            public void onIPCMessage(SubscriptionResponseMessage ipcMessage) {
                BinaryMessage binaryMessage = ipcMessage.getBinaryMessage();
                String message = new String(binaryMessage.getMessage(), StandardCharsets.UTF_8);
                String topic = binaryMessage.getContext().getTopic();
                logger.debug("Got message from IPC on topic {}: {} %n", topic, message);
                try {
                    // get the current shadow state
                    String shadowDataString = IpcUtils.getShadowData(THING_NAME, "opc");
                    logger.debug("Shadow String {} %n", shadowDataString);
                    boolean turbineStatus = new JSONObject(shadowDataString).getJSONObject("state")
                            .getJSONObject("desired")
                            .getJSONObject("opcua").getBoolean(TURBINE_STATUS_STRING);

                    // synchronous connect
                    opcUaClient.connect().get();
                    writeTurbineStatus(opcUaClient, turbineStatus);

                } catch (Exception e) {
                    // upon exception try & stop the opcua GG component
                    Thread.currentThread().interrupt();
                    logger.debug("Shadow read interrupted.");
                    e.printStackTrace();
                }
            }

            @Override
            public void onIPCConnectionError(String errorMessage) {
                logger.error("Error on IPC message {}", errorMessage);
            }

            @Override
            public void onIPCConnectionClosed() {
                logger.error("IPC Stream connection closed");
            }
        };

        try {
            // initialize IPC utils
            IpcUtils.getInstance();
            logger.debug("Successfully initialized IPC Client");
            // subscribe to opc named shadows for core device from IPC
            IpcUtils.subscribeToIpc(OPC_SHADOW_TOPIC_BASE + "/update/delta", ipcMessageInterface);

            // initialize milo OPCUA client runner
            new ClientRunner(new Opcua(), opcuaEndpointUrl, false).run();

        } catch (Exception e) {
            logger.error("OPCUA Component exception occurred :{}", e.getMessage());
            e.printStackTrace();
        }

        // keep th main thread running loop to receive shadow subscriptions
        try {
            while (true) {
                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Component interrupted.");
            e.printStackTrace();
            System.exit(1);
        }

    }

    @Override
    public void run(OpcUaClient client, CompletableFuture<OpcUaClient> future) throws Exception {
        Opcua.opcUaClient = client;
        // synchronous connect
        client.connect().get();

        logger.info("OPCUA Milo Client connected!!");

        // Get a typed reference to the Server object: ServerNode
        ServerTypeNode serverNode = (ServerTypeNode) client.getAddressSpace().getObjectNode(
                Identifiers.Server,
                Identifiers.ServerType);

        // Read properties of the Server object...
        String[] serverArray = serverNode.getServerArray();
        String[] namespaceArray = serverNode.getNamespaceArray();

        logger.info("ServerArray={}", Arrays.toString(serverArray));
        logger.info("NamespaceArray={}", Arrays.toString(namespaceArray));

        // Read the value of attribute the ServerStatus variable component
        ServerStatusDataType serverStatus = serverNode.getServerStatus();

        logger.info("ServerStatus={}", serverStatus);

        // Get a typed reference to the ServerStatus variable
        // component and read value attributes individually
        ServerStatusTypeNode serverStatusNode = serverNode.getServerStatusNode();

        // this wont work for KepEx Server
        // https://stackoverflow.com/questions/45325230/throws-uaexception-for-getting-buildinfo-with-kepware-ua-server
        BuildInfo buildInfo = serverStatusNode.getBuildInfo();
        logger.info("ServerStatus.BuildInfo={}", buildInfo);
        DateTime startTime = serverStatusNode.getStartTime();
        DateTime currentTime = serverStatusNode.getCurrentTime();
        ServerState state = serverStatusNode.getState();

        logger.info("ServerStatus.StartTime={}", startTime);
        logger.info("ServerStatus.CurrentTime={}", currentTime);
        logger.info("ServerStatus.State={}", state);

        // browse nodes
        // start browsing at root folder list all the nodes
        // browseNode("1", client, Identifiers.RootFolder);

        // synchronous read request via VariableNode
        logger.debug("Initiating Read of Wind Turbine nodes -->");

        Variant turbineStatus = readOPCNode(client, 1, TURBINE_STATUS_STRING);
        Variant turbineSpeed = readOPCNode(client, 1, TURBINE_SPEED_STRING);

        JSONObject dataObject = new JSONObject();
        dataObject.put(TURBINE_STATUS_STRING, turbineStatus != null ? turbineStatus.getValue() : null);
        dataObject.put(TURBINE_SPEED_STRING, turbineSpeed != null ? turbineSpeed.getValue() : null);
        updateOpcShadow(dataObject);

        logger.debug("Setting up OPCUA Subscriptions ");
        // create a subscription
        // create a subscription @ 1000ms
        UaSubscription subscription = client.getSubscriptionManager().createSubscription(1000.0).get();

        // IMPORTANT: client handle must be unique per item within the context of a
        // subscription.
        // You are not required to use the UaSubscription's client handle sequence; it
        // is provided as a convenience.
        // Your application is free to assign client handles by whatever means
        // necessary.
        UInteger speedClientHandle = subscription.nextClientHandle();
        MonitoringParameters speedParameters = new MonitoringParameters(
                speedClientHandle,
                1000.0, // sampling interval
                null, // filter, null means use default
                uint(10), // queue size
                true // discard oldest
        );
        // subscribe to the Value attribute of the server's Turbine speed property
        ReadValueId turbineSpeedReadValueId = new ReadValueId(
                new NodeId(1, TURBINE_SPEED_STRING),
                AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);

        MonitoredItemCreateRequest turbineSpeedRequest = new MonitoredItemCreateRequest(
                turbineSpeedReadValueId,
                MonitoringMode.Reporting,
                speedParameters);

        // subscribe to the Value attribute of the server's Turbine Status property
        UInteger statusClientHandle = subscription.nextClientHandle();
        MonitoringParameters statusParameters = new MonitoringParameters(
                statusClientHandle,
                1000.0, // sampling interval
                null, // filter, null means use default
                uint(10), // queue size
                true // discard oldest
        );

        ReadValueId turbineStatusReadValueId = new ReadValueId(
                new NodeId(1, TURBINE_STATUS_STRING),
                AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);

        MonitoredItemCreateRequest turbineStatusRequest = new MonitoredItemCreateRequest(
                turbineStatusReadValueId,
                MonitoringMode.Reporting,
                statusParameters);

        // when creating items in MonitoringMode.Reporting this callback is where each
        // item needs to have its
        // value/event consumer hooked up. The alternative is to create the item in
        // sampling mode, hook up the
        // consumer after the creation call completes, and then change the mode for all
        // items to reporting.
        UaSubscription.ItemCreationCallback onItemCreated = (item, id) -> item
                .setValueConsumer(this::onSubscriptionValue);

        List<UaMonitoredItem> items = subscription.createMonitoredItems(
                TimestampsToReturn.Both,
                newArrayList(turbineSpeedRequest, turbineStatusRequest),
                onItemCreated).get();

        for (UaMonitoredItem item : items) {
            if (item.getStatusCode().isGood()) {
                logger.info("item created for nodeId={}", item.getReadValueId().getNodeId());
            } else {
                logger.warn(
                        "failed to create item for nodeId={} (status={})",
                        item.getReadValueId().getNodeId(), item.getStatusCode());
            }
        }

        try {
            while (true) {
                Thread.sleep(2000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void onSubscriptionValue(UaMonitoredItem item, DataValue value) {

        logger.info(
                "subscription value received: item={}, value={} \n\n",
                item.getReadValueId().getNodeId().getIdentifier(), value.getValue().getValue());
        JSONObject dataObject = new JSONObject();
        dataObject.put(item.getReadValueId().getNodeId().getIdentifier().toString(),
                value.getValue().getValue());
        updateOpcShadow(dataObject);
    }

    private Variant readOPCNode(OpcUaClient client, int namespaceIndex, String identifier) {
        try {
            UaVariableNode node = client.getAddressSpace().getVariableNode(
                    new NodeId(namespaceIndex, identifier));

            logger.info("DataType={}", node.getDataType());

            DataValue value = node.readValue();
            logger.info("value={}", value);

            Variant variant = value.getValue();
            logger.info("variant={}", variant.getValue());

            return variant;
        } catch (UaException e) {
            logger.error("unable to read node {}", e.getMessage());
            return null;
        }
    }

    private void updateOpcShadow(JSONObject dataObject) {
        // Update shadows
        JSONObject stateObject = new JSONObject();
        JSONObject reportedObject = new JSONObject();
        JSONObject opcuaObject = new JSONObject();
        opcuaObject.put("opcua", dataObject);
        reportedObject.put("reported", opcuaObject);
        stateObject.put("state", reportedObject);

        logger.debug("stateObject: {}", stateObject);

        IpcUtils.updateShadow(THING_NAME, "opc", stateObject.toString().getBytes());
    }

    private static void writeTurbineStatus(OpcUaClient client, boolean status) {
        try {
            UaVariableNode node = client.getAddressSpace().getVariableNode(
                    new NodeId(1, TURBINE_STATUS_STRING));
            // don't write status or timestamps
            CompletableFuture<StatusCode> f = client.writeValue(node.getNodeId(),
                    new DataValue(new Variant(status), null, null));

            StatusCode writeStatus = f.get();
            logger.info("write Status={}", writeStatus);

        } catch (UaException e) {
            logger.error("unable to write Turbine Status {}", e.getMessage());
        } catch (InterruptedException | ExecutionException e) {
            logger.error(" write Turbine Status interrupted{}", e.getMessage());
        }

    }

    private void browseNode(String indent, OpcUaClient client, NodeId browseRoot) {
        BrowseDescription browse = new BrowseDescription(
                browseRoot,
                BrowseDirection.Forward,
                Identifiers.References,
                true,
                uint(NodeClass.Object.getValue() | NodeClass.Variable.getValue()),
                uint(BrowseResultMask.All.getValue()));

        try {
            BrowseResult browseResult = client.browse(browse).get();

            List<ReferenceDescription> references = toList(browseResult.getReferences());

            for (ReferenceDescription rd : references) {
                logger.info("{} Node={}", indent, rd.getBrowseName().getName());

                // recursively browse to children
                rd.getNodeId().toNodeId(client.getNamespaceTable())
                        .ifPresent(nodeId -> browseNode(indent + "  ", client, nodeId));
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Browsing nodeId={} failed: {}", browseRoot, e.getMessage(), e);
        }
    }

}