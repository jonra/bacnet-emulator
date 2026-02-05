# [CRITICAL] Buffer Overflow in BACnet Protocol Parsing

**Labels**: `security`, `severity-critical`, `owasp-a03-injection`

## Vulnerability

The BACnet protocol parser in `BacnetService.java` lacks proper bounds checking when processing incoming UDP packets. Multiple buffer operations trust attacker-controlled length fields without validation, allowing malicious packets to cause buffer overflows, application crashes, or potentially remote code execution.

## OWASP Classification

**Category**: A03:2021 - Injection  
**Reference**: https://owasp.org/Top10/A03_2021-Injection/

**Description**: This OWASP category covers injection flaws that occur when untrusted data is sent to an interpreter as part of a command or query. In this case, the vulnerability involves buffer overflow where untrusted packet length values are used directly in buffer operations without validation, allowing attackers to inject malicious data beyond intended buffer boundaries.

## Location

**File**: `src/main/java/com/bacnet/emulator/service/BacnetService.java`

**Vulnerable code sections**:

1. **Line 136** - Packet data copy trusts packet length:
```java
byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
```

2. **Lines 161-164** - NPDU length extracted without bounds validation:
```java
int npduLength = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
if (data.length < 4 + npduLength) return;
byte[] npdu = Arrays.copyOfRange(data, 4, 4 + npduLength);
```
Issue: `npduLength` is read from the packet but check `data.length < 4 + npduLength` happens after extraction, and integer overflow in `4 + npduLength` is not checked.

3. **Lines 583-617** - Extract methods perform array access with insufficient bounds checking:
```java
private int extractDeviceInstance(byte[] data, int offset) {
    if (offset + 4 < data.length) {
        return ((data[offset] & 0xFF) << 24) | 
               ((data[offset + 1] & 0xFF) << 16) | 
               ((data[offset + 2] & 0xFF) << 8) | 
               (data[offset + 3] & 0xFF);
    }
    return 0;
}
```
Issue: Checks `offset + 4 < data.length` but should be `offset + 4 <= data.length` to prevent reading beyond array bounds.

## Risk Assessment

**Impact for BACnet Emulator (Local Development Tool)**:

While this is a local development tool, the risks are still significant:

1. **Denial of Service**: Malicious or malformed BACnet packets from misconfigured clients or testing tools can crash the emulator, disrupting development/testing workflows
2. **Development Environment Compromise**: If exploited for code execution, attacker could compromise the developer's workstation
3. **Integration Testing Issues**: Fuzzing tools or security scanners testing BACnet implementations could unintentionally crash the emulator
4. **Data Integrity**: Crashes could corrupt the H2 database, losing device/object configurations

**Severity**: CRITICAL - Buffer overflows in network protocol parsers are severe vulnerabilities that can lead to crashes or remote code execution.

## Suggested Fix

Add comprehensive bounds checking to all buffer operations:

### 1. Validate NPDU Length
```java
private void parseAndHandleBacnetMessage(byte[] data, InetAddress sourceAddress, int sourcePort) {
    try {
        // Skip BACnet/IP header (4 bytes)
        if (data.length < 4) return;
        
        int npduLength = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        
        // CRITICAL: Validate npduLength to prevent integer overflow and buffer overflow
        if (npduLength < 0 || npduLength > 1476 || data.length < 4 + npduLength) {
            log.warn("Invalid NPDU length: {} (data length: {})", npduLength, data.length);
            return;
        }
        
        byte[] npdu = Arrays.copyOfRange(data, 4, 4 + npduLength);
        // ... rest of processing
    } catch (Exception e) {
        log.error("Error parsing BACnet message", e);
    }
}
```

### 2. Fix Extract Methods Bounds Checking
```java
private int extractDeviceInstance(byte[] data, int offset) {
    // FIXED: Use <= instead of < to prevent off-by-one error
    if (offset >= 0 && offset + 4 <= data.length) {
        return ((data[offset] & 0xFF) << 24) | 
               ((data[offset + 1] & 0xFF) << 16) | 
               ((data[offset + 2] & 0xFF) << 8) | 
               (data[offset + 3] & 0xFF);
    }
    log.warn("extractDeviceInstance: Invalid offset {} for data length {}", offset, data.length);
    return 0;
}

private int extractObjectType(byte[] data, int offset) {
    if (offset >= 0 && offset < data.length) {
        return (data[offset] & 0xFF) >> 2;
    }
    log.warn("extractObjectType: Invalid offset {} for data length {}", offset, data.length);
    return 0;
}

private int extractPropertyId(byte[] data, int offset) {
    if (offset >= 0 && offset < data.length) {
        return data[offset] & 0xFF;
    }
    log.warn("extractPropertyId: Invalid offset {} for data length {}", offset, data.length);
    return 85; // Default to Present Value
}
```

### 3. Add Input Validation to handlePacket
```java
private void handlePacket(DatagramPacket packet) {
    try {
        // Validate packet length
        if (packet.getLength() < 4 || packet.getLength() > 1476) {
            log.warn("Invalid packet length: {}", packet.getLength());
            return;
        }
        
        byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
        // ... rest of processing
    } catch (Exception e) {
        log.error("Error handling BACnet packet", e);
    }
}
```

### 4. Add Validation to String Encoding
```java
private void encodeValue(ByteBuffer buffer, Object value) {
    if (value == null) {
        buffer.put((byte) 0x3F); // Null
        return;
    }
    
    if (value instanceof String) {
        String str = (String) value;
        // FIXED: Validate string length fits in protocol limit
        if (str.length() > 255) {
            log.warn("String value truncated from {} to 255 characters", str.length());
            str = str.substring(0, 255);
        }
        buffer.put((byte) 0x75); // Character string
        buffer.put((byte) 0); // Encoding: ANSI X3.4
        buffer.put((byte) str.length());
        for (char c : str.toCharArray()) {
            buffer.put((byte) c);
        }
    }
    // ... rest of method
}
```

## Acceptance Criteria

- [ ] All buffer operations include bounds checking before array access
- [ ] NPDU length is validated against maximum packet size (1476 bytes) and actual data length
- [ ] Integer overflow checks added for length calculations
- [ ] Extract methods validate both offset and offset+required_bytes against array length
- [ ] String encoding validates length before casting to byte
- [ ] Unit tests added to verify rejection of malformed packets:
  - Packets with length > 1476 bytes
  - NPDU length exceeding packet size
  - NPDU length causing integer overflow
  - Invalid offsets in extract methods
  - Strings exceeding 255 characters
- [ ] Fuzzing tests confirm no crashes with random/malformed input
- [ ] All tests pass without ArrayIndexOutOfBoundsException or BufferOverflowException
