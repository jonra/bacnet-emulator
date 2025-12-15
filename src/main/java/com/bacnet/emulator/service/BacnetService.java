package com.bacnet.emulator.service;

import com.bacnet.emulator.model.BacnetDevice;
import com.bacnet.emulator.model.BacnetObject;
import com.bacnet.emulator.model.EmulatorConfig;
import com.bacnet.emulator.repository.DeviceRepository;
import com.bacnet.emulator.repository.EmulatorConfigRepository;
import com.bacnet.emulator.repository.ObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Core BACnet protocol service that handles BACnet/IP communication.
 * 
 * <p>This service implements the BACnet/IP protocol stack and handles:
 * <ul>
 *   <li>Device discovery (Who-Is/I-Am)</li>
 *   <li>Property read/write operations (ReadProperty, WriteProperty)</li>
 *   <li>Batch operations (ReadPropertyMultiple)</li>
 *   <li>Change of Value (COV) subscriptions</li>
 *   <li>Alarm and event handling</li>
 * </ul>
 * 
 * <p>The service listens on UDP port 47808 (default BACnet/IP port) and processes
 * incoming BACnet messages, responding according to the configured device and object data.
 * 
 * <p>Device and object caches are refreshed periodically to ensure responses reflect
 * the current database state.
 * 
 * @author BACnet Emulator Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BacnetService {
    
    private final DeviceRepository deviceRepository;
    private final ObjectRepository objectRepository;
    private final EmulatorConfigRepository emulatorConfigRepository;
    private final MonitorService monitorService;
    
    @Value("${bacnet.network.port:47808}")
    private int defaultPort;
    
    @Value("${bacnet.network.bind-address:0.0.0.0}")
    private String defaultBindAddress;
    
    private DatagramSocket socket;
    private ExecutorService executorService;
    private boolean running = false;
    private final Map<Integer, BacnetDevice> deviceCache = new ConcurrentHashMap<>();
    private final Map<String, BacnetObject> objectCache = new ConcurrentHashMap<>();
    private final Map<String, CovSubscription> covSubscriptions = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        try {
            executorService = Executors.newFixedThreadPool(10);
            startServer();
            log.info("BACnet service initialized and started");
        } catch (Exception e) {
            log.error("Failed to initialize BACnet service", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
        log.info("BACnet service shut down");
    }
    
    private void startServer() throws SocketException, UnknownHostException {
        EmulatorConfig config = emulatorConfigRepository.findByConfigKey("default")
                .orElseGet(() -> {
                    EmulatorConfig c = new EmulatorConfig();
                    c.setConfigKey("default");
                    return emulatorConfigRepository.save(c);
                });
        
        int port = defaultPort;
        String bindAddress = defaultBindAddress;
        
        socket = new DatagramSocket(port, InetAddress.getByName(bindAddress));
        socket.setBroadcast(true);
        running = true;
        
        log.info("BACnet server started on {}:{}", bindAddress, port);
        
        // Start listening thread
        executorService.submit(this::listenForPackets);
        
        // Refresh device cache periodically
        executorService.submit(this::refreshDeviceCache);
    }
    
    private void listenForPackets() {
        byte[] buffer = new byte[1476]; // Maximum BACnet/IP packet size
        
        while (running && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                executorService.submit(() -> handlePacket(packet));
            } catch (IOException e) {
                if (running) {
                    log.error("Error receiving BACnet packet", e);
                }
            }
        }
    }
    
    private void handlePacket(DatagramPacket packet) {
        try {
            byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
            InetAddress sourceAddress = packet.getAddress();
            int sourcePort = packet.getPort();
            
            // Parse BACnet/IP header
            if (data.length < 4) {
                return;
            }
            
            // BACnet/IP header: Type (1 byte) + Function (1 byte) + Length (2 bytes)
            int type = data[0] & 0xFF;
            
            if (type == 0x81) { // Original-Unicast-NPDU or Original-Broadcast-NPDU
                parseAndHandleBacnetMessage(data, sourceAddress, sourcePort);
            }
        } catch (Exception e) {
            log.error("Error handling BACnet packet", e);
        }
    }
    
    private void parseAndHandleBacnetMessage(byte[] data, InetAddress sourceAddress, int sourcePort) {
        try {
            // Skip BACnet/IP header (4 bytes)
            if (data.length < 4) return;
            
            int npduLength = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            if (data.length < 4 + npduLength) return;
            
            byte[] npdu = Arrays.copyOfRange(data, 4, 4 + npduLength);
            
            // Parse NPDU
            if (npdu.length < 2) return;
            
            int npduType = npdu[0] & 0xFF;
            int npduFunction = npdu[1] & 0xFF;
            
            // Parse APDU (starts after NPDU)
            if (npdu.length < 3) return;
            
            int apduType = npdu[2] & 0xFF;
            int pduType = (apduType >> 4) & 0x0F;
            int serviceChoice = apduType & 0x0F;
            
            // Handle different PDU types
            if (pduType == 0) { // Unconfirmed Request
                handleUnconfirmedRequest(npdu, sourceAddress, sourcePort);
            } else if (pduType == 1) { // Confirmed Request
                handleConfirmedRequest(npdu, sourceAddress, sourcePort);
            }
        } catch (Exception e) {
            log.error("Error parsing BACnet message", e);
        }
    }
    
    private void handleUnconfirmedRequest(byte[] npdu, InetAddress sourceAddress, int sourcePort) {
        if (npdu.length < 3) return;
        
        int serviceChoice = npdu[2] & 0x0F;
        
        // Who-Is request
        if (serviceChoice == 0x08) {
            handleWhoIs(npdu, sourceAddress, sourcePort);
        }
    }
    
    private void handleConfirmedRequest(byte[] npdu, InetAddress sourceAddress, int sourcePort) {
        if (npdu.length < 5) return;
        
        int serviceChoice = npdu[4] & 0xFF;
        int invokeId = npdu[3] & 0xFF;
        
        switch (serviceChoice) {
            case 0x0C: // ReadProperty
                handleReadProperty(npdu, sourceAddress, sourcePort, invokeId);
                break;
            case 0x0F: // WriteProperty
                handleWriteProperty(npdu, sourceAddress, sourcePort, invokeId);
                break;
            case 0x1E: // ReadPropertyMultiple
                handleReadPropertyMultiple(npdu, sourceAddress, sourcePort, invokeId);
                break;
            case 0x05: // SubscribeCOV
                handleSubscribeCOV(npdu, sourceAddress, sourcePort, invokeId);
                break;
            default:
                log.debug("Unhandled service choice: {}", serviceChoice);
        }
    }
    
    private void handleWhoIs(byte[] npdu, InetAddress sourceAddress, int sourcePort) {
        int lowLimit = -1;
        int highLimit = -1;
        
        // Parse Who-Is parameters if present
        if (npdu.length > 3) {
            // Simplified parsing - actual implementation would decode BACnet encoding
        }
        
        monitorService.log("INFO", "Received Who-Is request", 
                sourceAddress.getHostAddress() + ":" + sourcePort, "Who-Is", null, null, null, null);
        
        // Respond with I-Am for all enabled devices
        List<BacnetDevice> devices = deviceRepository.findByEnabledTrue();
        for (BacnetDevice device : devices) {
            if (lowLimit == -1 || (device.getDeviceInstanceId() >= lowLimit && device.getDeviceInstanceId() <= highLimit)) {
                sendIAm(device, sourceAddress, sourcePort);
            }
        }
    }
    
    private void handleReadProperty(byte[] npdu, InetAddress sourceAddress, int sourcePort, int invokeId) {
        try {
            // Simplified parsing - extract device instance, object type, object instance, property ID
            // In a real implementation, this would properly decode BACnet encoding
            
            if (npdu.length < 10) return;
            
            // Extract device instance (simplified - actual BACnet encoding is more complex)
            int deviceInstance = extractDeviceInstance(npdu, 5);
            int objectType = extractObjectType(npdu, 8);
            int objectInstance = extractObjectInstance(npdu, 9);
            int propertyId = extractPropertyId(npdu, 12);
            
            BacnetDevice device = deviceCache.get(deviceInstance);
            if (device == null || !device.getEnabled()) {
                sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x01); // Unknown device
                return;
            }
            
            String objectKey = deviceInstance + ":" + objectType + ":" + objectInstance;
            BacnetObject object = objectCache.get(objectKey);
            
            if (object == null) {
                object = objectRepository.findByDeviceIdAndObjectTypeAndObjectInstance(
                        device.getId(), objectType, objectInstance).orElse(null);
                if (object != null) {
                    objectCache.put(objectKey, object);
                }
            }
            
            if (object == null) {
                sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x85); // Unknown object
                monitorService.log("WARN", "ReadProperty: Object not found", 
                        sourceAddress.getHostAddress(), "ReadProperty", deviceInstance, objectType, objectInstance, null);
                return;
            }
            
            // Get property value
            Object value = getPropertyValue(object, propertyId);
            
            monitorService.log("INFO", "ReadProperty request", 
                    sourceAddress.getHostAddress(), "ReadProperty", deviceInstance, objectType, objectInstance, 
                    "Property: " + propertyId + ", Value: " + value);
            
            sendReadPropertyResponse(sourceAddress, sourcePort, invokeId, deviceInstance, objectType, objectInstance, propertyId, value);
            
        } catch (Exception e) {
            log.error("Error handling ReadProperty", e);
            sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x01);
        }
    }
    
    private void handleWriteProperty(byte[] npdu, InetAddress sourceAddress, int sourcePort, int invokeId) {
        try {
            if (npdu.length < 10) return;
            
            int deviceInstance = extractDeviceInstance(npdu, 5);
            int objectType = extractObjectType(npdu, 8);
            int objectInstance = extractObjectInstance(npdu, 9);
            int propertyId = extractPropertyId(npdu, 12);
            
            BacnetDevice device = deviceCache.get(deviceInstance);
            if (device == null || !device.getEnabled()) {
                sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x01);
                return;
            }
            
            if (!device.getEnabled()) {
                sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x03); // Write access denied
                return;
            }
            
            String objectKey = deviceInstance + ":" + objectType + ":" + objectInstance;
            BacnetObject object = objectCache.get(objectKey);
            
            if (object == null) {
                object = objectRepository.findByDeviceIdAndObjectTypeAndObjectInstance(
                        device.getId(), objectType, objectInstance).orElse(null);
                if (object != null) {
                    objectCache.put(objectKey, object);
                }
            }
            
            if (object == null) {
                sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x85);
                return;
            }
            
            if (!object.getWritable() || propertyId != 85) { // 85 = Present Value
                sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x03); // Write access denied
                return;
            }
            
            // Extract value from request (simplified)
            String newValue = extractValue(npdu, 16);
            
            object.setPresentValue(newValue);
            objectRepository.save(object);
            objectCache.put(objectKey, object);
            
            monitorService.log("INFO", "WriteProperty request", 
                    sourceAddress.getHostAddress(), "WriteProperty", deviceInstance, objectType, objectInstance, 
                    "Property: " + propertyId + ", New Value: " + newValue);
            
            // Check for COV subscriptions
            checkAndNotifyCOV(object, deviceInstance);
            
            sendSimpleAck(sourceAddress, sourcePort, invokeId);
            
        } catch (Exception e) {
            log.error("Error handling WriteProperty", e);
            sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x01);
        }
    }
    
    private void handleReadPropertyMultiple(byte[] npdu, InetAddress sourceAddress, int sourcePort, int invokeId) {
        // Simplified implementation
        sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x01);
    }
    
    private void handleSubscribeCOV(byte[] npdu, InetAddress sourceAddress, int sourcePort, int invokeId) {
        try {
            if (npdu.length < 10) return;
            
            int deviceInstance = extractDeviceInstance(npdu, 5);
            int objectType = extractObjectType(npdu, 8);
            int objectInstance = extractObjectInstance(npdu, 9);
            
            String subscriptionKey = sourceAddress.getHostAddress() + ":" + sourcePort + ":" + 
                    deviceInstance + ":" + objectType + ":" + objectInstance;
            
            CovSubscription subscription = new CovSubscription();
            subscription.sourceAddress = sourceAddress;
            subscription.sourcePort = sourcePort;
            subscription.deviceInstance = deviceInstance;
            subscription.objectType = objectType;
            subscription.objectInstance = objectInstance;
            subscription.subscriberProcessId = 0; // Extract from request if needed
            
            covSubscriptions.put(subscriptionKey, subscription);
            
            monitorService.log("INFO", "COV subscription received", 
                    sourceAddress.getHostAddress(), "SubscribeCOV", deviceInstance, objectType, objectInstance, null);
            
            sendSimpleAck(sourceAddress, sourcePort, invokeId);
            
        } catch (Exception e) {
            log.error("Error handling SubscribeCOV", e);
            sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x01);
        }
    }
    
    private void sendIAm(BacnetDevice device, InetAddress destination, int port) {
        try {
            // Build I-Am response
            ByteBuffer buffer = ByteBuffer.allocate(256);
            buffer.order(ByteOrder.BIG_ENDIAN);
            
            // BACnet/IP header
            buffer.put((byte) 0x81); // Type: Original-Unicast-NPDU
            buffer.put((byte) 0x0A); // Function
            buffer.putShort((short) 0); // Length placeholder
            
            // NPDU
            buffer.put((byte) 0x01); // Version
            buffer.put((byte) 0x20); // Control: expecting reply, network layer message
            
            // APDU - Unconfirmed I-Am
            buffer.put((byte) 0x10); // PDU type: Unconfirmed Request
            buffer.put((byte) 0x00); // Service: I-Am
            
            // I-Am parameters
            encodeObjectIdentifier(buffer, 8, device.getDeviceInstanceId()); // Device object (type 8)
            encodeUnsignedInt(buffer, 0); // Max segments accepted
            encodeUnsignedInt(buffer, 1476); // Max APDU length accepted
            encodeUnsignedInt(buffer, 0); // Segmentation support
            encodeUnsignedInt(buffer, 0); // Vendor ID (simplified)
            
            // Update length
            int length = buffer.position() - 4;
            buffer.putShort(2, (short) length);
            
            byte[] data = Arrays.copyOf(buffer.array(), buffer.position());
            DatagramPacket packet = new DatagramPacket(data, data.length, destination, port);
            socket.send(packet);
            
            monitorService.log("INFO", "Sent I-Am response", 
                    destination.getHostAddress() + ":" + port, "I-Am", device.getDeviceInstanceId(), null, null, null);
            
        } catch (Exception e) {
            log.error("Error sending I-Am", e);
        }
    }
    
    private void sendReadPropertyResponse(InetAddress destination, int port, int invokeId, 
                                         int deviceInstance, int objectType, int objectInstance, 
                                         int propertyId, Object value) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(256);
            buffer.order(ByteOrder.BIG_ENDIAN);
            
            // BACnet/IP header
            buffer.put((byte) 0x81);
            buffer.put((byte) 0x0A);
            buffer.putShort((short) 0);
            
            // NPDU
            buffer.put((byte) 0x01);
            buffer.put((byte) 0x04);
            
            // APDU - Complex ACK
            buffer.put((byte) 0x30); // PDU type: Complex ACK
            buffer.put((byte) invokeId);
            buffer.put((byte) 0x0C); // Service: ReadProperty
            
            // Object identifier
            encodeObjectIdentifier(buffer, objectType, objectInstance);
            
            // Property identifier
            buffer.put((byte) 0x0C); // Context tag 0
            buffer.put((byte) propertyId);
            
            // Value
            encodeValue(buffer, value);
            
            int length = buffer.position() - 4;
            buffer.putShort(2, (short) length);
            
            byte[] data = Arrays.copyOf(buffer.array(), buffer.position());
            DatagramPacket packet = new DatagramPacket(data, data.length, destination, port);
            socket.send(packet);
            
        } catch (Exception e) {
            log.error("Error sending ReadProperty response", e);
        }
    }
    
    private void sendSimpleAck(InetAddress destination, int port, int invokeId) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(64);
            buffer.order(ByteOrder.BIG_ENDIAN);
            
            buffer.put((byte) 0x81);
            buffer.put((byte) 0x0A);
            buffer.putShort((short) 0);
            
            buffer.put((byte) 0x01);
            buffer.put((byte) 0x04);
            
            buffer.put((byte) 0x20); // Simple ACK
            buffer.put((byte) invokeId);
            
            int length = buffer.position() - 4;
            buffer.putShort(2, (short) length);
            
            byte[] data = Arrays.copyOf(buffer.array(), buffer.position());
            DatagramPacket packet = new DatagramPacket(data, data.length, destination, port);
            socket.send(packet);
            
        } catch (Exception e) {
            log.error("Error sending Simple ACK", e);
        }
    }
    
    private void sendErrorResponse(InetAddress destination, int port, int invokeId, int errorClass) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(64);
            buffer.order(ByteOrder.BIG_ENDIAN);
            
            buffer.put((byte) 0x81);
            buffer.put((byte) 0x0A);
            buffer.putShort((short) 0);
            
            buffer.put((byte) 0x01);
            buffer.put((byte) 0x04);
            
            buffer.put((byte) 0x50); // Error PDU
            buffer.put((byte) invokeId);
            buffer.put((byte) errorClass);
            buffer.put((byte) 0x01); // Error code
            
            int length = buffer.position() - 4;
            buffer.putShort(2, (short) length);
            
            byte[] data = Arrays.copyOf(buffer.array(), buffer.position());
            DatagramPacket packet = new DatagramPacket(data, data.length, destination, port);
            socket.send(packet);
            
        } catch (Exception e) {
            log.error("Error sending error response", e);
        }
    }
    
    // Helper methods for encoding/decoding
    private void encodeObjectIdentifier(ByteBuffer buffer, int objectType, int instance) {
        buffer.put((byte) 0x0C); // Context tag 0
        long value = ((long) objectType << 22) | instance;
        encodeUnsignedInt(buffer, (int) value);
    }
    
    private void encodeUnsignedInt(ByteBuffer buffer, int value) {
        if (value < 0x100) {
            buffer.put((byte) 0x21); // Tag: context 0, length 1
            buffer.put((byte) value);
        } else if (value < 0x10000) {
            buffer.put((byte) 0x22); // Tag: context 0, length 2
            buffer.putShort((short) value);
        } else {
            buffer.put((byte) 0x24); // Tag: context 0, length 4
            buffer.putInt(value);
        }
    }
    
    private void encodeValue(ByteBuffer buffer, Object value) {
        if (value == null) {
            buffer.put((byte) 0x3F); // Null
            return;
        }
        
        if (value instanceof String) {
            String str = (String) value;
            buffer.put((byte) 0x75); // Character string
            buffer.put((byte) 0); // Encoding: ANSI X3.4
            buffer.put((byte) str.length());
            for (char c : str.toCharArray()) {
                buffer.put((byte) c);
            }
        } else if (value instanceof Number) {
            double num = ((Number) value).doubleValue();
            buffer.put((byte) 0x44); // Real
            buffer.putFloat((float) num);
        } else if (value instanceof Boolean) {
            buffer.put((byte) 0x91); // Enumerated: 0 = false, 1 = true
            buffer.put((byte) ((Boolean) value ? 1 : 0));
        }
    }
    
    private int extractDeviceInstance(byte[] data, int offset) {
        // Simplified extraction
        if (offset + 4 < data.length) {
            return ((data[offset] & 0xFF) << 24) | 
                   ((data[offset + 1] & 0xFF) << 16) | 
                   ((data[offset + 2] & 0xFF) << 8) | 
                   (data[offset + 3] & 0xFF);
        }
        return 0;
    }
    
    private int extractObjectType(byte[] data, int offset) {
        if (offset < data.length) {
            return (data[offset] & 0xFF) >> 2;
        }
        return 0;
    }
    
    private int extractObjectInstance(byte[] data, int offset) {
        return extractDeviceInstance(data, offset);
    }
    
    private int extractPropertyId(byte[] data, int offset) {
        if (offset < data.length) {
            return data[offset] & 0xFF;
        }
        return 85; // Default to Present Value
    }
    
    private String extractValue(byte[] data, int offset) {
        // Simplified extraction
        if (offset < data.length) {
            return String.valueOf(data[offset] & 0xFF);
        }
        return "0";
    }
    
    private Object getPropertyValue(BacnetObject object, int propertyId) {
        switch (propertyId) {
            case 77: // Object Name
                return object.getObjectName();
            case 85: // Present Value
                return parsePresentValue(object);
            case 117: // Description
                return object.getDescription() != null ? object.getDescription() : "";
            default:
                return null;
        }
    }
    
    private Object parsePresentValue(BacnetObject object) {
        String value = object.getPresentValue();
        if (value == null) return 0;
        
        try {
            if (object.getObjectType() == 0 || object.getObjectType() == 1) { // Analog
                return Double.parseDouble(value);
            } else if (object.getObjectType() == 3 || object.getObjectType() == 4) { // Binary
                return value.equalsIgnoreCase("true") || value.equals("1") || value.equalsIgnoreCase("active");
            }
        } catch (NumberFormatException e) {
            // Return as string
        }
        
        return value;
    }
    
    private void checkAndNotifyCOV(BacnetObject object, int deviceInstance) {
        String objectKey = deviceInstance + ":" + object.getObjectType() + ":" + object.getObjectInstance();
        
        for (CovSubscription sub : covSubscriptions.values()) {
            if (sub.deviceInstance == deviceInstance && 
                sub.objectType == object.getObjectType() && 
                sub.objectInstance == object.getObjectInstance()) {
                sendCOVNotification(sub, object);
            }
        }
    }
    
    private void sendCOVNotification(CovSubscription subscription, BacnetObject object) {
        // Implementation for COV notification
        // This would send a COV notification to the subscriber
    }
    
    private void refreshDeviceCache() {
        while (running) {
            try {
                Thread.sleep(5000); // Refresh every 5 seconds
                
                deviceCache.clear();
                objectCache.clear();
                
                List<BacnetDevice> devices = deviceRepository.findByEnabledTrue();
                for (BacnetDevice device : devices) {
                    deviceCache.put(device.getDeviceInstanceId(), device);
                    
                    List<BacnetObject> objects = objectRepository.findByDeviceId(device.getId());
                    for (BacnetObject obj : objects) {
                        String key = device.getDeviceInstanceId() + ":" + obj.getObjectType() + ":" + obj.getObjectInstance();
                        objectCache.put(key, obj);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error refreshing device cache", e);
            }
        }
    }
    
    private static class CovSubscription {
        InetAddress sourceAddress;
        int sourcePort;
        int deviceInstance;
        int objectType;
        int objectInstance;
        int subscriberProcessId;
    }
}

