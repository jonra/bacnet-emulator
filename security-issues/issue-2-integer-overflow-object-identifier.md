# [HIGH] Integer Overflow in BACnet Object Identifier Encoding

**Labels**: `security`, `severity-high`, `owasp-a04-insecure-design`

## Vulnerability

The `encodeObjectIdentifier` method in `BacnetService.java` does not validate that the instance ID fits within the 22-bit field defined by the BACnet protocol. When large instance IDs are provided (> 4,194,303), the encoding silently overflows, causing incorrect object identifiers to be transmitted. This can lead to protocol violations, incorrect device behavior, and potential security issues if the overflow is exploited.

## OWASP Classification

**Category**: A04:2021 - Insecure Design  
**Reference**: https://owasp.org/Top10/A04_2021-Insecure_Design/

**Description**: This OWASP category focuses on risks related to design and architectural flaws. The lack of input validation on protocol-defined field sizes represents an insecure design where business logic does not enforce protocol constraints. This can lead to data corruption, protocol violations, and potentially exploitable conditions.

## Location

**File**: `src/main/java/com/bacnet/emulator/service/BacnetService.java`

**Lines 540-544**:
```java
private void encodeObjectIdentifier(ByteBuffer buffer, int objectType, int instance) {
    buffer.put((byte) 0x0C); // Context tag 0
    long value = ((long) objectType << 22) | instance;  // No validation of instance size
    encodeUnsignedInt(buffer, (int) value);
}
```

**Issue**: The BACnet protocol specifies that object identifiers consist of:
- Object Type: 10 bits (values 0-1023)
- Instance Number: 22 bits (values 0-4,194,303)

However, the code accepts any integer value for `instance` without validation. If `instance > 4,194,303`, the upper bits overflow into the object type field, corrupting the encoded identifier.

**Also affected**: Lines 418, 462 where `encodeObjectIdentifier` is called with user-controlled device/object instance IDs.

## Risk Assessment

**Impact for BACnet Emulator (Local Development Tool)**:

1. **Protocol Violations**: BACnet clients receiving malformed object identifiers will fail to properly identify objects, causing communication errors
2. **Incorrect Device Behavior**: Overflow could cause one device/object to masquerade as another, leading to incorrect reads/writes
3. **Testing Integrity**: Integration tests using large instance IDs will produce incorrect results, undermining test validity
4. **Client Confusion**: BACnet clients may crash or behave unpredictably when receiving malformed identifiers
5. **Exploitability**: An attacker with API access could create devices with crafted instance IDs that encode as different devices, potentially bypassing access controls in client applications

**Severity**: HIGH - Can cause protocol violations and incorrect system behavior, with potential for exploitation.

## Suggested Fix

Add validation to ensure instance IDs fit within protocol-defined bit limits:

### 1. Add Validation Constants
```java
// Add at class level
private static final int MAX_OBJECT_TYPE = 1023;        // 10 bits: 2^10 - 1
private static final int MAX_INSTANCE_NUMBER = 4194303;  // 22 bits: 2^22 - 1
```

### 2. Validate in encodeObjectIdentifier
```java
private void encodeObjectIdentifier(ByteBuffer buffer, int objectType, int instance) {
    // Validate object type fits in 10 bits
    if (objectType < 0 || objectType > MAX_OBJECT_TYPE) {
        log.error("Invalid object type: {}. Must be 0-1023", objectType);
        throw new IllegalArgumentException("Object type must be 0-1023, got: " + objectType);
    }
    
    // Validate instance fits in 22 bits
    if (instance < 0 || instance > MAX_INSTANCE_NUMBER) {
        log.error("Invalid instance number: {}. Must be 0-4194303", instance);
        throw new IllegalArgumentException("Instance number must be 0-4194303, got: " + instance);
    }
    
    buffer.put((byte) 0x0C); // Context tag 0
    long value = ((long) objectType << 22) | instance;
    encodeUnsignedInt(buffer, (int) value);
}
```

### 3. Add Validation to DTOs
```java
// In DeviceDto.java
@NotNull(message = "Device Instance ID is required")
@Min(value = 1, message = "Device Instance ID must be positive")
@Max(value = 4194303, message = "Device Instance ID must not exceed 4194303 (22-bit limit)")
private Integer deviceInstanceId;
```

```java
// In ObjectDto.java
@NotNull(message = "Object Type is required")
@Min(value = 0, message = "Object Type must be non-negative")
@Max(value = 1023, message = "Object Type must not exceed 1023 (10-bit limit)")
private Integer objectType;

@NotNull(message = "Object Instance is required")
@Min(value = 1, message = "Object Instance must be positive")
@Max(value = 4194303, message = "Object Instance must not exceed 4194303 (22-bit limit)")
private Integer objectInstance;
```

### 4. Add Validation in Services
```java
// In DeviceService.java - createDevice and updateDevice methods
public DeviceDto createDevice(DeviceDto dto) {
    if (dto.getDeviceInstanceId() > MAX_INSTANCE_NUMBER) {
        throw new RuntimeException("Device Instance ID exceeds maximum allowed value of 4194303");
    }
    // ... rest of method
}
```

```java
// In ObjectService.java - createObject and updateObject methods  
public ObjectDto createObject(ObjectDto dto) {
    if (dto.getObjectType() > MAX_OBJECT_TYPE) {
        throw new RuntimeException("Object Type exceeds maximum allowed value of 1023");
    }
    if (dto.getObjectInstance() > MAX_INSTANCE_NUMBER) {
        throw new RuntimeException("Object Instance exceeds maximum allowed value of 4194303");
    }
    // ... rest of method
}
```

### 5. Add Validation to Extraction Methods
```java
private int extractObjectType(byte[] data, int offset) {
    if (offset >= 0 && offset < data.length) {
        int objectType = (data[offset] & 0xFF) >> 2;
        // Validate extracted object type is within valid range
        if (objectType > MAX_OBJECT_TYPE) {
            log.warn("Extracted object type {} exceeds maximum {}", objectType, MAX_OBJECT_TYPE);
            return 0;
        }
        return objectType;
    }
    log.warn("extractObjectType: Invalid offset {} for data length {}", offset, data.length);
    return 0;
}
```

## Acceptance Criteria

- [ ] Constants defined for maximum object type (1023) and instance number (4194303)
- [ ] `encodeObjectIdentifier` validates both objectType and instance parameters
- [ ] IllegalArgumentException thrown for out-of-range values with descriptive messages
- [ ] DTO validation annotations updated with @Max constraints
- [ ] Service layer validation added to createDevice/updateDevice methods
- [ ] Service layer validation added to createObject/updateObject methods
- [ ] Unit tests verify rejection of:
  - Device instance IDs > 4,194,303
  - Object types > 1023
  - Object instances > 4,194,303
  - Negative values for any identifier
- [ ] Integration tests confirm API returns 400 Bad Request for invalid identifiers
- [ ] Existing valid identifiers (within range) continue to work correctly
- [ ] Error messages clearly indicate the valid range for each field
