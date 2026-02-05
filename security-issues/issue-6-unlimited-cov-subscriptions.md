# [LOW] Unlimited COV Subscriptions Leading to Memory Exhaustion

**Labels**: `security`, `severity-low`, `owasp-a04-insecure-design`

## Vulnerability

The `BacnetService.java` class stores Change of Value (COV) subscriptions in an unbounded `ConcurrentHashMap` without any limits on the number of subscriptions or subscription lifetime. An attacker or misconfigured client can create unlimited subscriptions by sending repeated `SubscribeCOV` requests, eventually exhausting available memory and causing the application to crash with an `OutOfMemoryError`.

## OWASP Classification

**Category**: A04:2021 - Insecure Design  
**Reference**: https://owasp.org/Top10/A04_2021-Insecure_Design/

**Description**: This OWASP category covers design and architectural flaws. The lack of resource limits on subscription storage represents an insecure design where the system does not protect itself from resource exhaustion attacks. Similar to other resource exhaustion vulnerabilities, this can lead to denial of service even though it doesn't involve traditional injection or authentication bypass.

## Location

**File**: `src/main/java/com/bacnet/emulator/service/BacnetService.java`

**Line 68** - Unbounded subscription map:
```java
private final Map<String, CovSubscription> covSubscriptions = new ConcurrentHashMap<>();
```

**Lines 366-396** - handleSubscribeCOV method:
```java
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
        subscription.subscriberProcessId = 0;
        
        // VULNERABILITY: No limit on subscription count
        // No expiration time
        // No validation that object exists
        covSubscriptions.put(subscriptionKey, subscription);
        
        monitorService.log("INFO", "COV subscription received", 
                sourceAddress.getHostAddress(), "SubscribeCOV", deviceInstance, objectType, objectInstance, null);
        
        sendSimpleAck(sourceAddress, sourcePort, invokeId);
        
    } catch (Exception e) {
        log.error("Error handling SubscribeCOV", e);
        sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x01);
    }
}
```

**Issues**:
1. No maximum subscription limit
2. No subscription expiration or timeout
3. No mechanism to unsubscribe (except service restart)
4. No validation that the subscribed object exists
5. Subscriptions never removed, even if client disconnects
6. Memory grows unbounded with each new subscription

## Risk Assessment

**Impact for BACnet Emulator (Local Development Tool)**:

1. **Memory Exhaustion**:
   - Each subscription consumes ~100-200 bytes of memory
   - Attacker can create millions of subscriptions: `10,000,000 * 150 bytes = ~1.5 GB`
   - Eventually triggers OutOfMemoryError, crashing the application

2. **Legitimate Use Cases Affected**:
   - Long-running testing sessions accumulate subscriptions
   - Repeated test runs without restarting emulator
   - Multiple test clients subscribing to many objects

3. **Performance Degradation**:
   - Large subscription map slows down notification checks (line 650-660)
   - Iteration over millions of entries on each value change

4. **Resource Leaks**:
   - Subscriptions for deleted objects remain in memory
   - Subscriptions from disconnected clients never cleaned up

5. **Testing Impact**:
   - Memory growth forces frequent emulator restarts
   - Unpredictable crashes during long-running tests
   - Difficulty reproducing issues due to memory state

**Severity**: LOW - Requires sustained attack or long-running misconfiguration. Not easily exploitable remotely, but can occur in legitimate testing scenarios.

## Suggested Fix

Implement subscription limits, expiration, and cleanup mechanisms:

### 1. Add Subscription Limits and Expiration

```java
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacnetService {
    
    // Configuration constants
    private static final int MAX_SUBSCRIPTIONS = 1000;
    private static final long SUBSCRIPTION_TIMEOUT_SECONDS = 3600; // 1 hour
    
    private final Map<String, CovSubscription> covSubscriptions = new ConcurrentHashMap<>();
    
    // ... existing code ...
    
    private void handleSubscribeCOV(byte[] npdu, InetAddress sourceAddress, int sourcePort, int invokeId) {
        try {
            if (npdu.length < 10) return;
            
            int deviceInstance = extractDeviceInstance(npdu, 5);
            int objectType = extractObjectType(npdu, 8);
            int objectInstance = extractObjectInstance(npdu, 9);
            
            // Validate device and object exist
            BacnetDevice device = deviceCache.get(deviceInstance);
            if (device == null || !device.getEnabled()) {
                log.warn("SubscribeCOV for unknown device: {}", deviceInstance);
                sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x01);
                return;
            }
            
            String objectKey = deviceInstance + ":" + objectType + ":" + objectInstance;
            BacnetObject object = objectCache.get(objectKey);
            if (object == null) {
                log.warn("SubscribeCOV for unknown object: {}", objectKey);
                sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x85);
                return;
            }
            
            // Check subscription limit
            if (covSubscriptions.size() >= MAX_SUBSCRIPTIONS) {
                log.warn("Subscription limit reached ({}). Rejecting new subscription from {}",
                         MAX_SUBSCRIPTIONS, sourceAddress.getHostAddress());
                sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x04); // Resource limit exceeded
                return;
            }
            
            String subscriptionKey = sourceAddress.getHostAddress() + ":" + sourcePort + ":" + 
                    deviceInstance + ":" + objectType + ":" + objectInstance;
            
            CovSubscription subscription = new CovSubscription();
            subscription.sourceAddress = sourceAddress;
            subscription.sourcePort = sourcePort;
            subscription.deviceInstance = deviceInstance;
            subscription.objectType = objectType;
            subscription.objectInstance = objectInstance;
            subscription.subscriberProcessId = 0;
            subscription.expirationTime = Instant.now().plusSeconds(SUBSCRIPTION_TIMEOUT_SECONDS);
            
            covSubscriptions.put(subscriptionKey, subscription);
            
            log.info("COV subscription added: {} (total: {})", subscriptionKey, covSubscriptions.size());
            monitorService.log("INFO", "COV subscription received", 
                    sourceAddress.getHostAddress(), "SubscribeCOV", deviceInstance, objectType, objectInstance, null);
            
            sendSimpleAck(sourceAddress, sourcePort, invokeId);
            
        } catch (Exception e) {
            log.error("Error handling SubscribeCOV", e);
            sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x01);
        }
    }
    
    @PostConstruct
    public void initialize() {
        try {
            // ... existing initialization ...
            
            // Start subscription cleanup thread
            executorService.submit(this::cleanupExpiredSubscriptions);
            
            log.info("BACnet service initialized and started");
        } catch (Exception e) {
            log.error("Failed to initialize BACnet service", e);
        }
    }
    
    private void cleanupExpiredSubscriptions() {
        while (running) {
            try {
                Thread.sleep(60000);  // Check every minute
                
                Instant now = Instant.now();
                int removedCount = 0;
                
                Iterator<Map.Entry<String, CovSubscription>> iterator = 
                    covSubscriptions.entrySet().iterator();
                
                while (iterator.hasNext()) {
                    Map.Entry<String, CovSubscription> entry = iterator.next();
                    CovSubscription subscription = entry.getValue();
                    
                    if (subscription.expirationTime != null && 
                        subscription.expirationTime.isBefore(now)) {
                        iterator.remove();
                        removedCount++;
                    }
                }
                
                if (removedCount > 0) {
                    log.info("Cleaned up {} expired COV subscriptions. Remaining: {}", 
                             removedCount, covSubscriptions.size());
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error cleaning up COV subscriptions", e);
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
        Instant expirationTime;  // NEW: Track expiration
    }
}
```

### 2. Add Configuration Properties

Add to `application.yml`:
```yaml
bacnet:
  cov:
    max-subscriptions: 1000
    subscription-timeout-seconds: 3600  # 1 hour
```

### 3. Add Subscription Management Endpoints

Add to `ApiController.java`:
```java
@Operation(summary = "Get COV subscription statistics")
@GetMapping("/stats/cov-subscriptions")
public ResponseEntity<Map<String, Object>> getCovStats() {
    // This would require exposing subscription stats from BacnetService
    Map<String, Object> stats = Map.of(
        "totalSubscriptions", /* get from BacnetService */,
        "maxSubscriptions", /* get from config */
    );
    return ResponseEntity.ok(stats);
}
```

### 4. Add Monitoring and Alerts

```java
private void checkAndNotifyCOV(BacnetObject object, int deviceInstance) {
    String objectKey = deviceInstance + ":" + object.getObjectType() + ":" + object.getObjectInstance();
    
    int notificationsSent = 0;
    for (CovSubscription sub : covSubscriptions.values()) {
        if (sub.deviceInstance == deviceInstance && 
            sub.objectType == object.getObjectType() && 
            sub.objectInstance == object.getObjectInstance()) {
            sendCOVNotification(sub, object);
            notificationsSent++;
        }
    }
    
    // Warn if too many subscriptions for one object
    if (notificationsSent > 100) {
        log.warn("Sending {} COV notifications for object {}. Consider subscription limits.",
                 notificationsSent, objectKey);
    }
}
```

## Acceptance Criteria

- [ ] Maximum subscription limit enforced (default 1000, configurable)
- [ ] Subscription requests beyond limit return error (resource limit exceeded)
- [ ] Subscriptions expire after timeout (default 1 hour, configurable)
- [ ] Background thread removes expired subscriptions every minute
- [ ] Subscriptions validate that device and object exist before accepting
- [ ] Subscription statistics available via API endpoint
- [ ] Configuration properties in application.yml
- [ ] Unit tests verify:
  - Subscription limit enforcement
  - Expiration and cleanup
  - Validation of device/object existence
  - Memory doesn't grow unbounded
- [ ] Load test confirms:
  - Memory usage stays bounded under subscription flood
  - Expired subscriptions are cleaned up
  - System remains responsive with max subscriptions
- [ ] Logging indicates subscription lifecycle events
- [ ] Documentation explains subscription limits and timeouts
