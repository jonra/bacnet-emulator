# Demo Scripts for BACnet Emulator

This directory contains scripts to quickly configure the emulator and generate activity logs for presentations and demonstrations.

## Scripts Overview

### 1. `demo-script.py` - REST API Only
**Sets up devices/objects via REST API** (no BACnet protocol messages)

- Creates devices and objects
- Updates values via REST API
- Generates REST API logs only
- **Does NOT send BACnet protocol messages**

### 2. `demo-script-bacnet.py` - Full Protocol Demo ⭐ **RECOMMENDED**
**Sets up devices/objects AND sends real BACnet protocol messages**

- Creates devices and objects via REST API
- Sends actual BACnet protocol messages (Who-Is, ReadProperty, WriteProperty)
- Generates both REST API logs AND BACnet protocol logs
- **Best for presentations showing real BACnet interactions**

## Quick Start

### Automated Setup (Easiest)

**One-time setup:**
```bash
./setup-demo.sh
```

This script will:
- Create a Python virtual environment
- Install all required dependencies (requests, BAC0)
- Optionally run the demo immediately

**Run demo anytime:**
```bash
./run-demo.sh protocol    # Full demo with BACnet protocol (recommended)
./run-demo.sh basic       # Basic scenario (REST API only)
./run-demo.sh full        # Full scenario (REST API only)
```

### Manual Setup

### Option 1: Full Protocol Demo (Recommended)

**Prerequisites:**
```bash
pip3 install requests BAC0
```

**Usage:**
```bash
# Full demo with BACnet protocol interactions
python3 demo-script-bacnet.py

# Custom host
python3 demo-script-bacnet.py --host 192.168.1.100
```

**What it does:**
1. Creates devices and objects via REST API
2. Sends Who-Is request (device discovery)
3. Sends ReadProperty requests
4. Sends WriteProperty requests
5. Generates comprehensive logs showing both REST API and BACnet protocol activity

### Option 2: REST API Only

**Prerequisites:**
```bash
pip3 install requests
```

**Basic Usage:**
```bash
# Basic scenario (single device, few objects)
python3 demo-script.py

# Full scenario (multiple devices, many objects)
python3 demo-script.py --scenario full

# Custom host/port
python3 demo-script.py --host 192.168.1.100 --port 8080
```

**What it does:**
- Creates BACnet devices and objects
- Simulates value changes and updates
- Generates REST API activity logs only
- **Does NOT send BACnet protocol messages**

### Shell Script (Alternative)

The shell script is a lightweight alternative that only requires `curl`.

**Prerequisites:**
- `curl` (usually pre-installed)
- `jq` (optional, for pretty output)

**Usage:**
```bash
# Basic usage
./demo-script.sh

# Custom host/port
./demo-script.sh localhost 8080
```

## Scenarios

### Basic Scenario

Creates a simple HVAC system with:
- 1 device (HVAC Controller)
- 3 objects:
  - Temperature sensor (Analog Input)
  - Humidity sensor (Analog Input)
  - Cooling setpoint (Analog Output, writable)
- Simulates temperature changes
- Updates setpoint

**Duration:** ~15-20 seconds

### Full Scenario

Creates a complete building automation system with:
- 3 devices:
  - HVAC System
  - Lighting Controller
  - Security Panel
- 10+ objects across all devices
- Multiple value changes and updates
- Various object types (Analog Input/Output, Binary Input/Output)

**Duration:** ~30-40 seconds

## Viewing the Logs

After running the script, view the generated logs at:

```
http://localhost:8080/monitor/logs
```

### With `demo-script-bacnet.py` (Full Protocol):
The logs will show:
- **REST API - CreateDevice**: Device creation via API
- **REST API - CreateObject**: Object creation via API
- **Who-Is**: BACnet device discovery request
- **ReadProperty**: BACnet property read requests
- **WriteProperty**: BACnet property write requests
- **I-Am**: BACnet device discovery responses
- Timestamps, source addresses, and full details

### With `demo-script.py` (REST API Only):
The logs will show:
- **REST API - CreateDevice**: Device creation
- **REST API - CreateObject**: Object creation
- **REST API - UpdateValue**: Value updates
- **Note**: No BACnet protocol messages (Who-Is, ReadProperty, etc.)

## Presentation Tips

1. **Run the script before your presentation** to populate the logs
2. **Run it multiple times** to generate more activity
3. **Use the full scenario** for a more impressive demo
4. **Show the logs page** to demonstrate the monitoring capabilities
5. **Show the dashboard** to display device/object statistics

## Example Workflow

```bash
# 1. Start the emulator
mvn spring-boot:run

# 2. In another terminal, run the demo script
python3 demo-script.py --scenario full

# 3. Open your browser to view logs
open http://localhost:8080/monitor/logs

# 4. Show the dashboard
open http://localhost:8080/
```

## Troubleshooting

### Connection Error

If you see a connection error:
- Make sure the emulator is running: `mvn spring-boot:run`
- Check the host and port are correct
- Verify the emulator started successfully

### Python Module Not Found

**Option 1: Use the setup script (recommended)**
```bash
./setup-demo.sh
```

**Option 2: Manual installation**
```bash
# Create virtual environment
python3 -m venv venv

# Activate it
source venv/bin/activate

# Install dependencies
pip install requests BAC0
```

### Script Not Executable

Make scripts executable:
```bash
chmod +x setup-demo.sh
chmod +x run-demo.sh
chmod +x demo-script.py
chmod +x demo-script-bacnet.py
chmod +x demo-script.sh
```

### Virtual Environment Issues

If you get import errors:
1. Make sure the virtual environment is activated: `source venv/bin/activate`
2. Re-run setup: `./setup-demo.sh`
3. Check Python version: `python3 --version` (needs 3.7+)

## Customization

You can modify the scripts to:
- Create different device configurations
- Add more object types
- Simulate different scenarios
- Adjust timing and delays
- Add more value changes

Edit the script files to customize the demo for your specific needs.

