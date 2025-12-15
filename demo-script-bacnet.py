#!/usr/bin/env python3
"""
BACnet Emulator Demo Script with Real BACnet Protocol Interactions
===================================================================

This script:
1. Sets up devices and objects via REST API
2. Sends actual BACnet protocol messages (Who-Is, ReadProperty, WriteProperty)
3. Generates both REST API logs AND BACnet protocol logs

Prerequisites:
    pip3 install requests BAC0

Usage:
    python3 demo-script-bacnet.py [--host localhost] [--port 8080]
"""

import argparse
import requests
import time
import json
from datetime import datetime
from typing import Dict, List, Optional

try:
    from BAC0 import lite
    BAC0_AVAILABLE = True
except ImportError:
    BAC0_AVAILABLE = False
    print("⚠️  BAC0 library not available. Install with: pip3 install BAC0")
    print("   Falling back to REST API only mode...")

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
            "objectType": object_type,
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
    
    def get_stats(self) -> Dict:
        """Get emulator statistics."""
        response = self.session.get(f"{self.base_url}/stats")
        response.raise_for_status()
        return response.json()


class BacnetProtocolClient:
    """Client for sending actual BACnet protocol messages."""
    
    def __init__(self, host: str = "localhost"):
        self.host = host
        self.bacnet = None
        if BAC0_AVAILABLE:
            try:
                self.bacnet = lite()
                print(f"   ✅ BACnet client initialized")
            except Exception as e:
                print(f"   ⚠️  Could not initialize BACnet client: {e}")
                self.bacnet = None
    
    def who_is(self, low_limit: int = None, high_limit: int = None):
        """Send Who-Is request to discover devices."""
        if not self.bacnet:
            print("   ⚠️  BAC0 not available, skipping Who-Is")
            return []
        
        try:
            print(f"   📡 Sending Who-Is broadcast...")
            devices = self.bacnet.whois(low_limit, high_limit)
            print(f"   ✅ Discovered {len(devices)} device(s)")
            return devices
        except Exception as e:
            print(f"   ⚠️  Who-Is failed: {e}")
            return []
    
    def read_property(self, device_instance: int, object_type: int, 
                     object_instance: int, property_id: int = 85):
        """Read a property from a BACnet object."""
        if not self.bacnet:
            print("   ⚠️  BAC0 not available, skipping ReadProperty")
            return None
        
        try:
            # BAC0 format: 'device_instance:object_type,object_instance property_id'
            address = f"{device_instance}:{object_type},{object_instance} presentValue"
            value = self.bacnet.read(address)
            print(f"   ✅ ReadProperty: {value}")
            return value
        except Exception as e:
            print(f"   ⚠️  ReadProperty failed: {e}")
            return None
    
    def write_property(self, device_instance: int, object_type: int,
                      object_instance: int, value):
        """Write a property to a BACnet object."""
        if not self.bacnet:
            print("   ⚠️  BAC0 not available, skipping WriteProperty")
            return False
        
        try:
            address = f"{device_instance}:{object_type},{object_instance} presentValue"
            self.bacnet.write(address, value)
            print(f"   ✅ WriteProperty: {value}")
            return True
        except Exception as e:
            print(f"   ⚠️  WriteProperty failed: {e}")
            return False
    
    def close(self):
        """Close BACnet connection."""
        if self.bacnet:
            try:
                self.bacnet.disconnect()
            except:
                pass


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
    print(f"   ⏳ Waiting {seconds} seconds...")
    time.sleep(seconds)


def full_demo(rest_client: BacnetEmulatorClient, bacnet_client: BacnetProtocolClient):
    """Full demo with both REST API setup and BACnet protocol interactions."""
    print_header("FULL DEMO: Setup + BACnet Protocol Interactions")
    
    # Phase 1: Setup via REST API
    print_header("PHASE 1: Setting Up Devices and Objects (REST API)")
    
    print_step(1, "Creating HVAC Controller Device")
    device = rest_client.create_device(
        instance_id=1000,
        name="HVAC Controller",
        vendor_id="12345",
        model="HVAC-2024"
    )
    print(f"   ✅ Created device: {device['deviceName']} (Instance ID: {device['deviceInstanceId']})")
    device_id = device['id']
    wait_for_activity(2)
    
    print_step(2, "Creating Temperature Sensor (Analog Input)")
    temp_sensor = rest_client.create_object(
        device_id=device_id,
        object_type=0,  # Analog Input
        object_instance=1,
        name="Room Temperature",
        present_value="72.5",
        units="degreesFahrenheit",
        cov_enabled=True
    )
    print(f"   ✅ Created: {temp_sensor['objectName']} = {temp_sensor['presentValue']}°F")
    wait_for_activity(2)
    
    print_step(3, "Creating Cooling Setpoint (Analog Output - Writable)")
    cooling_setpoint = rest_client.create_object(
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
    wait_for_activity(2)
    
    print_step(4, "Creating Binary Status (Binary Output)")
    status = rest_client.create_object(
        device_id=device_id,
        object_type=4,  # Binary Output
        object_instance=1,
        name="Cooling Status",
        present_value="active",
        writable=True,
        cov_enabled=True
    )
    print(f"   ✅ Created: {status['objectName']} = {status['presentValue']}")
    wait_for_activity(2)
    
    # Phase 2: BACnet Protocol Interactions
    print_header("PHASE 2: BACnet Protocol Interactions")
    
    if not BAC0_AVAILABLE:
        print("\n   ⚠️  BAC0 library not installed. Skipping protocol interactions.")
        print("   Install with: pip3 install BAC0")
        print("\n   ✅ REST API logs are still available at http://localhost:8080/monitor/logs")
        return
    
    print_step(5, "Sending Who-Is Request (Device Discovery)")
    devices = bacnet_client.who_is()
    wait_for_activity(2)
    
    print_step(6, "Reading Temperature Sensor (ReadProperty)")
    value = bacnet_client.read_property(
        device_instance=1000,
        object_type=0,  # Analog Input
        object_instance=1,
        property_id=85  # Present Value
    )
    wait_for_activity(2)
    
    print_step(7, "Reading Cooling Setpoint (ReadProperty)")
    value = bacnet_client.read_property(
        device_instance=1000,
        object_type=1,  # Analog Output
        object_instance=1,
        property_id=85  # Present Value
    )
    wait_for_activity(2)
    
    print_step(8, "Writing Cooling Setpoint (WriteProperty)")
    success = bacnet_client.write_property(
        device_instance=1000,
        object_type=1,  # Analog Output
        object_instance=1,
        value=74.0
    )
    wait_for_activity(2)
    
    print_step(9, "Reading Updated Setpoint (ReadProperty)")
    value = bacnet_client.read_property(
        device_instance=1000,
        object_type=1,  # Analog Output
        object_instance=1,
        property_id=85  # Present Value
    )
    wait_for_activity(2)
    
    print_step(10, "Reading Binary Status (ReadProperty)")
    value = bacnet_client.read_property(
        device_instance=1000,
        object_type=4,  # Binary Output
        object_instance=1,
        property_id=85  # Present Value
    )
    wait_for_activity(2)
    
    # Summary
    print_header("DEMO COMPLETE")
    stats = rest_client.get_stats()
    print(f"   Total Devices: {stats['totalDevices']}")
    print(f"   Enabled Devices: {stats['enabledDevices']}")
    print(f"   Total Objects: {stats['totalObjects']}")
    print("\n   📋 View logs at: http://localhost:8080/monitor/logs")
    print("   📊 View dashboard at: http://localhost:8080/")
    print("\n   The logs will show:")
    print("   • REST API calls (device/object creation)")
    print("   • BACnet protocol messages (Who-Is, ReadProperty, WriteProperty)")


def main():
    parser = argparse.ArgumentParser(
        description="BACnet Emulator Demo with Protocol Interactions",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python3 demo-script-bacnet.py                    # Full demo with protocol
  python3 demo-script-bacnet.py --host 192.168.1.100

Note: Install BAC0 for protocol interactions:
  pip3 install BAC0
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
        help="Emulator REST API port (default: 8080)"
    )
    
    args = parser.parse_args()
    
    print_header("BACnet Emulator Demo Script with Protocol Interactions")
    print(f"   REST API: http://{args.host}:{args.port}")
    print(f"   BACnet Port: 47808")
    print(f"   Started at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    
    if not BAC0_AVAILABLE:
        print("\n   ⚠️  BAC0 library not installed.")
        print("   The script will still work but only show REST API logs.")
        print("   Install BAC0 for full protocol interactions: pip3 install BAC0")
        time.sleep(2)
    
    try:
        rest_client = BacnetEmulatorClient(args.host, args.port)
        bacnet_client = BacnetProtocolClient(args.host)
        
        # Test REST API connection
        print("\n   🔌 Testing REST API connection...")
        stats = rest_client.get_stats()
        print(f"   ✅ Connected! Current stats: {stats['totalDevices']} devices, {stats['totalObjects']} objects")
        
        # Run demo
        full_demo(rest_client, bacnet_client)
        
        # Cleanup
        bacnet_client.close()
        
        print_header("DEMO COMPLETE")
        print("\n   ✅ All activities have been logged!")
        print(f"   📋 View logs: http://{args.host}:{args.port}/monitor/logs")
        print("\n   The logs page will show:")
        print("   • REST API - CreateDevice")
        print("   • REST API - CreateObject")
        print("   • Who-Is (if BAC0 installed)")
        print("   • ReadProperty (if BAC0 installed)")
        print("   • WriteProperty (if BAC0 installed)")
        
    except requests.exceptions.ConnectionError:
        print("\n   ❌ ERROR: Could not connect to emulator!")
        print(f"   Make sure the emulator is running at http://{args.host}:{args.port}")
        print("   Start it with: mvn spring-boot:run")
        return 1
    except Exception as e:
        print(f"\n   ❌ ERROR: {type(e).__name__}: {e}")
        import traceback
        traceback.print_exc()
        return 1
    
    return 0


if __name__ == "__main__":
    exit(main())

