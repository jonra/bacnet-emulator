#!/bin/bash
# Script to create security vulnerability labels and issues in GitHub
# Uses GitHub CLI (gh) to create issues directly

set -e

REPO="jonra/bacnet-emulator"

echo "=========================================="
echo "Creating Security Labels and Issues"
echo "Repository: $REPO"
echo "=========================================="
echo ""

# Check if gh is installed and authenticated
if ! command -v gh &> /dev/null; then
    echo "ERROR: GitHub CLI (gh) is not installed"
    echo "Install from: https://cli.github.com/"
    exit 1
fi

echo "Checking authentication..."
if ! gh auth status &> /dev/null; then
    echo "ERROR: Not authenticated with GitHub CLI"
    echo "Run: gh auth login"
    exit 1
fi

echo "✓ GitHub CLI authenticated"
echo ""

# Create labels
echo "Creating labels..."
gh label create "security" --color "d73a4a" --force --repo "$REPO" 2>/dev/null || echo "  Label 'security' exists or created"
gh label create "severity-critical" --color "b60205" --force --repo "$REPO" 2>/dev/null || echo "  Label 'severity-critical' exists or created"
gh label create "severity-high" --color "d93f0b" --force --repo "$REPO" 2>/dev/null || echo "  Label 'severity-high' exists or created"
gh label create "severity-medium" --color "fbca04" --force --repo "$REPO" 2>/dev/null || echo "  Label 'severity-medium' exists or created"
gh label create "severity-low" --color "0e8a16" --force --repo "$REPO" 2>/dev/null || echo "  Label 'severity-low' exists or created"
gh label create "owasp-a03-injection" --color "1d76db" --force --repo "$REPO" 2>/dev/null || echo "  Label 'owasp-a03-injection' exists or created"
gh label create "owasp-a04-insecure-design" --color "1d76db" --force --repo "$REPO" 2>/dev/null || echo "  Label 'owasp-a04-insecure-design' exists or created"

echo "✓ Labels created"
echo ""

# Issue 1: Buffer Overflow in BACnet Protocol Parsing
echo "Creating Issue 1: Buffer Overflow in BACnet Protocol Parsing [CRITICAL]"
gh issue create \
  --repo "$REPO" \
  --title "[CRITICAL] Buffer Overflow in BACnet Protocol Parsing" \
  --label "security,severity-critical,owasp-a03-injection" \
  --body "## Vulnerability

The BACnet protocol parser in \`BacnetService.java\` lacks proper bounds checking when processing incoming UDP packets. Multiple buffer operations trust attacker-controlled length fields without validation, allowing malicious packets to cause buffer overflows, application crashes, or potentially remote code execution.

## OWASP Classification

**Category**: A03:2021 - Injection  
**Reference**: https://owasp.org/Top10/A03_2021-Injection/

**Description**: This OWASP category covers injection flaws that occur when untrusted data is sent to an interpreter as part of a command or query. In this case, the vulnerability involves buffer overflow where untrusted packet length values are used directly in buffer operations without validation, allowing attackers to inject malicious data beyond intended buffer boundaries.

## Location

**File**: \`src/main/java/com/bacnet/emulator/service/BacnetService.java\`

**Vulnerable code sections**:

1. **Line 136** - Packet data copy trusts packet length:
\`\`\`java
byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
\`\`\`

2. **Lines 161-164** - NPDU length extracted without bounds validation:
\`\`\`java
int npduLength = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
if (data.length < 4 + npduLength) return;
byte[] npdu = Arrays.copyOfRange(data, 4, 4 + npduLength);
\`\`\`
Issue: \`npduLength\` is read from the packet but check happens after extraction, and integer overflow in \`4 + npduLength\` is not checked.

3. **Lines 583-617** - Extract methods perform array access with insufficient bounds checking:
\`\`\`java
private int extractDeviceInstance(byte[] data, int offset) {
    if (offset + 4 < data.length) {
        return ((data[offset] & 0xFF) << 24) | 
               ((data[offset + 1] & 0xFF) << 16) | 
               ((data[offset + 2] & 0xFF) << 8) | 
               (data[offset + 3] & 0xFF);
    }
    return 0;
}
\`\`\`
Issue: Checks \`offset + 4 < data.length\` but should be \`offset + 4 <= data.length\` to prevent reading beyond array bounds.

## Risk Assessment

**Impact for BACnet Emulator (Local Development Tool)**:

1. **Denial of Service**: Malicious or malformed BACnet packets from misconfigured clients or testing tools can crash the emulator
2. **Development Environment Compromise**: If exploited for code execution, attacker could compromise the developer's workstation
3. **Integration Testing Issues**: Fuzzing tools or security scanners testing BACnet implementations could unintentionally crash the emulator
4. **Data Integrity**: Crashes could corrupt the H2 database, losing device/object configurations

**Severity**: CRITICAL - Buffer overflows in network protocol parsers are severe vulnerabilities that can lead to crashes or remote code execution.

## Suggested Fix

Add comprehensive bounds checking to all buffer operations:

### 1. Validate NPDU Length
\`\`\`java
private void parseAndHandleBacnetMessage(byte[] data, InetAddress sourceAddress, int sourcePort) {
    try {
        if (data.length < 4) return;
        
        int npduLength = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        
        // CRITICAL: Validate npduLength to prevent overflow
        if (npduLength < 0 || npduLength > 1476 || data.length < 4 + npduLength) {
            log.warn(\"Invalid NPDU length: {} (data length: {})\", npduLength, data.length);
            return;
        }
        
        byte[] npdu = Arrays.copyOfRange(data, 4, 4 + npduLength);
        // ... rest of processing
    } catch (Exception e) {
        log.error(\"Error parsing BACnet message\", e);
    }
}
\`\`\`

### 2. Fix Extract Methods Bounds Checking
\`\`\`java
private int extractDeviceInstance(byte[] data, int offset) {
    // FIXED: Use <= instead of <
    if (offset >= 0 && offset + 4 <= data.length) {
        return ((data[offset] & 0xFF) << 24) | 
               ((data[offset + 1] & 0xFF) << 16) | 
               ((data[offset + 2] & 0xFF) << 8) | 
               (data[offset + 3] & 0xFF);
    }
    log.warn(\"extractDeviceInstance: Invalid offset {} for data length {}\", offset, data.length);
    return 0;
}
\`\`\`

## Acceptance Criteria

- [ ] All buffer operations include bounds checking before array access
- [ ] NPDU length is validated against maximum packet size (1476 bytes)
- [ ] Integer overflow checks added for length calculations
- [ ] Extract methods validate both offset and offset+required_bytes
- [ ] Unit tests verify rejection of malformed packets
- [ ] Fuzzing tests confirm no crashes with random/malformed input"

echo "✓ Issue 1 created"
echo ""

# Issue 2: Integer Overflow in BACnet Object Identifier Encoding
echo "Creating Issue 2: Integer Overflow in BACnet Object Identifier Encoding [HIGH]"
gh issue create \
  --repo "$REPO" \
  --title "[HIGH] Integer Overflow in BACnet Object Identifier Encoding" \
  --label "security,severity-high,owasp-a04-insecure-design" \
  --body "## Vulnerability

The \`encodeObjectIdentifier\` method in \`BacnetService.java\` does not validate that the instance ID fits within the 22-bit field defined by the BACnet protocol. When large instance IDs are provided (> 4,194,303), the encoding silently overflows, causing incorrect object identifiers to be transmitted.

## OWASP Classification

**Category**: A04:2021 - Insecure Design  
**Reference**: https://owasp.org/Top10/A04_2021-Insecure_Design/

**Description**: This OWASP category focuses on risks related to design and architectural flaws. The lack of input validation on protocol-defined field sizes represents an insecure design where business logic does not enforce protocol constraints.

## Location

**File**: \`src/main/java/com/bacnet/emulator/service/BacnetService.java\`

**Lines 540-544**:
\`\`\`java
private void encodeObjectIdentifier(ByteBuffer buffer, int objectType, int instance) {
    buffer.put((byte) 0x0C);
    long value = ((long) objectType << 22) | instance;  // No validation
    encodeUnsignedInt(buffer, (int) value);
}
\`\`\`

**Issue**: The BACnet protocol specifies:
- Object Type: 10 bits (values 0-1023)
- Instance Number: 22 bits (values 0-4,194,303)

However, the code accepts any integer value for \`instance\` without validation.

## Risk Assessment

**Impact for BACnet Emulator (Local Development Tool)**:

1. **Protocol Violations**: BACnet clients receiving malformed object identifiers will fail to properly identify objects
2. **Incorrect Device Behavior**: Overflow could cause one device/object to masquerade as another
3. **Testing Integrity**: Integration tests using large instance IDs will produce incorrect results
4. **Exploitability**: An attacker with API access could create devices with crafted instance IDs that encode as different devices

**Severity**: HIGH - Can cause protocol violations and incorrect system behavior.

## Suggested Fix

Add validation to ensure instance IDs fit within protocol-defined bit limits:

\`\`\`java
// Add at class level
private static final int MAX_OBJECT_TYPE = 1023;        // 10 bits
private static final int MAX_INSTANCE_NUMBER = 4194303;  // 22 bits

private void encodeObjectIdentifier(ByteBuffer buffer, int objectType, int instance) {
    if (objectType < 0 || objectType > MAX_OBJECT_TYPE) {
        throw new IllegalArgumentException(\"Object type must be 0-1023, got: \" + objectType);
    }
    if (instance < 0 || instance > MAX_INSTANCE_NUMBER) {
        throw new IllegalArgumentException(\"Instance must be 0-4194303, got: \" + instance);
    }
    
    buffer.put((byte) 0x0C);
    long value = ((long) objectType << 22) | instance;
    encodeUnsignedInt(buffer, (int) value);
}
\`\`\`

Also add validation in DTOs:
\`\`\`java
// DeviceDto.java
@Max(value = 4194303, message = \"Device Instance ID must not exceed 4194303\")
private Integer deviceInstanceId;

// ObjectDto.java
@Max(value = 1023, message = \"Object Type must not exceed 1023\")
private Integer objectType;

@Max(value = 4194303, message = \"Object Instance must not exceed 4194303\")
private Integer objectInstance;
\`\`\`

## Acceptance Criteria

- [ ] Constants defined for MAX_OBJECT_TYPE (1023) and MAX_INSTANCE_NUMBER (4,194,303)
- [ ] \`encodeObjectIdentifier\` validates both parameters
- [ ] DTO validation annotations updated with @Max constraints
- [ ] Service layer validation added
- [ ] Unit tests verify rejection of out-of-range values
- [ ] API returns 400 Bad Request for invalid identifiers"

echo "✓ Issue 2 created"
echo ""

# Issue 3: Missing Input Validation Enforcement in REST API
echo "Creating Issue 3: Missing Input Validation Enforcement in REST API [MEDIUM]"
gh issue create \
  --repo "$REPO" \
  --title "[MEDIUM] Missing Input Validation Enforcement in REST API" \
  --label "security,severity-medium,owasp-a04-insecure-design" \
  --body "## Vulnerability

The REST API controllers in \`ApiController.java\` accept user input through DTOs but do not enforce validation using the \`@Valid\` annotation. While DTO classes define validation constraints using Jakarta Bean Validation annotations, these constraints are never enforced because the \`@Valid\` annotation is missing from controller method parameters.

## OWASP Classification

**Category**: A04:2021 - Insecure Design  
**Reference**: https://owasp.org/Top10/A04_2021-Insecure_Design/

**Description**: Input validation is a critical security control that should be enforced at system boundaries. The failure to enforce validation annotations represents a design flaw where security controls exist but are not activated.

## Location

**File**: \`src/main/java/com/bacnet/emulator/controller/ApiController.java\`

**Affected endpoints**:
- Line 62: \`createDevice\` - missing @Valid on DeviceDto
- Line 83: \`updateDevice\` - missing @Valid on DeviceDto
- Line 141: \`createObject\` - missing @Valid on ObjectDto
- Line 164: \`updateObject\` - missing @Valid on ObjectDto
- Lines 220-223: configuration update endpoints

## Risk Assessment

**Impact for BACnet Emulator (Local Development Tool)**:

1. **Data Integrity Issues**: Invalid device/object configurations stored in database
2. **Application Crashes**: Missing required fields cause NullPointerExceptions
3. **Protocol Violations**: Invalid values violate BACnet protocol
4. **Poor User Experience**: Validation errors occur deep in service layer

**Severity**: MEDIUM - Weakens security posture and can cause data corruption and crashes.

## Suggested Fix

Add \`@Valid\` annotation to all controller method parameters:

\`\`\`java
import jakarta.validation.Valid;

@PostMapping(\"/devices\")
public ResponseEntity<DeviceDto> createDevice(
        @Valid @RequestBody DeviceDto deviceDto,  // ADDED @Valid
        HttpServletRequest request) {
    // ...
}

@PutMapping(\"/devices/{id}\")
public ResponseEntity<DeviceDto> updateDevice(
        @PathVariable Long id,
        @Valid @RequestBody DeviceDto deviceDto,  // ADDED @Valid
        HttpServletRequest request) {
    // ...
}

@PostMapping(\"/objects\")
public ResponseEntity<ObjectDto> createObject(
        @Valid @RequestBody ObjectDto objectDto,  // ADDED @Valid
        HttpServletRequest request) {
    // ...
}

@PutMapping(\"/objects/{id}\")
public ResponseEntity<ObjectDto> updateObject(
        @PathVariable Long id,
        @Valid @RequestBody ObjectDto objectDto) {  // ADDED @Valid
    // ...
}
\`\`\`

Create GlobalExceptionHandler:
\`\`\`java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            errors.put(fieldName, error.getDefaultMessage());
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put(\"status\", \"error\");
        response.put(\"message\", \"Validation failed\");
        response.put(\"errors\", errors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
\`\`\`

## Acceptance Criteria

- [ ] @Valid annotation added to all controller methods accepting DTOs
- [ ] GlobalExceptionHandler created for MethodArgumentNotValidException
- [ ] Validation errors return 400 Bad Request with structured messages
- [ ] Unit tests verify validation enforcement
- [ ] Existing valid API calls continue to work"

echo "✓ Issue 3 created"
echo ""

# Issue 4: Race Condition in Device/Object Cache Refresh
echo "Creating Issue 4: Race Condition in Device/Object Cache Refresh [MEDIUM]"
gh issue create \
  --repo "$REPO" \
  --title "[MEDIUM] Race Condition in Device/Object Cache Refresh" \
  --label "security,severity-medium,owasp-a04-insecure-design" \
  --body "## Vulnerability

The \`BacnetService.java\` class implements a periodic cache refresh mechanism that clears and rebuilds device and object caches. However, the cache clearing and rebuilding operations are not atomic, creating a race condition where BACnet protocol handlers may access empty caches during the refresh period.

## OWASP Classification

**Category**: A04:2021 - Insecure Design  
**Reference**: https://owasp.org/Top10/A04_2021-Insecure_Design/

**Description**: This vulnerability represents an insecure design where concurrent operations are not properly synchronized. Race conditions are a common security flaw where the timing and ordering of operations can lead to incorrect system state.

## Location

**File**: \`src/main/java/com/bacnet/emulator/service/BacnetService.java\`

**Lines 667-692** - Cache refresh method:
\`\`\`java
private void refreshDeviceCache() {
    while (running) {
        try {
            Thread.sleep(5000);
            
            deviceCache.clear();  // RACE CONDITION: Cache is now empty
            objectCache.clear();  // RACE CONDITION: Cache is now empty
            // GAP: If BACnet request arrives here, it will fail
            
            List<BacnetDevice> devices = deviceRepository.findByEnabledTrue();
            for (BacnetDevice device : devices) {
                deviceCache.put(device.getDeviceInstanceId(), device);
                // ... rebuild cache
            }
        } catch (InterruptedException e) {
            // ...
        }
    }
}
\`\`\`

## Risk Assessment

**Impact for BACnet Emulator (Local Development Tool)**:

1. **Intermittent Request Failures**: Every 5 seconds, there's a window where requests fail with \"device not found\"
2. **Testing Reliability**: Integration tests experience random failures
3. **Client Confusion**: BACnet clients see devices disappear and reappear
4. **Inconsistent State**: Request might find device but fail to find objects

**Severity**: MEDIUM - Causes intermittent service failures but does not lead to data corruption.

## Suggested Fix

Implement atomic cache updates using a read-write lock:

\`\`\`java
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

private void handleReadProperty(...) {
    cacheLock.readLock().lock();
    try {
        BacnetDevice device = deviceCache.get(deviceInstance);
        // ... rest of processing
    } finally {
        cacheLock.readLock().unlock();
    }
}

private void refreshDeviceCache() {
    while (running) {
        try {
            Thread.sleep(5000);
            
            // Build new caches first
            Map<Integer, BacnetDevice> newDeviceCache = new ConcurrentHashMap<>();
            Map<String, BacnetObject> newObjectCache = new ConcurrentHashMap<>();
            
            // ... populate new caches ...
            
            // Atomic swap under write lock
            cacheLock.writeLock().lock();
            try {
                deviceCache.clear();
                deviceCache.putAll(newDeviceCache);
                objectCache.clear();
                objectCache.putAll(newObjectCache);
            } finally {
                cacheLock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            // ...
        }
    }
}
\`\`\`

## Acceptance Criteria

- [ ] Cache refresh operations do not clear caches before new data is ready
- [ ] Cache reads protected with read locks
- [ ] Cache updates use write locks for atomic swap
- [ ] No \"device not found\" errors during cache refresh
- [ ] Unit tests verify concurrent reads during refresh succeed
- [ ] Load test confirms no intermittent failures"

echo "✓ Issue 4 created"
echo ""

# Issue 5: Thread Pool Exhaustion Leading to Denial of Service
echo "Creating Issue 5: Thread Pool Exhaustion Leading to Denial of Service [MEDIUM]"
gh issue create \
  --repo "$REPO" \
  --title "[MEDIUM] Thread Pool Exhaustion Leading to Denial of Service" \
  --label "security,severity-medium,owasp-a04-insecure-design" \
  --body "## Vulnerability

The \`BacnetService.java\` class uses a fixed-size thread pool (10 threads) to process incoming BACnet packets without any form of backpressure or queue limiting. The \`listenForPackets\` method continuously submits packet handling tasks to the executor service without checking if threads are available or if the queue is full.

## OWASP Classification

**Category**: A04:2021 - Insecure Design  
**Reference**: https://owasp.org/Top10/A04_2021-Insecure_Design/

**Description**: The lack of resource limits and backpressure mechanisms represents an insecure design pattern where the system does not protect itself from resource exhaustion.

## Location

**File**: \`src/main/java/com/bacnet/emulator/service/BacnetService.java\`

**Lines 73, 111, 117-131**:
\`\`\`java
executorService = Executors.newFixedThreadPool(10);  // Fixed pool

private void listenForPackets() {
    byte[] buffer = new byte[1476];
    
    while (running && socket != null && !socket.isClosed()) {
        try {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            
            // VULNERABILITY: No check for queue size
            executorService.submit(() -> handlePacket(packet));  // Unbounded
        } catch (IOException e) {
            // ...
        }
    }
}
\`\`\`

## Risk Assessment

**Impact for BACnet Emulator (Local Development Tool)**:

1. **Denial of Service**: Attacker on local network can flood with UDP packets
2. **Memory Exhaustion**: Unbounded queue grows until OutOfMemoryError
3. **Development/Testing Disruption**: High-volume testing can accidentally DoS the emulator
4. **No Graceful Degradation**: System doesn't drop packets when overloaded

**Severity**: MEDIUM - Can cause complete service failure but requires sustained packet flood.

## Suggested Fix

Implement bounded queue with backpressure:

\`\`\`java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

private static final int QUEUE_CAPACITY = 100;
private final AtomicLong droppedPackets = new AtomicLong(0);

@PostConstruct
public void initialize() {
    ArrayBlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    
    RejectedExecutionHandler rejectionHandler = (runnable, executor) -> {
        long dropped = droppedPackets.incrementAndGet();
        if (dropped % 100 == 0) {
            log.warn(\"Thread pool saturated. Dropped packets: {}\", dropped);
        }
    };
    
    executorService = new ThreadPoolExecutor(
        5, 10, 60L, TimeUnit.SECONDS,
        workQueue, rejectionHandler
    );
}

private void listenForPackets() {
    byte[] buffer = new byte[1476];
    
    while (running && socket != null && !socket.isClosed()) {
        try {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            
            try {
                executorService.execute(() -> handlePacket(packet));
            } catch (RejectedExecutionException e) {
                // Queue full - packet dropped
            }
        } catch (IOException e) {
            // ...
        }
    }
}
\`\`\`

## Acceptance Criteria

- [ ] Thread pool uses bounded queue with configurable capacity
- [ ] Packets dropped when queue is full
- [ ] Dropped packet count tracked and logged
- [ ] Configuration properties allow tuning pool sizes
- [ ] Load test confirms system remains responsive under packet flood
- [ ] No OutOfMemoryError under sustained load"

echo "✓ Issue 5 created"
echo ""

# Issue 6: Unlimited COV Subscriptions Leading to Memory Exhaustion
echo "Creating Issue 6: Unlimited COV Subscriptions Leading to Memory Exhaustion [LOW]"
gh issue create \
  --repo "$REPO" \
  --title "[LOW] Unlimited COV Subscriptions Leading to Memory Exhaustion" \
  --label "security,severity-low,owasp-a04-insecure-design" \
  --body "## Vulnerability

The \`BacnetService.java\` class stores Change of Value (COV) subscriptions in an unbounded \`ConcurrentHashMap\` without any limits on the number of subscriptions or subscription lifetime. An attacker or misconfigured client can create unlimited subscriptions, eventually exhausting available memory.

## OWASP Classification

**Category**: A04:2021 - Insecure Design  
**Reference**: https://owasp.org/Top10/A04_2021-Insecure_Design/

**Description**: The lack of resource limits on subscription storage represents an insecure design where the system does not protect itself from resource exhaustion attacks.

## Location

**File**: \`src/main/java/com/bacnet/emulator/service/BacnetService.java\`

**Line 68**: Unbounded subscription map
\`\`\`java
private final Map<String, CovSubscription> covSubscriptions = new ConcurrentHashMap<>();
\`\`\`

**Lines 366-396**: handleSubscribeCOV method adds subscriptions without limits

## Risk Assessment

**Impact for BACnet Emulator (Local Development Tool)**:

1. **Memory Exhaustion**: Each subscription consumes memory; unlimited subscriptions lead to OutOfMemoryError
2. **Performance Degradation**: Large subscription map slows down notification checks
3. **Resource Leaks**: Subscriptions from disconnected clients never cleaned up
4. **Testing Impact**: Long-running tests accumulate subscriptions

**Severity**: LOW - Requires sustained attack or long-running misconfiguration.

## Suggested Fix

Implement subscription limits and expiration:

\`\`\`java
import java.time.Instant;

private static final int MAX_SUBSCRIPTIONS = 1000;
private static final long SUBSCRIPTION_TIMEOUT_SECONDS = 3600; // 1 hour

private void handleSubscribeCOV(...) {
    // Validate device and object exist
    BacnetDevice device = deviceCache.get(deviceInstance);
    if (device == null) {
        sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x01);
        return;
    }
    
    // Check subscription limit
    if (covSubscriptions.size() >= MAX_SUBSCRIPTIONS) {
        log.warn(\"Subscription limit reached: {}\", MAX_SUBSCRIPTIONS);
        sendErrorResponse(sourceAddress, sourcePort, invokeId, 0x04);
        return;
    }
    
    CovSubscription subscription = new CovSubscription();
    subscription.expirationTime = Instant.now().plusSeconds(SUBSCRIPTION_TIMEOUT_SECONDS);
    // ... set other fields
    
    covSubscriptions.put(subscriptionKey, subscription);
}

private void cleanupExpiredSubscriptions() {
    while (running) {
        Thread.sleep(60000);  // Check every minute
        
        Instant now = Instant.now();
        covSubscriptions.entrySet().removeIf(entry -> 
            entry.getValue().expirationTime.isBefore(now)
        );
    }
}
\`\`\`

## Acceptance Criteria

- [ ] Maximum subscription limit enforced (default 1000)
- [ ] Subscriptions expire after timeout (default 1 hour)
- [ ] Background thread removes expired subscriptions
- [ ] Subscriptions validate device/object exist
- [ ] Unit tests verify limit enforcement and expiration
- [ ] Memory usage stays bounded under subscription flood"

echo "✓ Issue 6 created"
echo ""

# Issue 7: Log Injection via Unsanitized User Input
echo "Creating Issue 7: Log Injection via Unsanitized User Input [LOW]"
gh issue create \
  --repo "$REPO" \
  --title "[LOW] Log Injection via Unsanitized User Input" \
  --label "security,severity-low,owasp-a03-injection" \
  --body "## Vulnerability

The API controllers and BACnet service log user-controlled data without sanitization or validation. Device names, object names, and other user-provided strings are directly interpolated into log messages, allowing attackers to inject malicious content including newlines, ANSI escape codes, and control characters.

## OWASP Classification

**Category**: A03:2021 - Injection  
**Reference**: https://owasp.org/Top10/A03_2021-Injection/

**Description**: Log injection is a form of injection attack where attackers insert malicious data into log files. While less severe than SQL injection, log injection can be used to cover tracks, forge audit trails, or exploit log viewing tools.

## Location

**File**: \`src/main/java/com/bacnet/emulator/controller/ApiController.java\`

**Lines 66-69**: createDevice logging - unsanitized device name, vendor ID, model name
**Lines 145-151**: createObject logging - unsanitized object name, present value
**Lines 183-187**: updateObjectValue logging - unsanitized object name and value

Similar issues in \`BacnetService.java\` lines 235, 278-279, 287-288, 346-348, 388, 433

## Risk Assessment

**Impact for BACnet Emulator (Local Development Tool)**:

1. **Log Forgery**: Inject fake log entries to hide malicious activity
2. **Log Parser Breaking**: Special characters break log analysis tools
3. **Terminal Manipulation**: ANSI escape codes manipulate terminal output
4. **Development Impact**: Confusing log output makes debugging difficult

**Severity**: LOW - Primarily affects log integrity and analysis, not direct security compromise.

## Suggested Fix

Create log sanitization utility:

\`\`\`java
public class LogSanitizer {
    public static String sanitize(String input) {
        if (input == null) return \"[null]\";
        
        // Remove ANSI escape codes
        String sanitized = input.replaceAll(\"\\\\u001B\\\\[[;\\\\d]*m\", \"\");
        
        // Replace newlines and carriage returns
        sanitized = sanitized.replace(\"\\n\", \"\\\\n\");
        sanitized = sanitized.replace(\"\\r\", \"\\\\r\");
        
        // Replace tabs with spaces
        sanitized = sanitized.replace(\"\\t\", \" \");
        
        // Remove control characters
        sanitized = sanitized.replaceAll(\"[\\\\x00-\\\\x1F\\\\x7F]\", \"\");
        
        // Truncate if too long
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200) + \"...[truncated]\";
        }
        
        return sanitized;
    }
}
\`\`\`

Apply to all log statements:
\`\`\`java
monitorService.log(\"INFO\", 
    String.format(\"Device created: %s\", 
        LogSanitizer.sanitize(created.getDeviceName())),
    // ...
);
\`\`\`

## Acceptance Criteria

- [ ] LogSanitizer utility class created
- [ ] All user-controlled strings sanitized before logging
- [ ] Newlines/carriage returns replaced with escaped versions
- [ ] ANSI escape codes removed
- [ ] Control characters removed
- [ ] Long strings truncated
- [ ] Unit tests verify sanitization of malicious input"

echo "✓ Issue 7 created"
echo ""

echo "=========================================="
echo "✓ All labels and issues created successfully!"
echo "=========================================="
