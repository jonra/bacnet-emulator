# [MEDIUM] Missing Input Validation Enforcement in REST API

**Labels**: `security`, `severity-medium`, `owasp-a04-insecure-design`

## Vulnerability

The REST API controllers in `ApiController.java` accept user input through DTOs but do not enforce validation using the `@Valid` annotation. While the DTO classes define validation constraints using Jakarta Bean Validation annotations (e.g., `@NotNull`, `@NotBlank`, `@Min`), these constraints are never enforced because the `@Valid` annotation is missing from the controller method parameters.

This allows invalid data to bypass validation and reach the service layer, where it may cause unexpected behavior, exceptions, or data corruption.

## OWASP Classification

**Category**: A04:2021 - Insecure Design  
**Reference**: https://owasp.org/Top10/A04_2021-Insecure_Design/

**Description**: This OWASP category covers flaws in design and architecture. Input validation is a critical security control that should be enforced at system boundaries. The failure to enforce validation annotations represents a design flaw where security controls exist but are not activated, allowing invalid data into the application.

## Location

**File**: `src/main/java/com/bacnet/emulator/controller/ApiController.java`

**Affected endpoints**:

1. **Line 62** - createDevice endpoint:
```java
@PostMapping("/devices")
public ResponseEntity<DeviceDto> createDevice(
        @Parameter(description = "Device configuration", required = true) @RequestBody DeviceDto deviceDto,
        // Missing @Valid annotation
        HttpServletRequest request) {
```

2. **Line 83** - updateDevice endpoint:
```java
@PutMapping("/devices/{id}")
public ResponseEntity<DeviceDto> updateDevice(
        @Parameter(description = "Device database ID", required = true) @PathVariable Long id,
        @Parameter(description = "Updated device configuration", required = true) @RequestBody DeviceDto deviceDto,
        // Missing @Valid annotation
        HttpServletRequest request) {
```

3. **Line 141** - createObject endpoint:
```java
@PostMapping("/objects")
public ResponseEntity<ObjectDto> createObject(
        @Parameter(description = "Object configuration", required = true) @RequestBody ObjectDto objectDto,
        // Missing @Valid annotation
        HttpServletRequest request) {
```

4. **Line 164** - updateObject endpoint:
```java
@PutMapping("/objects/{id}")
public ResponseEntity<ObjectDto> updateObject(
        @Parameter(description = "Object database ID", required = true) @PathVariable Long id,
        @Parameter(description = "Updated object configuration", required = true) @RequestBody ObjectDto objectDto) {
        // Missing @Valid annotation
```

5. **Lines 220-223** - configuration update endpoints (NetworkConfigDto, EmulatorConfigDto)

## Risk Assessment

**Impact for BACnet Emulator (Local Development Tool)**:

1. **Data Integrity Issues**: Invalid device/object configurations can be stored in the database, causing:
   - Null pointer exceptions during BACnet protocol handling
   - Invalid BACnet responses sent to clients
   - Database constraint violations with unclear error messages

2. **Application Crashes**: Missing required fields (e.g., deviceName, objectName) will cause NullPointerExceptions in service layer or BACnet protocol handling

3. **Protocol Violations**: Invalid values (e.g., negative object types, zero/negative instance IDs) violate BACnet protocol and cause interoperability issues

4. **Poor User Experience**: Validation errors occur deep in the service layer rather than immediately at the API boundary, making debugging difficult

5. **Security Boundary Weakness**: While not directly exploitable for RCE, weak input validation increases attack surface and may combine with other vulnerabilities

**Severity**: MEDIUM - Does not directly lead to system compromise but weakens security posture and can cause data corruption and crashes.

## Suggested Fix

Add `@Valid` annotation to all controller method parameters that accept user input:

### 1. Update ApiController.java

```java
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "BACnet Emulator API", description = "RESTful API for managing BACnet devices, objects, and configuration")
public class ApiController {
    
    // ... existing fields ...
    
    @PostMapping("/devices")
    public ResponseEntity<DeviceDto> createDevice(
            @Valid @RequestBody DeviceDto deviceDto,  // ADDED @Valid
            HttpServletRequest request) {
        DeviceDto created = deviceService.createDevice(deviceDto);
        // ... rest of method
    }
    
    @PutMapping("/devices/{id}")
    public ResponseEntity<DeviceDto> updateDevice(
            @PathVariable Long id,
            @Valid @RequestBody DeviceDto deviceDto,  // ADDED @Valid
            HttpServletRequest request) {
        DeviceDto updated = deviceService.updateDevice(id, deviceDto);
        // ... rest of method
    }
    
    @PostMapping("/objects")
    public ResponseEntity<ObjectDto> createObject(
            @Valid @RequestBody ObjectDto objectDto,  // ADDED @Valid
            HttpServletRequest request) {
        ObjectDto created = objectService.createObject(objectDto);
        // ... rest of method
    }
    
    @PutMapping("/objects/{id}")
    public ResponseEntity<ObjectDto> updateObject(
            @PathVariable Long id,
            @Valid @RequestBody ObjectDto objectDto) {  // ADDED @Valid
        return ResponseEntity.ok(objectService.updateObject(id, objectDto));
    }
    
    @PutMapping("/config/network")
    public ResponseEntity<NetworkConfigDto> updateNetworkConfig(
            @Valid @RequestBody NetworkConfigDto configDto) {  // ADDED @Valid
        return ResponseEntity.ok(configService.updateNetworkConfig(configDto));
    }
    
    @PutMapping("/config/emulator")
    public ResponseEntity<EmulatorConfigDto> updateEmulatorConfig(
            @Valid @RequestBody EmulatorConfigDto configDto) {  // ADDED @Valid
        return ResponseEntity.ok(configService.updateEmulatorConfig(configDto));
    }
}
```

### 2. Add Global Exception Handler for Validation Errors

Create a new file `src/main/java/com/bacnet/emulator/controller/GlobalExceptionHandler.java`:

```java
package com.bacnet.emulator.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", "Validation failed");
        response.put("errors", errors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
```

### 3. Enhance DTO Validation Constraints

Review and strengthen validation in DTOs:

```java
// DeviceDto.java - add additional constraints
@NotBlank(message = "Device Name is required and cannot be empty")
@Size(min = 1, max = 100, message = "Device Name must be between 1 and 100 characters")
private String deviceName;

@Size(max = 50, message = "Vendor ID cannot exceed 50 characters")
private String vendorId;

@Size(max = 100, message = "Model Name cannot exceed 100 characters")
private String modelName;

@Size(max = 500, message = "Description cannot exceed 500 characters")
private String description;
```

```java
// ObjectDto.java - add range validation
@NotBlank(message = "Object Name is required and cannot be empty")
@Size(min = 1, max = 100, message = "Object Name must be between 1 and 100 characters")
private String objectName;

@Size(max = 50, message = "Units cannot exceed 50 characters")
private String units;

@Size(max = 500, message = "Description cannot exceed 500 characters")
private String description;

@DecimalMin(value = "0.0", message = "COV Increment must be non-negative")
private Double covIncrement;
```

## Acceptance Criteria

- [ ] `@Valid` annotation added to all controller methods accepting DTOs
- [ ] GlobalExceptionHandler created to handle MethodArgumentNotValidException
- [ ] Validation errors return 400 Bad Request with structured error messages
- [ ] Error response includes field names and validation messages
- [ ] Unit tests verify validation enforcement:
  - Test creating device with null deviceName returns 400
  - Test creating device with negative deviceInstanceId returns 400
  - Test creating object with null objectName returns 400
  - Test creating object with negative objectInstance returns 400
  - Test valid requests still succeed (200 OK)
- [ ] Integration tests confirm validation messages are clear and helpful
- [ ] API documentation (Swagger) reflects validation constraints
- [ ] Existing valid API calls continue to work without changes
