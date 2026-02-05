# [LOW] Log Injection via Unsanitized User Input

**Labels**: `security`, `severity-low`, `owasp-a03-injection`

## Vulnerability

The API controllers and BACnet service log user-controlled data without sanitization or validation. Device names, object names, and other user-provided strings are directly interpolated into log messages. This allows attackers to inject malicious content into logs, including newlines, ANSI escape codes, and other control characters that can:
- Forge log entries to hide malicious activity
- Break log parsing tools
- Inject ANSI codes to manipulate terminal output
- Confuse log analysis and monitoring systems

## OWASP Classification

**Category**: A03:2021 - Injection  
**Reference**: https://owasp.org/Top10/A03_2021-Injection/

**Description**: This OWASP category covers injection flaws that occur when untrusted data is sent to an interpreter. Log injection is a form of injection attack where attackers insert malicious data into log files. While less severe than SQL injection or command injection, log injection can be used to cover tracks, forge audit trails, or exploit log viewing tools.

## Location

**File**: `src/main/java/com/bacnet/emulator/controller/ApiController.java`

**Lines 66-69** - createDevice logging:
```java
monitorService.log("INFO", 
    String.format("Device created via API: %s (Instance ID: %d)", 
        created.getDeviceName(),  // User-controlled, unsanitized
        created.getDeviceInstanceId()),
    request.getRemoteAddr(), "REST API - CreateDevice", created.getDeviceInstanceId(), null, null,
    String.format("Device ID: %d, Vendor: %s, Model: %s", 
        created.getId(), 
        created.getVendorId(),    // User-controlled, unsanitized
        created.getModelName()));  // User-controlled, unsanitized
```

**Lines 145-151** - createObject logging:
```java
monitorService.log("INFO",
    String.format("Object created via API: %s (Type: %s, Instance: %d)", 
        created.getObjectName(),     // User-controlled, unsanitized
        created.getObjectTypeName(), 
        created.getObjectInstance()),
    request.getRemoteAddr(), "REST API - CreateObject", device.getDeviceInstanceId(), 
    created.getObjectType(), created.getObjectInstance(),
    String.format("Present Value: %s, Writable: %s, COV: %s", 
        created.getPresentValue(),  // User-controlled, unsanitized
        created.getWritable(), created.getCovEnabled()));
```

**Lines 183-187** - updateObjectValue logging:
```java
monitorService.log("INFO",
    String.format("Object value updated via API: %s = %s", 
        updated.getObjectName(),    // User-controlled, unsanitized
        updated.getPresentValue()), // User-controlled, unsanitized
```

**Similar issues in**:
- `BacnetService.java` lines 235, 278-279, 287-288, 346-348, 388, 433

## Risk Assessment

**Impact for BACnet Emulator (Local Development Tool)**:

1. **Log Forgery**:
   - Attacker creates device with name: `"TestDevice\n[2024-01-01 00:00:00] INFO  Backdoor user created"`
   - Logs appear to show events that never happened
   - Audit trails become unreliable

2. **Log Parser Breaking**:
   - Device names with tab characters, null bytes, or special characters
   - Break log analysis tools that assume well-formed log entries
   - Security monitoring systems fail to parse logs correctly

3. **Terminal Manipulation**:
   - Device name with ANSI escape codes: `"Device\u001b[2J\u001b[H"` (clear screen)
   - When logs viewed in terminal, screen is cleared
   - Can hide malicious activity from administrators

4. **Cross-Site Scripting in Log Viewers**:
   - If logs viewed in web-based log viewer
   - Device name: `"Device<script>alert('XSS')</script>"`
   - Could execute JavaScript in log viewer (if not escaped)

5. **Development Impact**:
   - Developers debugging issues see confusing log output
   - Legitimate newlines in object names break log formatting
   - Difficult to trace actual system behavior

**Severity**: LOW - Primarily affects log integrity and analysis. Does not directly compromise system security, but can hide other attacks and confuse incident response.

## Suggested Fix

Sanitize all user-controlled data before logging:

### 1. Create Log Sanitizer Utility

Create new file `src/main/java/com/bacnet/emulator/util/LogSanitizer.java`:

```java
package com.bacnet.emulator.util;

import org.springframework.stereotype.Component;

/**
 * Utility class for sanitizing user-controlled data before logging.
 * Prevents log injection attacks by removing or escaping dangerous characters.
 */
@Component
public class LogSanitizer {
    
    /**
     * Sanitizes a string for safe inclusion in log messages.
     * - Removes newline and carriage return characters
     * - Removes ANSI escape codes
     * - Removes other control characters
     * - Truncates overly long strings
     * 
     * @param input The string to sanitize
     * @return Sanitized string safe for logging, or "[null]" if input is null
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "[null]";
        }
        
        // Remove ANSI escape codes (ESC followed by bracket and parameters)
        String sanitized = input.replaceAll("\u001B\\[[;\\d]*m", "");
        
        // Remove other common ANSI escape sequences
        sanitized = sanitized.replaceAll("\u001B\\[([0-9]{1,2}(;[0-9]{1,2})?)?[mGKHflSTuABCDEFnpsuJ]", "");
        
        // Replace newlines and carriage returns with escaped versions
        sanitized = sanitized.replace("\n", "\\n");
        sanitized = sanitized.replace("\r", "\\r");
        
        // Replace tabs with spaces
        sanitized = sanitized.replace("\t", " ");
        
        // Remove null bytes and other control characters (except space)
        sanitized = sanitized.replaceAll("[\\x00-\\x1F\\x7F]", "");
        
        // Truncate if too long (prevent log flooding)
        int maxLength = 200;
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength) + "...[truncated]";
        }
        
        return sanitized;
    }
    
    /**
     * Sanitizes a string and adds quotes around it for clarity in logs.
     */
    public static String sanitizeAndQuote(String input) {
        return "\"" + sanitize(input) + "\"";
    }
    
    /**
     * Sanitizes multiple values and joins them with a separator.
     */
    public static String sanitizeAll(String separator, String... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(sanitize(values[i]));
        }
        return sb.toString();
    }
}
```

### 2. Update ApiController to Use Sanitizer

```java
import com.bacnet.emulator.util.LogSanitizer;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    
    private final DeviceService deviceService;
    private final ObjectService objectService;
    private final ConfigService configService;
    private final MonitorService monitorService;
    
    @PostMapping("/devices")
    public ResponseEntity<DeviceDto> createDevice(
            @Valid @RequestBody DeviceDto deviceDto,
            HttpServletRequest request) {
        DeviceDto created = deviceService.createDevice(deviceDto);
        monitorService.log("INFO", 
            String.format("Device created via API: %s (Instance ID: %d)", 
                LogSanitizer.sanitizeAndQuote(created.getDeviceName()),  // SANITIZED
                created.getDeviceInstanceId()),
            request.getRemoteAddr(), "REST API - CreateDevice", created.getDeviceInstanceId(), null, null,
            String.format("Device ID: %d, Vendor: %s, Model: %s", 
                created.getId(), 
                LogSanitizer.sanitize(created.getVendorId()),    // SANITIZED
                LogSanitizer.sanitize(created.getModelName())));  // SANITIZED
        return ResponseEntity.ok(created);
    }
    
    @PutMapping("/devices/{id}")
    public ResponseEntity<DeviceDto> updateDevice(
            @PathVariable Long id,
            @Valid @RequestBody DeviceDto deviceDto,
            HttpServletRequest request) {
        DeviceDto updated = deviceService.updateDevice(id, deviceDto);
        monitorService.log("INFO",
            String.format("Device updated via API: %s (Instance ID: %d)", 
                LogSanitizer.sanitizeAndQuote(updated.getDeviceName()),  // SANITIZED
                updated.getDeviceInstanceId()),
            request.getRemoteAddr(), "REST API - UpdateDevice", updated.getDeviceInstanceId(), null, null,
            String.format("Enabled: %s", updated.getEnabled()));
        return ResponseEntity.ok(updated);
    }
    
    @PostMapping("/objects")
    public ResponseEntity<ObjectDto> createObject(
            @Valid @RequestBody ObjectDto objectDto,
            HttpServletRequest request) {
        ObjectDto created = objectService.createObject(objectDto);
        DeviceDto device = deviceService.getDeviceById(created.getDeviceId());
        monitorService.log("INFO",
            String.format("Object created via API: %s (Type: %s, Instance: %d)", 
                LogSanitizer.sanitizeAndQuote(created.getObjectName()),  // SANITIZED
                created.getObjectTypeName(), 
                created.getObjectInstance()),
            request.getRemoteAddr(), "REST API - CreateObject", device.getDeviceInstanceId(), 
            created.getObjectType(), created.getObjectInstance(),
            String.format("Present Value: %s, Writable: %s, COV: %s", 
                LogSanitizer.sanitize(created.getPresentValue()),  // SANITIZED
                created.getWritable(), created.getCovEnabled()));
        return ResponseEntity.ok(created);
    }
    
    @PutMapping("/objects/{id}/value")
    public ResponseEntity<ObjectDto> updateObjectValue(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        String value = request.get("presentValue");
        ObjectDto updated = objectService.updateObjectValue(id, value);
        DeviceDto device = deviceService.getDeviceById(updated.getDeviceId());
        monitorService.log("INFO",
            String.format("Object value updated via API: %s = %s", 
                LogSanitizer.sanitizeAndQuote(updated.getObjectName()),     // SANITIZED
                LogSanitizer.sanitizeAndQuote(updated.getPresentValue())),  // SANITIZED
            httpRequest.getRemoteAddr(), "REST API - UpdateValue", device.getDeviceInstanceId(),
            updated.getObjectType(), updated.getObjectInstance(),
            String.format("Previous value changed to: %s", 
                LogSanitizer.sanitize(value)));  // SANITIZED
        return ResponseEntity.ok(updated);
    }
}
```

### 3. Add Unit Tests for Log Sanitizer

Create `src/test/java/com/bacnet/emulator/util/LogSanitizerTest.java`:

```java
package com.bacnet.emulator.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LogSanitizerTest {
    
    @Test
    void testSanitizeNull() {
        assertEquals("[null]", LogSanitizer.sanitize(null));
    }
    
    @Test
    void testSanitizeNewlines() {
        String input = "Device\nName\rwith\r\nbreaks";
        String result = LogSanitizer.sanitize(input);
        assertFalse(result.contains("\n"));
        assertFalse(result.contains("\r"));
        assertTrue(result.contains("\\n"));
        assertTrue(result.contains("\\r"));
    }
    
    @Test
    void testSanitizeAnsiCodes() {
        String input = "Device\u001B[31mRed\u001B[0mNormal";
        String result = LogSanitizer.sanitize(input);
        assertFalse(result.contains("\u001B"));
        assertEquals("DeviceRedNormal", result);
    }
    
    @Test
    void testSanitizeControlCharacters() {
        String input = "Device\u0000Name\u0001\u0002";
        String result = LogSanitizer.sanitize(input);
        assertFalse(result.contains("\u0000"));
        assertEquals("DeviceName", result);
    }
    
    @Test
    void testSanitizeTabs() {
        String input = "Device\tName";
        String result = LogSanitizer.sanitize(input);
        assertFalse(result.contains("\t"));
        assertTrue(result.contains(" "));
    }
    
    @Test
    void testTruncateLongStrings() {
        String input = "A".repeat(300);
        String result = LogSanitizer.sanitize(input);
        assertTrue(result.length() <= 220); // 200 + "[truncated]"
        assertTrue(result.endsWith("...[truncated]"));
    }
    
    @Test
    void testSanitizeAndQuote() {
        String input = "Device Name";
        String result = LogSanitizer.sanitizeAndQuote(input);
        assertEquals("\"Device Name\"", result);
    }
}
```

## Acceptance Criteria

- [ ] LogSanitizer utility class created with sanitize methods
- [ ] All user-controlled strings sanitized before logging in ApiController
- [ ] All user-controlled strings sanitized before logging in BacnetService
- [ ] Newlines and carriage returns replaced with escaped versions (\\n, \\r)
- [ ] ANSI escape codes removed from log output
- [ ] Control characters removed from log output
- [ ] Long strings truncated to prevent log flooding
- [ ] Unit tests verify sanitization of:
  - Null values
  - Newlines and carriage returns
  - ANSI escape codes
  - Control characters
  - Tab characters
  - Overly long strings
- [ ] Integration tests confirm:
  - Device creation with malicious names doesn't corrupt logs
  - Log parsing tools can process all log entries
  - No ANSI codes appear in log files
- [ ] Existing legitimate names (with normal characters) log correctly
- [ ] Performance impact of sanitization is negligible (< 1ms per log entry)
