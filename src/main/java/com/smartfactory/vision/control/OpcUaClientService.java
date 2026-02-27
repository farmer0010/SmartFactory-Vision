package com.smartfactory.vision.control;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

@Slf4j
@Service
public class OpcUaClientService {

    @Value("${app.opcua.endpoint-url}")
    private String endpointUrl;

    @Value("${app.opcua.nodes.status}")
    private String statusNodeIdStr;

    @Value("${app.opcua.nodes.estop}")
    private String estopNodeIdStr;

    @Value("${app.opcua.nodes.reject-kicker}")
    private String rejectKickerNodeIdStr;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private OpcUaClient client;

    public void triggerEStop() {
        log.warn(">>> Executing OPC UA E-STOP Write <<<");
        writeBooleanNode(estopNodeIdStr, true);
    }

    public void resetPlc() {
        log.info(">>> Executing OPC UA Reset Write <<<");
        writeBooleanNode(estopNodeIdStr, false);
        writeBooleanNode(rejectKickerNodeIdStr, false);
    }

    public void triggerRejectKicker() {
        log.info(">>> Executing OPC UA RejectKicker Write <<<");
        writeBooleanNode(rejectKickerNodeIdStr, true);
    }

    @PostConstruct
    public void init() {
        try {
            connect();
            subscribeToStatus();
        } catch (Exception e) {
            log.warn("Failed to connect to OPC UA server at startup. Continuing without PLC integration: {}",
                    e.getMessage());
        }
    }

    private void connect() throws Exception {
        log.info("Connecting to OPC UA Server at {}", endpointUrl);

        List<EndpointDescription> endpoints = DiscoveryClient.getEndpoints(endpointUrl).get();

        EndpointDescription endpoint = endpoints.stream()
                .filter(e -> e.getSecurityPolicyUri().equals(SecurityPolicy.None.getUri()))
                .findFirst()
                .orElseThrow(() -> new Exception("no desired endpoints returned"));

        OpcUaClientConfig config = OpcUaClientConfig.builder()
                .setApplicationName(
                        org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText.english("SmartFactory-Vision"))
                .setApplicationUri("urn:eclipse:milo:smartfactory")
                .setEndpoint(endpoint)
                .build();

        client = OpcUaClient.create(config);
        client.connect().get();
        log.info("Successfully connected to OPC UA Server");
    }

    private void subscribeToStatus() {
        try {
            NodeId statusNodeId = NodeId.parse(statusNodeIdStr);
            UaSubscription subscription = client.getSubscriptionManager().createSubscription(1000.0).get();

            ReadValueId readValueId = new ReadValueId(
                    statusNodeId,
                    AttributeId.Value.uid(),
                    null,
                    org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName.NULL_VALUE);

            org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger clientHandle = uint(1);

            MonitoringParameters parameters = new MonitoringParameters(
                    clientHandle,
                    1000.0, 
                    null, 
                    uint(10), 
                    true 
            );

            MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
                    readValueId,
                    MonitoringMode.Reporting,
                    parameters);

            List<UaMonitoredItem> items = subscription.createMonitoredItems(
                    TimestampsToReturn.Both,
                    List.of(request),
                    (item, id) -> item.setValueConsumer(this::onStatusNodeValueChanged)).get();

            log.info("Subscribed to PLC Status node: {}", statusNodeIdStr);
        } catch (Exception e) {
            log.warn("Failed to subscribe to Status node: {}", e.getMessage());
        }
    }

    private void onStatusNodeValueChanged(UaMonitoredItem item, DataValue value) {
        Object val = value.getValue().getValue();
        log.info("[PLC Status Changed] Node: {}, New Value: {}", item.getReadValueId().getNodeId(), val);

        messagingTemplate.convertAndSend("/topic/plc/status", "{\"status\": \"" + val + "\"}");
    }

    public void writeBooleanNode(String nodeIdString, boolean value) {
        if (client == null) {
            log.debug("OPC UA Client not connected, ignoring write: {}", nodeIdString);
            return;
        }

        try {
            NodeId targetNodeId = NodeId.parse(nodeIdString);
            Variant v = new Variant(value);
            DataValue dataValue = new DataValue(v, null, null);

            CompletableFuture<org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode> future = client
                    .writeValue(targetNodeId, dataValue);

            future.thenAccept(status -> {
                if (status.isGood()) {
                    log.info("Successfully wrote {} to Node: {}", value, nodeIdString);
                } else {
                    log.error("Failed to write {} to Node: {}. Status: {}", value, nodeIdString, status);
                }
            }).exceptionally(ex -> {
                log.error("Exception writing to node {}", nodeIdString, ex);
                return null;
            });

        } catch (Exception e) {
            log.error("Error writing to node: {}", nodeIdString, e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            try {
                client.disconnect().get();
                log.info("Disconnected from OPC UA Server");
            } catch (Exception e) {
                log.error("Error disconnecting from OPC UA Server", e);
            }
        }
    }
}
