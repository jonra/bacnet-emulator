#!/usr/bin/env python3
"""
BACnet Emulator Demo Script
============================

This script configures the BACnet emulator and generates activity logs
for demonstration purposes. Perfect for presentations and testing.

Usage:
    python3 demo-script.py [--host localhost] [--port 8080] [--scenario basic|full]

Examples:
    python3 demo-script.py
    python3 demo-script.py --scenario full
    python3 demo-script.py --host 192.168.1.100 --port 8080
"""

import argparse
import requests
import time
import json
from datetime import datetime
from typing import Dict, List, Optional

class BacnetEmulatorClient:
    """Client for interacting with the BACnet Emulator REST API."""
    
    def __init__(self, host: str = "localhost", port: int = 8080):
        self.base_url = f"http://{host}:{port}/api"
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})
    
    def create_device(self, instance_id: int, name: str, vendor_id: str = None, 
                     model: str = None, enabled: bool = True) -> Dict:
        """Create a new BACnet device."""
        device = {
            "deviceInstanceId": instance_id,
            "deviceName": name,
            "vendorId": vendor_id,
            "modelName": model,
            "enabled": enabled
        }
        response = self.session.post(f"{self.base_url}/devices", json=device)
        response.raise_for_status()
        return response.json()
    
    def create_object(self, device_id: int, object_type: int, object_instance: int,
                     name: str, present_value: str = None, units: str = None,
                     writable: bool = False, cov_enabled: bool = False) -> Dict:
        """Create a new BACnet object."""
        obj = {
            "deviceId": device_id,
            "objectType": object_type,  # 0=AnalogInput, 1=AnalogOutput, 3=BinaryInput, 4=BinaryOutput
            "objectInstance": object_instance,
            "objectName": name,
            "presentValue": present_value,
            "units": units,
            "writable": writable,
            "covEnabled": cov_enabled
        }
        response = self.session.post(f"{self.base_url}/objects", json=obj)
        response.raise_for_status()
        return response.json()
    
    def update_object_value(self, object_id: int, value: str) -> Dict:
        """Update an object's present value."""
        response = self.session.put(
            f"{self.base_url}/objects/{object_id}/value",
            json={"presentValue": value}
        )
        response.raise_for_status()
        return response.json()
    
    def get_stats(self) -> Dict:
        """Get emulator statistics."""
        response = self.session.get(f"{self.base_url}/stats")
        response.raise_for_status()
        return response.json()
    
    def get_all_devices(self) -> List[Dict]:
        """Get all devices."""
        response = self.session.get(f"{self.base_url}/devices")
        response.raise_for_status()
        return response.json()
    
    def get_all_objects(self, device_id: int = None) -> List[Dict]:
        """Get all objects, optionally filtered by device."""
        url = f"{self.base_url}/objects"
        if device_id:
            url += f"?deviceId={device_id}"
        response = self.session.get(url)
        response.raise_for_status()
        return response.json()


def print_header(text: str):
    """Print a formatted header."""
    print("\n" + "=" * 70)
    print(f"  {text}")
    print("=" * 70)


def print_step(step: int, description: str):
    """Print a formatted step."""
    print(f"\n[Step {step}] {description}")
    print("-" * 70)


def wait_for_activity(seconds: int = 2):
    """Wait to simulate real-world timing."""
    print(f"   ⏳ Waiting {seconds} seconds for activity to be logged...")
    time.sleep(seconds)


def basic_scenario(client: BacnetEmulatorClient):
    """Basic scenario: Create a simple HVAC system."""
    print_header("BASIC SCENARIO: Simple HVAC System")
    
    # Step 1: Create HVAC Controller Device
    print_step(1, "Creating HVAC Controller Device")
    device = client.create_device(
        instance_id=1000,
        name="HVAC Controller",
        vendor_id="12345",
        model="HVAC-2024"
    )
    print(f"   ✅ Created device: {device['deviceName']} (Instance ID: {device['deviceInstanceId']})")
    device_id = device['id']
    wait_for_activity()
    
    # Step 2: Create Temperature Sensor (Analog Input)
    print_step(2, "Creating Temperature Sensor (Analog Input)")
    temp_sensor = client.create_object(
        device_id=device_id,
        object_type=0,  # Analog Input
        object_instance=1,
        name="Room Temperature",
        present_value="72.5",
        units="degreesFahrenheit",
        cov_enabled=True
    )
    print(f"   ✅ Created: {temp_sensor['objectName']} = {temp_sensor['presentValue']}°F")
    wait_for_activity()
    
    # Step 3: Create Humidity Sensor (Analog Input)
    print_step(3, "Creating Humidity Sensor (Analog Input)")
    humidity_sensor = client.create_object(
        device_id=device_id,
        object_type=0,  # Analog Input
        object_instance=2,
        name="Room Humidity",
        present_value="45.0",
        units="percent",
        cov_enabled=True
    )
    print(f"   ✅ Created: {humidity_sensor['objectName']} = {humidity_sensor['presentValue']}%")
    wait_for_activity()
    
    # Step 4: Create Cooling Setpoint (Analog Output - Writable)
    print_step(4, "Creating Cooling Setpoint (Analog Output)")
    cooling_setpoint = client.create_object(
        device_id=device_id,
        object_type=1,  # Analog Output
        object_instance=1,
        name="Cooling Setpoint",
        present_value="75.0",
        units="degreesFahrenheit",
        writable=True,
        cov_enabled=True
    )
    print(f"   ✅ Created: {cooling_setpoint['objectName']} = {cooling_setpoint['presentValue']}°F (Writable)")
    wait_for_activity()
    
    # Step 5: Simulate Value Changes
    print_step(5, "Simulating Temperature Changes")
    values = ["73.2", "74.1", "72.8", "73.5"]
    for i, value in enumerate(values, 1):
        updated = client.update_object_value(temp_sensor['id'], value)
        print(f"   📊 Update {i}: Temperature changed to {value}°F")
        wait_for_activity(1)
    
    # Step 6: Update Setpoint
    print_step(6, "Updating Cooling Setpoint")
    updated = client.update_object_value(cooling_setpoint['id'], "74.0")
    print(f"   ✅ Setpoint updated to {updated['presentValue']}°F")
    wait_for_activity()
    
    # Summary
    print_header("SCENARIO COMPLETE")
    stats = client.get_stats()
    print(f"   Total Devices: {stats['totalDevices']}")
    print(f"   Enabled Devices: {stats['enabledDevices']}")
    print(f"   Total Objects: {stats['totalObjects']}")
    print("\n   📋 View logs at: http://localhost:8080/monitor/logs")
    print("   📊 View dashboard at: http://localhost:8080/")


def full_scenario(client: BacnetEmulatorClient):
    """Full scenario: Multiple devices with various object types."""
    print_header("FULL SCENARIO: Multi-Device Building Automation System")
    
    devices_created = []
    objects_created = []
    
    # Device 1: HVAC System
    print_step(1, "Creating HVAC System")
    hvac = client.create_device(1000, "HVAC System", "HVAC-001", "AC-2024")
    devices_created.append(hvac)
    print(f"   ✅ Device: {hvac['deviceName']} (ID: {hvac['deviceInstanceId']})")
    
    # HVAC Objects
    objects_created.append(client.create_object(
        hvac['id'], 0, 1, "Supply Air Temperature", "68.5", "degreesFahrenheit", False, True
    ))
    objects_created.append(client.create_object(
        hvac['id'], 0, 2, "Return Air Temperature", "72.3", "degreesFahrenheit", False, True
    ))
    objects_created.append(client.create_object(
        hvac['id'], 1, 1, "Cooling Setpoint", "70.0", "degreesFahrenheit", True, True
    ))
    objects_created.append(client.create_object(
        hvac['id'], 4, 1, "Cooling Status", "active", None, False, True
    ))
    print(f"   ✅ Created 4 objects for HVAC system")
    wait_for_activity()
    
    # Device 2: Lighting System
    print_step(2, "Creating Lighting Control System")
    lighting = client.create_device(2000, "Lighting Controller", "LIGHT-001", "LC-2024")
    devices_created.append(lighting)
    print(f"   ✅ Device: {lighting['deviceName']} (ID: {lighting['deviceInstanceId']})")
    
    # Lighting Objects
    objects_created.append(client.create_object(
        lighting['id'], 0, 1, "Ambient Light Level", "450", "lux", False, True
    ))
    objects_created.append(client.create_object(
        lighting['id'], 4, 1, "Main Lights", "active", None, True, True
    ))
    objects_created.append(client.create_object(
        lighting['id'], 4, 2, "Task Lights", "inactive", None, True, True
    ))
    print(f"   ✅ Created 3 objects for lighting system")
    wait_for_activity()
    
    # Device 3: Security System
    print_step(3, "Creating Security System")
    security = client.create_device(3000, "Security Panel", "SEC-001", "SP-2024")
    devices_created.append(security)
    print(f"   ✅ Device: {security['deviceName']} (ID: {security['deviceInstanceId']})")
    
    # Security Objects
    objects_created.append(client.create_object(
        security['id'], 3, 1, "Front Door Sensor", "closed", None, False, True
    ))
    objects_created.append(client.create_object(
        security['id'], 3, 2, "Back Door Sensor", "closed", None, False, True
    ))
    objects_created.append(client.create_object(
        security['id'], 4, 1, "Alarm Status", "inactive", None, False, True
    ))
    print(f"   ✅ Created 3 objects for security system")
    wait_for_activity()
    
    # Simulate Activity
    print_step(4, "Simulating System Activity")
    
    # Temperature changes
    print("   🌡️  Simulating temperature fluctuations...")
    for value in ["69.2", "70.1", "68.8", "69.5", "70.2"]:
        client.update_object_value(objects_created[0]['id'], value)
        print(f"      Supply Air Temp: {value}°F")
        wait_for_activity(0.5)
    
    # Lighting changes
    print("   💡 Simulating lighting changes...")
    client.update_object_value(objects_created[5]['id'], "inactive")
    print("      Main Lights: OFF")
    wait_for_activity(1)
    client.update_object_value(objects_created[5]['id'], "active")
    print("      Main Lights: ON")
    wait_for_activity(1)
    
    # Security events
    print("   🔒 Simulating security events...")
    client.update_object_value(objects_created[7]['id'], "open")
    print("      Front Door: OPENED")
    wait_for_activity(1)
    client.update_object_value(objects_created[7]['id'], "closed")
    print("      Front Door: CLOSED")
    wait_for_activity(1)
    
    # Setpoint adjustment
    print("   ⚙️  Adjusting setpoints...")
    client.update_object_value(objects_created[2]['id'], "69.0")
    print("      Cooling Setpoint: 69.0°F")
    wait_for_activity()
    
    # Summary
    print_header("FULL SCENARIO COMPLETE")
    stats = client.get_stats()
    print(f"   Total Devices Created: {stats['totalDevices']}")
    print(f"   Enabled Devices: {stats['enabledDevices']}")
    print(f"   Total Objects Created: {stats['totalObjects']}")
    print(f"\n   📋 View detailed logs at: http://localhost:8080/monitor/logs")
    print(f"   📊 View dashboard at: http://localhost:8080/")
    print(f"   🔍 View devices at: http://localhost:8080/devices")
    print(f"   📦 View objects at: http://localhost:8080/objects")


def main():
    parser = argparse.ArgumentParser(
        description="BACnet Emulator Demo Script - Generate activity logs for presentations",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python3 demo-script.py                    # Basic scenario on localhost
  python3 demo-script.py --scenario full    # Full multi-device scenario
  python3 demo-script.py --host 192.168.1.100  # Use different host
        """
    )
    parser.add_argument(
        "--host", 
        default="localhost",
        help="Emulator host (default: localhost)"
    )
    parser.add_argument(
        "--port",
        type=int,
        default=8080,
        help="Emulator port (default: 8080)"
    )
    parser.add_argument(
        "--scenario",
        choices=["basic", "full"],
        default="basic",
        help="Scenario to run (default: basic)"
    )
    
    args = parser.parse_args()
    
    print_header("BACnet Emulator Demo Script")
    print(f"   Connecting to: http://{args.host}:{args.port}")
    print(f"   Scenario: {args.scenario}")
    print(f"   Started at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    
    try:
        client = BacnetEmulatorClient(args.host, args.port)
        
        # Test connection
        print("\n   🔌 Testing connection...")
        stats = client.get_stats()
        print(f"   ✅ Connected! Current stats: {stats['totalDevices']} devices, {stats['totalObjects']} objects")
        
        # Run scenario
        if args.scenario == "basic":
            basic_scenario(client)
        else:
            full_scenario(client)
        
        print_header("DEMO COMPLETE")
        print("\n   ✅ All activities have been logged!")
        print(f"   📋 View logs: http://{args.host}:{args.port}/monitor/logs")
        print(f"   📊 Dashboard: http://{args.host}:{args.port}/")
        print("\n   💡 Tip: Keep this script running or run it multiple times")
        print("      to generate more activity logs for your presentation.")
        
    except requests.exceptions.ConnectionError:
        print("\n   ❌ ERROR: Could not connect to emulator!")
        print(f"   Make sure the emulator is running at http://{args.host}:{args.port}")
        print("   Start it with: mvn spring-boot:run")
        return 1
    except requests.exceptions.HTTPError as e:
        print(f"\n   ❌ ERROR: HTTP {e.response.status_code}")
        print(f"   Response: {e.response.text}")
        return 1
    except Exception as e:
        print(f"\n   ❌ ERROR: {type(e).__name__}: {e}")
        return 1
    
    return 0


if __name__ == "__main__":
    exit(main())

