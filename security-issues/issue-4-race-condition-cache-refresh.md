# [MEDIUM] Race Condition in Device/Object Cache Refresh

**Labels**: `security`, `severity-medium`, `owasp-a04-insecure-design`

## Vulnerability

The `BacnetService.java` class implements a periodic cache refresh mechanism that clears and rebuilds device and object caches. However, the cache clearing and rebuilding operations are not atomic, creating a race condition where BACnet protocol handlers may access empty caches during the refresh period. This can cause legitimate BACnet requests to fail with "unknown device" or "unknown object" errors, effectively creating a denial of service condition during cache refresh windows.

## OWASP Classification

**Category**: A04:2021 - Insecure Design  
**Reference**: https://owasp.org/Top10/A04_2021-Insecure_Design/

**Description**: This vulnerability represents an insecure design where concurrent operations are not properly synchronized. Race conditions are a common security flaw where the timing and ordering of operations can lead to incorrect system state. In this case, the lack of proper synchronization between cache refresh and cache access threads can cause service availability issues and incorrect behavior.

## Location

**File**: `src/main/java/com/bacnet/emulator/service/BacnetService.java`

**Lines 667-692** - Cache refresh method:
```java
private void refreshDeviceCache() {
    while (running) {
        try {
            Thread.sleep(5000); // Refresh every 5 seconds
            
            deviceCache.clear();  // RACE CONDITION: Cache is now empty
            objectCache.clear();  // RACE CONDITION: Cache is now empty
            // GAP: If BACnet request arrives here, it will fail
            
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
            // ...
        }
    }
}
```

**Also affected**:
- **Lines 259-263, 307-311, 318-327** - handleReadProperty and handleWriteProperty access caches without synchronization
- **Line 66-67** - ConcurrentHashMap is used but doesn't provide atomicity for clear+rebuild operations

## Risk Assessment

**Impact for BACnet Emulator (Local Development Tool)**:

1. **Intermittent Request Failures**: Every 5 seconds, there's a window where incoming BACnet requests will fail with "device not found" or "object not found" errors, even for valid devices/objects

2. **Testing Reliability**: Integration tests that send continuous BACnet requests will experience random failures during cache refresh windows, making test results unreliable

3. **Client Confusion**: BACnet clients performing periodic polling may see devices disappear and reappear, potentially triggering error handling or alarm conditions

4. **Inconsistent State**: A request might find a device but then fail to find its objects if the request executes between device cache rebuild and object cache rebuild

5. **Development Workflow Impact**: Developers testing BACnet client implementations will see unexplained failures, wasting debugging time

**Severity**: MEDIUM - Causes intermittent service failures but does not lead to data corruption or security compromise. However, it significantly impacts reliability and user experience.

## Suggested Fix

Implement atomic cache updates using a read-write lock or copy-on-write pattern:

### Option 1: Use ReadWriteLock (Recommended)

```java
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacnetService {
    
    // ... existing fields ...
    
    private final Map<Integer, BacnetDevice> deviceCache = new ConcurrentHashMap<>();
    private final Map<String, BacnetObject> objectCache = new ConcurrentHashMap<>();
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    // ... existing methods ...
    
    private void handleReadProperty(byte[] npdu, InetAddress sourceAddress, int sourcePort, int invokeId) {
        try {
            // ... extract fields ...
            
            cacheLock.readLock().lock();  // Acquire read lock
            try {
                BacnetDevice device = deviceCache.get(deviceInstance);
                if (device == null || !device.getEnabled()) {
                    sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x01);
                    return;
                }
                
                String objectKey = deviceInstance + ":" + objectType + ":" + objectInstance;
                BacnetObject object = objectCache.get(objectKey);
                
                if (object == null) {
                    // Cache miss - query database
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
                
                Object value = getPropertyValue(object, propertyId);
                sendReadPropertyResponse(sourceAddress, sourcePort, invokeId, deviceInstance, 
                                       objectType, objectInstance, propertyId, value);
            } finally {
                cacheLock.readLock().unlock();  // Always release lock
            }
            
        } catch (Exception e) {
            log.error("Error handling ReadProperty", e);
            sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x01);
        }
    }
    
    private void handleWriteProperty(byte[] npdu, InetAddress sourceAddress, int sourcePort, int invokeId) {
        try {
            // ... extract fields ...
            
            cacheLock.readLock().lock();  // Acquire read lock
            try {
                BacnetDevice device = deviceCache.get(deviceInstance);
                if (device == null || !device.getEnabled()) {
                    sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x01);
                    return;
                }
                
                String objectKey = deviceInstance + ":" + objectType + ":" + objectInstance;
                BacnetObject object = objectCache.get(objectKey);
                
                if (object == null) {
                    object = objectRepository.findByDeviceIdAndObjectTypeAndObjectInstance(
                            device.getId(), objectType, objectInstance).orElse(null);
                }
                
                if (object == null) {
                    sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x85);
                    return;
                }
                
                if (!object.getWritable() || propertyId != 85) {
                    sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x03);
                    return;
                }
                
                String newValue = extractValue(npdu, 16);
                object.setPresentValue(newValue);
                objectRepository.save(object);
                objectCache.put(objectKey, object);
                
                checkAndNotifyCOV(object, deviceInstance);
                sendSimpleAck(sourceAddress, sourcePort, invokeId);
            } finally {
                cacheLock.readLock().unlock();  // Always release lock
            }
            
        } catch (Exception e) {
            log.error("Error handling WriteProperty", e);
            sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x01);
        }
    }
    
    private void refreshDeviceCache() {
        while (running) {
            try {
                Thread.sleep(5000);
                
                // Build new caches
                Map<Integer, BacnetDevice> newDeviceCache = new ConcurrentHashMap<>();
                Map<String, BacnetObject> newObjectCache = new ConcurrentHashMap<>();
                
                List<BacnetDevice> devices = deviceRepository.findByEnabledTrue();
                for (BacnetDevice device : devices) {
                    newDeviceCache.put(device.getDeviceInstanceId(), device);
                    
                    List<BacnetObject> objects = objectRepository.findByDeviceId(device.getId());
                    for (BacnetObject obj : objects) {
                        String key = device.getDeviceInstanceId() + ":" + 
                                   obj.getObjectType() + ":" + obj.getObjectInstance();
                        newObjectCache.put(key, obj);
                    }
                }
                
                // Atomic swap under write lock
                cacheLock.writeLock().lock();
                try {
                    deviceCache.clear();
                    deviceCache.putAll(newDeviceCache);
                    objectCache.clear();
                    objectCache.putAll(newObjectCache);
                    log.debug("Cache refreshed: {} devices, {} objects", 
                             deviceCache.size(), objectCache.size());
                } finally {
                    cacheLock.writeLock().unlock();
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error refreshing device cache", e);
            }
        }
    }
}
```

### Option 2: Use AtomicReference (Alternative)

```java
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacnetService {
    
    private final AtomicReference<Map<Integer, BacnetDevice>> deviceCache = 
        new AtomicReference<>(new ConcurrentHashMap<>());
    private final AtomicReference<Map<String, BacnetObject>> objectCache = 
        new AtomicReference<>(new ConcurrentHashMap<>());
    
    // In handleReadProperty and handleWriteProperty, use:
    BacnetDevice device = deviceCache.get().get(deviceInstance);
    BacnetObject object = objectCache.get().get(objectKey);
    
    // In refreshDeviceCache:
    private void refreshDeviceCache() {
        while (running) {
            try {
                Thread.sleep(5000);
                
                Map<Integer, BacnetDevice> newDeviceCache = new ConcurrentHashMap<>();
                Map<String, BacnetObject> newObjectCache = new ConcurrentHashMap<>();
                
                // ... populate new caches ...
                
                // Atomic swap
                deviceCache.set(newDeviceCache);
                objectCache.set(newObjectCache);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
```

## Acceptance Criteria

- [ ] Cache refresh operations do not clear caches before new data is ready
- [ ] Cache reads are protected with read locks
- [ ] Cache updates use write locks for atomic swap
- [ ] No "device not found" or "object not found" errors occur during cache refresh
- [ ] Performance impact of locking is acceptable (< 1ms overhead per request)
- [ ] Unit tests verify:
  - Concurrent reads during cache refresh succeed
  - Cache values remain consistent during refresh
  - No deadlocks occur under load
- [ ] Load test with continuous BACnet requests confirms no intermittent failures
- [ ] Cache refresh logging added to track refresh duration and size
- [ ] Documentation updated to explain cache synchronization strategy
