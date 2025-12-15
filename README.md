# BACnet Emulator

[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A standalone, open-source BACnet/IP emulator built with Spring Boot. This emulator allows developers and testers to simulate BACnet devices without requiring physical hardware, making it ideal for development, testing, and integration scenarios.

## 🎯 Purpose

The BACnet Emulator provides a virtual BACnet server that responds to BACnet client requests exactly like physical devices would. This is particularly useful for:

- **Development**: Test BACnet client applications without physical hardware
- **Integration Testing**: Simulate various BACnet device configurations
- **Training**: Learn BACnet protocol interactions in a controlled environment
- **CI/CD Pipelines**: Automated testing of BACnet-dependent applications

## ✨ Features

### Core Functionality

- **Full BACnet/IP Protocol Support**
  - Who-Is / I-Am device discovery
  - ReadProperty / WriteProperty operations
  - ReadPropertyMultiple for batch operations
  - SubscribeCOV (Change of Value) subscriptions
  - Alarm and Event handling

- **Device Management**
  - Create and manage multiple virtual BACnet devices
  - Configure device properties (Instance ID, Name, Vendor ID, etc.)
  - Enable/disable devices dynamically

- **Object Management**
  - Support for standard BACnet object types:
    - Analog Input (read-only analog values)
    - Analog Output (read/write analog values)
    - Binary Input (read-only binary values)
    - Binary Output (read/write binary values)
  - Configure object properties, present values, and units
  - COV (Change of Value) support with configurable thresholds

- **Web-Based Configuration Interface**
  - Intuitive Thymeleaf-based UI
  - Real-time device and object management
  - Network configuration
  - Activity monitoring and logging

- **RESTful API**
  - Complete REST API for programmatic configuration
  - JSON-based device and object management
  - Suitable for automation and integration

- **Advanced Features**
  - Configurable response delays (simulate network latency)
  - Error simulation capabilities
  - Real-time activity logging
  - Persistent configuration storage

## 📋 Requirements

- **Java**: JDK 17 or higher
- **Maven**: 3.6 or higher (for building from source)
- **Network**: UDP port 47808 available (default BACnet/IP port)

## 🚀 Quick Start

### Option 1: Using Pre-built JAR (Recommended)

1. Download the latest release JAR file from the [Releases](https://github.com/yourusername/bacnet-emulator/releases) page

2. Run the application:
   ```bash
   java -jar bacnet-emulator-1.0.0.jar
   ```

3. Access the web interface at `http://localhost:8080`

4. The BACnet service will be listening on UDP port `47808`

### Option 2: Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/bacnet-emulator.git
   cd bacnet-emulator
   ```

2. Build the project:
   ```bash
   mvn clean package
   ```

3. Run the application:
   ```bash
   java -jar target/bacnet-emulator-1.0.0.jar
   ```

   Or use Maven:
   ```bash
   mvn spring-boot:run
   ```

## 📖 User Guide

### First Steps

1. **Start the Emulator**
   - Run the application using one of the methods above
   - Wait for the startup message: `BACnet server started on 0.0.0.0:47808`

2. **Access the Web Interface**
   - Open your browser and navigate to `http://localhost:8080`
   - You'll see the dashboard with system statistics

3. **Create Your First Device**
   - Click on "Devices" in the sidebar
   - Click "Create Device"
   - Fill in the required fields:
     - **Device Instance ID**: A unique number (e.g., 1000)
     - **Device Name**: A descriptive name (e.g., "HVAC Controller")
     - **Vendor ID**: Optional vendor identifier
     - **Model Name**: Optional model name
   - Click "Create Device"

4. **Add Objects to Your Device**
   - Click on "Objects" in the sidebar
   - Click "Create Object"
   - Select your device from the dropdown
   - Choose an object type (e.g., "Analog Input")
   - Set the Object Instance (e.g., 1)
   - Enter an Object Name (e.g., "Temperature Sensor")
   - Set the Present Value (e.g., "72.5" for temperature)
   - For analog objects, you can set Units (e.g., "degreesCelsius")
   - Configure additional options:
     - **Writable**: Allow WriteProperty operations
     - **COV Enabled**: Enable Change of Value notifications
     - **COV Increment**: Threshold for COV notifications (analog objects)
   - Click "Create Object"

### Using with BACnet Clients

Once you've created devices and objects, you can interact with them using any BACnet client:

1. **Device Discovery**
   - Your BACnet client should send a Who-Is request
   - The emulator will respond with I-Am messages for all enabled devices
   - Your client should discover the virtual devices

2. **Reading Values**
   - Use ReadProperty to read object present values
   - Use ReadPropertyMultiple for batch reads
   - Values are returned as configured in the web interface

3. **Writing Values**
   - Use WriteProperty on writable objects
   - Values are updated in real-time
   - COV notifications are sent to subscribers if enabled

### Web Interface Guide

#### Dashboard
- Overview of all devices, enabled devices, and total objects
- Quick access to common actions
- System status indicators

#### Devices Management
- **List Devices**: View all configured devices with their status
- **Create Device**: Add a new virtual BACnet device
- **Edit Device**: Modify device properties
- **Delete Device**: Remove a device (and all its objects)
- **View Objects**: Navigate to objects for a specific device

#### Objects Management
- **List Objects**: View all objects, optionally filtered by device
- **Create Object**: Add a new object to a device
- **Edit Object**: Modify object properties and values
- **Update Value**: Quickly update the present value
- **Delete Object**: Remove an object

#### Network Configuration
- **UDP Port**: Configure the BACnet/IP port (default: 47808)
- **Bind Address**: Network interface to bind to (default: 0.0.0.0 for all interfaces)
- **Broadcast Address**: Broadcast address for device discovery
- **Default Device Instance ID**: Default ID for new devices

> **Note**: Network configuration changes require a server restart to take effect.

#### Monitoring & Logs
- **Activity Logs**: View all BACnet requests and responses
- **Filter by Service Type**: See specific operations (Who-Is, ReadProperty, etc.)
- **Real-time Updates**: Logs update as requests are received
- **Error Tracking**: Monitor errors and exceptions

## 🔧 Configuration

### Application Configuration

Edit `src/main/resources/application.yml` or create `application.properties`:

```yaml
server:
  port: 8080  # Web server port

bacnet:
  network:
    port: 47808  # BACnet/IP UDP port
    bind-address: 0.0.0.0  # Bind to all interfaces
    broadcast-address: 255.255.255.255
  device:
    default-instance-id: 1000
  behavior:
    default-response-delay-ms: 0  # Simulate network latency
    enable-error-simulation: false
```

### Database Configuration

The application uses an embedded H2 database by default. Data is stored in `./data/bacnet-emulator.mv.db`.

To use a different database (PostgreSQL, MySQL, etc.), update the configuration:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bacnet
    username: your_username
    password: your_password
  jpa:
    hibernate:
      ddl-auto: update
```

## 📡 REST API

The emulator provides a complete REST API for programmatic access.

### Base URL
```
http://localhost:8080/api
```

### Device Endpoints

- `GET /api/devices` - List all devices
- `GET /api/devices/{id}` - Get device by ID
- `POST /api/devices` - Create a new device
- `PUT /api/devices/{id}` - Update a device
- `DELETE /api/devices/{id}` - Delete a device

**Example: Create Device**
```bash
curl -X POST http://localhost:8080/api/devices \
  -H "Content-Type: application/json" \
  -d '{
    "deviceInstanceId": 1000,
    "deviceName": "Test Device",
    "vendorId": "123",
    "enabled": true
  }'
```

### Object Endpoints

- `GET /api/objects` - List all objects (optional `?deviceId={id}`)
- `GET /api/objects/{id}` - Get object by ID
- `POST /api/objects` - Create a new object
- `PUT /api/objects/{id}` - Update an object
- `PUT /api/objects/{id}/value` - Update object present value
- `DELETE /api/objects/{id}` - Delete an object

**Example: Create Object**
```bash
curl -X POST http://localhost:8080/api/objects \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": 1,
    "objectType": 0,
    "objectInstance": 1,
    "objectName": "Temperature",
    "presentValue": "72.5",
    "units": "degreesCelsius",
    "writable": false,
    "covEnabled": true
  }'
```

### Configuration Endpoints

- `GET /api/config/network` - Get network configuration
- `PUT /api/config/network` - Update network configuration
- `GET /api/config/emulator` - Get emulator configuration
- `PUT /api/config/emulator` - Update emulator configuration

### Statistics Endpoint

- `GET /api/stats` - Get system statistics

## 🧪 Testing Examples

### Using BACnet Explorer (YABE)

1. Start the emulator
2. Create a device with Instance ID 1000
3. Add some objects (e.g., Analog Input with value 72.5)
4. Open YABE (Yet Another BACnet Explorer)
5. Send a Who-Is broadcast
6. The emulator device should appear in the device list
7. Browse objects and read/write values

### Using Python with BAC0

```python
from BAC0 import lite

# Connect to the emulator
bacnet = lite()

# Discover devices
devices = bacnet.whois()

# Read a property
value = bacnet.read('1000:0 analogInput,1 presentValue')
print(f"Temperature: {value}")

# Write a property (if writable)
bacnet.write('1000:1 analogOutput,1 presentValue', 75.0)
```

### Using Node.js with node-bacnet

```javascript
const bacnet = require('node-bacnet');

const client = new bacnet({
  adr: { host: '127.0.0.1', port: 47808 }
});

// Discover devices
client.whoIs();

// Read property
client.readProperty('127.0.0.1', {
  objectType: 0,  // Analog Input
  objectInstance: 1
}, 85, (err, value) => {
  console.log('Present Value:', value);
});
```

## 🐛 Troubleshooting

### Port Already in Use

**Error**: `Address already in use` or `Port 47808 is already in use`

**Solution**: 
- Change the port in `application.yml`: `bacnet.network.port: 47809`
- Or stop the process using port 47808:
  ```bash
  # Find process
  lsof -i :47808
  # Kill process (replace PID)
  kill -9 <PID>
  ```

### Devices Not Discovered

**Problem**: BACnet client cannot discover emulator devices

**Solutions**:
- Ensure devices are enabled in the web interface
- Check firewall settings (UDP port 47808 must be open)
- Verify network configuration (bind address, broadcast address)
- Ensure client and emulator are on the same network segment
- Try using the emulator's IP address explicitly in the client

### Compilation Errors

**Problem**: Lombok annotation processing errors

**Solutions**:
- Ensure you have Lombok plugin installed in your IDE
- For IntelliJ IDEA: Install "Lombok" plugin from Settings → Plugins
- For Eclipse: Install Lombok from https://projectlombok.org/setup/eclipse
- Or build using Maven command line: `mvn clean package`

### Database Errors

**Problem**: Database connection or schema errors

**Solutions**:
- Delete the database file: `rm -rf ./data/bacnet-emulator.mv.db`
- Restart the application (database will be recreated)
- Check database permissions if using external database

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### Development Setup

1. Fork the repository
2. Clone your fork: `git clone https://github.com/yourusername/bacnet-emulator.git`
3. Create a feature branch: `git checkout -b feature/amazing-feature`
4. Make your changes
5. Commit: `git commit -m 'Add amazing feature'`
6. Push: `git push origin feature/amazing-feature`
7. Open a Pull Request

### Code Style

- Follow Java coding conventions
- Use meaningful variable and method names
- Add Javadoc comments for public APIs
- Write unit tests for new features

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built with [Spring Boot](https://spring.io/projects/spring-boot)
- Uses [Thymeleaf](https://www.thymeleaf.org/) for templating
- BACnet protocol implementation based on BACnet/IP standard (ANSI/ASHRAE 135)

## 📞 Support

- **Issues**: Report bugs or request features on [GitHub Issues](https://github.com/yourusername/bacnet-emulator/issues)
- **Discussions**: Join discussions on [GitHub Discussions](https://github.com/yourusername/bacnet-emulator/discussions)
- **Documentation**: Check the [Wiki](https://github.com/yourusername/bacnet-emulator/wiki) for additional documentation

## 🗺️ Roadmap

- [ ] Support for additional BACnet object types (Multistate, Accumulator, Loop, etc.)
- [ ] BACnet MS/TP transport support
- [ ] WebSocket-based real-time monitoring
- [ ] Device templates and presets
- [ ] Import/export device configurations
- [ ] Advanced error simulation scenarios
- [ ] Performance metrics and analytics
- [ ] Docker containerization
- [ ] Kubernetes deployment manifests

## 📊 Project Status

**Current Version**: 1.0.0

**Status**: Active Development

This project is actively maintained and open to contributions. We're continuously improving the emulator based on user feedback and requirements.

---

**Made with ❤️ for the BACnet community**
