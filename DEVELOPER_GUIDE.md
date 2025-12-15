# Developer Guide

This guide is for developers who want to understand, extend, or contribute to the BACnet Emulator project.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Project Structure](#project-structure)
3. [Core Components](#core-components)
4. [Development Setup](#development-setup)
5. [Adding New Features](#adding-new-features)
6. [BACnet Protocol Implementation](#bacnet-protocol-implementation)
7. [Testing](#testing)
8. [Code Style and Conventions](#code-style-and-conventions)
9. [Debugging](#debugging)
10. [Performance Considerations](#performance-considerations)

## Architecture Overview

The BACnet Emulator follows a layered architecture pattern:

```
┌─────────────────────────────────────┐
│      Web Interface (Thymeleaf)      │
│      REST API (Spring MVC)          │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         Controllers Layer            │
│  (Device, Object, Network, Monitor) │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│          Services Layer             │
│  (Business Logic & Orchestration)  │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      Repository Layer (JPA)         │
│      (Data Access)                  │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      Database (H2/PostgreSQL)       │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│    BACnet Protocol Layer            │
│    (UDP Socket, Message Handling)   │
└─────────────────────────────────────┘
```

### Key Design Principles

- **Separation of Concerns**: Each layer has a distinct responsibility
- **Dependency Injection**: Spring's IoC container manages dependencies
- **Repository Pattern**: Data access is abstracted through repositories
- **DTO Pattern**: Data transfer objects separate API from domain models
- **Service Layer**: Business logic is encapsulated in service classes

## Project Structure

```
bacnet-emulator/
├── src/
│   ├── main/
│   │   ├── java/com/bacnet/emulator/
│   │   │   ├── config/              # Configuration classes
│   │   │   │   ├── WebConfig.java
│   │   │   │   └── BacnetConfig.java (if needed)
│   │   │   ├── controller/          # Web and REST controllers
│   │   │   │   ├── DeviceController.java
│   │   │   │   ├── ObjectController.java
│   │   │   │   ├── NetworkController.java
│   │   │   │   ├── MonitorController.java
│   │   │   │   ├── ApiController.java
│   │   │   │   └── IndexController.java
│   │   │   ├── dto/                 # Data Transfer Objects
│   │   │   │   ├── DeviceDto.java
│   │   │   │   ├── ObjectDto.java
│   │   │   │   ├── NetworkConfigDto.java
│   │   │   │   └── EmulatorConfigDto.java
│   │   │   ├── model/               # JPA Entities
│   │   │   │   ├── BacnetDevice.java
│   │   │   │   ├── BacnetObject.java
│   │   │   │   ├── NetworkConfig.java
│   │   │   │   ├── EmulatorConfig.java
│   │   │   │   └── BacnetLogEntry.java
│   │   │   ├── repository/          # Data Access Layer
│   │   │   │   ├── DeviceRepository.java
│   │   │   │   ├── ObjectRepository.java
│   │   │   │   ├── NetworkConfigRepository.java
│   │   │   │   ├── EmulatorConfigRepository.java
│   │   │   │   └── LogEntryRepository.java
│   │   │   └── service/             # Business Logic
│   │   │       ├── BacnetService.java      # Core BACnet protocol
│   │   │       ├── DeviceService.java
│   │   │       ├── ObjectService.java
│   │   │       ├── ConfigService.java
│   │   │       └── MonitorService.java
│   │   └── resources/
│   │       ├── templates/           # Thymeleaf templates
│   │       │   ├── index.html
│   │       │   ├── devices/
│   │       │   ├── objects/
│   │       │   ├── network/
│   │       │   └── monitor/
│   │       └── application.yml      # Application configuration
│   └── test/                        # Test classes
└── pom.xml                          # Maven configuration
```

## Core Components

### 1. BACnetService

**Location**: `com.bacnet.emulator.service.BacnetService`

**Purpose**: Core BACnet protocol implementation. Handles UDP communication and BACnet message processing.

**Key Methods**:
- `initialize()`: Starts the UDP server
- `handlePacket()`: Processes incoming BACnet packets
- `handleWhoIs()`: Responds to device discovery requests
- `handleReadProperty()`: Handles property read requests
- `handleWriteProperty()`: Handles property write requests
- `sendIAm()`: Sends I-Am response for device discovery

**Extension Points**:
- Add new BACnet service handlers in `handleConfirmedRequest()`
- Extend message encoding/decoding in helper methods
- Add new object types in `getPropertyValue()`

### 2. DeviceService

**Location**: `com.bacnet.emulator.service.DeviceService`

**Purpose**: Manages virtual BACnet devices.

**Key Methods**:
- `createDevice()`: Creates a new device with validation
- `updateDevice()`: Updates device properties
- `getEnabledDevices()`: Returns only enabled devices (for BACnet responses)

**Extension Points**:
- Add device-level validation rules
- Add device templates or presets
- Add device import/export functionality

### 3. ObjectService

**Location**: `com.bacnet.emulator.service.ObjectService`

**Purpose**: Manages BACnet objects within devices.

**Key Methods**:
- `createObject()`: Creates a new object with validation
- `updateObjectValue()`: Updates present value (triggers COV if enabled)
- `getObjectByDeviceAndTypeAndInstance()`: Finds objects by BACnet identifier

**Extension Points**:
- Add new object types (Multistate, Accumulator, Loop, etc.)
- Add object value validation rules
- Add object templates or presets

### 4. ConfigService

**Location**: `com.bacnet.emulator.service.ConfigService`

**Purpose**: Manages emulator and network configuration.

**Key Methods**:
- `getNetworkConfig()`: Retrieves network settings
- `updateNetworkConfig()`: Updates network settings
- `getEmulatorConfig()`: Retrieves behavior settings

**Extension Points**:
- Add new configuration options
- Add configuration validation
- Add configuration import/export

### 5. MonitorService

**Location**: `com.bacnet.emulator.service.MonitorService`

**Purpose**: Logs BACnet activity for monitoring and debugging.

**Key Methods**:
- `log()`: Creates a log entry
- `getRecentLogs()`: Retrieves recent log entries

**Extension Points**:
- Add log filtering and search
- Add log export functionality
- Add real-time log streaming via WebSocket

## Development Setup

### Prerequisites

1. **JDK 17+**: Install Java Development Kit
2. **Maven 3.6+**: Install Apache Maven
3. **IDE**: IntelliJ IDEA, Eclipse, or VS Code with Java extensions
4. **Lombok Plugin**: Required for IDE support

### IDE Setup

#### IntelliJ IDEA

1. Install Lombok plugin:
   - Settings → Plugins → Search "Lombok" → Install
2. Enable annotation processing:
   - Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   - Check "Enable annotation processing"
3. Import project:
   - File → Open → Select `pom.xml`
   - Maven will download dependencies

#### Eclipse

1. Install Lombok:
   - Download from https://projectlombok.org/download
   - Run: `java -jar lombok.jar`
   - Select Eclipse installation
2. Import project:
   - File → Import → Maven → Existing Maven Projects
   - Select project directory

### Building the Project

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package JAR
mvn clean package

# Run application
mvn spring-boot:run
```

### Database Setup

The project uses H2 embedded database by default. To use PostgreSQL or MySQL:

1. Add dependency to `pom.xml`:
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

2. Update `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bacnet
    username: your_username
    password: your_password
```

## Adding New Features

### Adding a New BACnet Object Type

1. **Update ObjectService**:
   - Add object type constant
   - Update `getObjectTypeName()` method
   - Add value parsing logic in `parsePresentValue()`

2. **Update UI**:
   - Add object type to dropdown in `objects/create.html` and `objects/edit.html`
   - Update `ObjectController.getObjectTypes()` method

3. **Update Documentation**:
   - Add to README.md
   - Update API documentation

### Adding a New BACnet Service

1. **Extend BacnetService**:
   - Add handler method (e.g., `handleNewService()`)
   - Add case in `handleConfirmedRequest()` or `handleUnconfirmedRequest()`
   - Implement request parsing and response generation

2. **Add Logging**:
   - Log requests in `MonitorService`
   - Update log viewer if needed

3. **Add Tests**:
   - Unit tests for service handler
   - Integration tests with BACnet client

### Adding a New REST Endpoint

1. **Add to ApiController**:
   - Add method with `@GetMapping`, `@PostMapping`, etc.
   - Add Swagger annotations
   - Call appropriate service method

2. **Add Swagger Documentation**:
   - `@Operation`: Describe the endpoint
   - `@ApiResponses`: Document response codes
   - `@Parameter`: Document parameters

3. **Update Tests**:
   - Add controller test
   - Add integration test

## BACnet Protocol Implementation

### Message Structure

BACnet/IP messages have the following structure:

```
┌─────────────────────────────────┐
│   BACnet/IP Header (4 bytes)    │
│   - Type (1 byte)               │
│   - Function (1 byte)           │
│   - Length (2 bytes)            │
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│   NPDU (Network Protocol Data)  │
│   - Version (1 byte)            │
│   - Control (1 byte)            │
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│   APDU (Application Protocol)   │
│   - PDU Type (4 bits)           │
│   - Service Choice (4 bits)     │
│   - Invoke ID (1 byte)          │
│   - Service Parameters          │
└─────────────────────────────────┘
```

### Encoding/Decoding

The `BacnetService` includes helper methods for encoding/decoding:

- `encodeObjectIdentifier()`: Encodes BACnet object identifiers
- `encodeUnsignedInt()`: Encodes unsigned integers
- `encodeValue()`: Encodes property values (Real, String, Boolean, etc.)
- `extractDeviceInstance()`: Extracts device instance from message
- `extractObjectType()`: Extracts object type from message

### Adding Protocol Support

To add support for a new BACnet service:

1. **Parse Request**:
   - Extract service parameters from APDU
   - Validate required parameters

2. **Process Request**:
   - Query database for device/object
   - Perform requested operation
   - Handle errors appropriately

3. **Generate Response**:
   - Build APDU response
   - Encode response data
   - Send via UDP socket

## Testing

### Unit Testing

Create test classes in `src/test/java`:

```java
@SpringBootTest
class DeviceServiceTest {
    
    @Autowired
    private DeviceService deviceService;
    
    @Test
    void testCreateDevice() {
        DeviceDto dto = new DeviceDto();
        dto.setDeviceInstanceId(1000);
        dto.setDeviceName("Test Device");
        
        DeviceDto created = deviceService.createDevice(dto);
        
        assertNotNull(created.getId());
        assertEquals(1000, created.getDeviceInstanceId());
    }
}
```

### Integration Testing

Test BACnet protocol interactions:

```java
@SpringBootTest
@AutoConfigureMockMvc
class BacnetIntegrationTest {
    
    @Test
    void testWhoIsDiscovery() throws Exception {
        // Send Who-Is request
        // Verify I-Am response received
    }
}
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=DeviceServiceTest

# Run with coverage
mvn test jacoco:report
```

## Code Style and Conventions

### Naming Conventions

- **Classes**: PascalCase (e.g., `BacnetDevice`)
- **Methods**: camelCase (e.g., `getDeviceById()`)
- **Variables**: camelCase (e.g., `deviceInstanceId`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `DEFAULT_PORT`)

### JavaDoc

All public classes and methods should have JavaDoc:

```java
/**
 * Retrieves a device by its instance ID.
 *
 * @param deviceInstanceId the BACnet device instance ID
 * @return the device DTO
 * @throws RuntimeException if device not found
 */
public DeviceDto getDeviceByInstanceId(Integer deviceInstanceId) {
    // implementation
}
```

### Code Organization

- Keep methods focused (single responsibility)
- Extract complex logic into helper methods
- Use meaningful variable names
- Add comments for complex algorithms
- Keep methods under 50 lines when possible

## Debugging

### Enable Debug Logging

Update `application.yml`:

```yaml
logging:
  level:
    com.bacnet.emulator: DEBUG
    org.springframework.web: DEBUG
```

### Debug BACnet Messages

Add logging in `BacnetService.handlePacket()`:

```java
log.debug("Received packet from {}:{}", packet.getAddress(), packet.getPort());
log.debug("Packet data: {}", Hex.encodeHexString(data));
```

### Use H2 Console

Access H2 console at `http://localhost:8080/h2-console`:
- JDBC URL: `jdbc:h2:file:./data/bacnet-emulator`
- Username: `sa`
- Password: (empty)

### Network Debugging

Use Wireshark or tcpdump to capture BACnet/IP traffic:

```bash
# Capture on port 47808
sudo tcpdump -i any -n port 47808 -X
```

## Performance Considerations

### Database Queries

- Use `@Query` annotations for complex queries
- Add database indexes for frequently queried fields
- Use `@EntityGraph` to avoid N+1 query problems

### Caching

Consider adding caching for:
- Device lookups (by instance ID)
- Object lookups (by device + type + instance)
- Configuration values

Example with Spring Cache:

```java
@Cacheable("devices")
public DeviceDto getDeviceByInstanceId(Integer deviceInstanceId) {
    // ...
}
```

### Threading

- `BacnetService` uses thread pool for packet handling
- Adjust pool size based on expected load
- Consider async processing for heavy operations

### Memory Management

- Device and object caches are refreshed periodically
- Log entries should be rotated/archived for production
- Consider pagination for large result sets

## Common Tasks

### Adding a New Field to Device

1. Add field to `BacnetDevice` entity
2. Add field to `DeviceDto`
3. Update `DeviceService.toDto()` and `toEntity()`
4. Update create/edit forms in Thymeleaf templates
5. Update API documentation

### Changing Database Schema

1. Update entity class
2. Hibernate will auto-update schema (if `ddl-auto: update`)
3. For production, use Flyway or Liquibase migrations

### Adding a New Page

1. Create Thymeleaf template in `resources/templates/`
2. Add controller method
3. Add navigation link in sidebar
4. Update routing if needed

## Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [BACnet Protocol Standard](https://www.bacnet.org/)
- [Thymeleaf Documentation](https://www.thymeleaf.org/documentation.html)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Swagger/OpenAPI](https://swagger.io/specification/)

## Getting Help

- Check existing [GitHub Issues](https://github.com/yourusername/bacnet-emulator/issues)
- Review code comments and JavaDoc
- Ask questions in [GitHub Discussions](https://github.com/yourusername/bacnet-emulator/discussions)
- Review the [Wiki](https://github.com/yourusername/bacnet-emulator/wiki)

---

Happy coding! 🚀

