#!/bin/bash
# BACnet Emulator Demo Script (Shell version)
# Alternative to Python script for quick setup

set -e

HOST="${1:-localhost}"
PORT="${2:-8080}"
BASE_URL="http://${HOST}:${PORT}/api"

echo "=================================="
echo "BACnet Emulator Demo Script"
echo "=================================="
echo "Host: ${HOST}:${PORT}"
echo ""

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if curl is available
if ! command -v curl &> /dev/null; then
    echo "Error: curl is required but not installed."
    exit 1
fi

# Check if jq is available (optional, for pretty output)
JQ_AVAILABLE=false
if command -v jq &> /dev/null; then
    JQ_AVAILABLE=true
fi

# Test connection
echo -e "${BLUE}Testing connection...${NC}"
if curl -s -f "${BASE_URL}/stats" > /dev/null; then
    echo -e "${GREEN}✓ Connected!${NC}"
else
    echo -e "${YELLOW}✗ Could not connect. Make sure emulator is running.${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}[Step 1] Creating HVAC Controller Device${NC}"
DEVICE_RESPONSE=$(curl -s -X POST "${BASE_URL}/devices" \
    -H "Content-Type: application/json" \
    -d '{
        "deviceInstanceId": 1000,
        "deviceName": "HVAC Controller",
        "vendorId": "12345",
        "modelName": "HVAC-2024",
        "enabled": true
    }')

if [ "$JQ_AVAILABLE" = true ]; then
    DEVICE_ID=$(echo "$DEVICE_RESPONSE" | jq -r '.id')
    DEVICE_NAME=$(echo "$DEVICE_RESPONSE" | jq -r '.deviceName')
    echo -e "${GREEN}✓ Created device: ${DEVICE_NAME} (ID: ${DEVICE_ID})${NC}"
else
    DEVICE_ID=$(echo "$DEVICE_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
    echo -e "${GREEN}✓ Device created (ID: ${DEVICE_ID})${NC}"
fi

sleep 2

echo ""
echo -e "${BLUE}[Step 2] Creating Temperature Sensor${NC}"
TEMP_RESPONSE=$(curl -s -X POST "${BASE_URL}/objects" \
    -H "Content-Type: application/json" \
    -d "{
        \"deviceId\": ${DEVICE_ID},
        \"objectType\": 0,
        \"objectInstance\": 1,
        \"objectName\": \"Room Temperature\",
        \"presentValue\": \"72.5\",
        \"units\": \"degreesFahrenheit\",
        \"covEnabled\": true
    }")

if [ "$JQ_AVAILABLE" = true ]; then
    TEMP_ID=$(echo "$TEMP_RESPONSE" | jq -r '.id')
    TEMP_NAME=$(echo "$TEMP_RESPONSE" | jq -r '.objectName')
    TEMP_VALUE=$(echo "$TEMP_RESPONSE" | jq -r '.presentValue')
    echo -e "${GREEN}✓ Created: ${TEMP_NAME} = ${TEMP_VALUE}°F${NC}"
else
    TEMP_ID=$(echo "$TEMP_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
    echo -e "${GREEN}✓ Temperature sensor created${NC}"
fi

sleep 2

echo ""
echo -e "${BLUE}[Step 3] Creating Humidity Sensor${NC}"
HUMIDITY_RESPONSE=$(curl -s -X POST "${BASE_URL}/objects" \
    -H "Content-Type: application/json" \
    -d "{
        \"deviceId\": ${DEVICE_ID},
        \"objectType\": 0,
        \"objectInstance\": 2,
        \"objectName\": \"Room Humidity\",
        \"presentValue\": \"45.0\",
        \"units\": \"percent\",
        \"covEnabled\": true
    }")

if [ "$JQ_AVAILABLE" = true ]; then
    HUMIDITY_NAME=$(echo "$HUMIDITY_RESPONSE" | jq -r '.objectName')
    HUMIDITY_VALUE=$(echo "$HUMIDITY_RESPONSE" | jq -r '.presentValue')
    echo -e "${GREEN}✓ Created: ${HUMIDITY_NAME} = ${HUMIDITY_VALUE}%${NC}"
else
    echo -e "${GREEN}✓ Humidity sensor created${NC}"
fi

sleep 2

echo ""
echo -e "${BLUE}[Step 4] Simulating Temperature Changes${NC}"
for value in 73.2 74.1 72.8 73.5; do
    curl -s -X PUT "${BASE_URL}/objects/${TEMP_ID}/value" \
        -H "Content-Type: application/json" \
        -d "{\"presentValue\": \"${value}\"}" > /dev/null
    echo -e "  📊 Temperature: ${value}°F"
    sleep 1
done

echo ""
echo -e "${BLUE}[Step 5] Creating Cooling Setpoint${NC}"
SETPOINT_RESPONSE=$(curl -s -X POST "${BASE_URL}/objects" \
    -H "Content-Type: application/json" \
    -d "{
        \"deviceId\": ${DEVICE_ID},
        \"objectType\": 1,
        \"objectInstance\": 1,
        \"objectName\": \"Cooling Setpoint\",
        \"presentValue\": \"75.0\",
        \"units\": \"degreesFahrenheit\",
        \"writable\": true,
        \"covEnabled\": true
    }")

if [ "$JQ_AVAILABLE" = true ]; then
    SETPOINT_ID=$(echo "$SETPOINT_RESPONSE" | jq -r '.id')
    SETPOINT_NAME=$(echo "$SETPOINT_RESPONSE" | jq -r '.objectName')
    echo -e "${GREEN}✓ Created: ${SETPOINT_NAME} (Writable)${NC}"
    
    echo ""
    echo -e "${BLUE}[Step 6] Updating Setpoint${NC}"
    curl -s -X PUT "${BASE_URL}/objects/${SETPOINT_ID}/value" \
        -H "Content-Type: application/json" \
        -d '{"presentValue": "74.0"}' > /dev/null
    echo -e "${GREEN}✓ Setpoint updated to 74.0°F${NC}"
else
    echo -e "${GREEN}✓ Setpoint created${NC}"
fi

echo ""
echo "=================================="
echo -e "${GREEN}Demo Complete!${NC}"
echo "=================================="
echo ""
echo "📋 View logs at: http://${HOST}:${PORT}/monitor/logs"
echo "📊 Dashboard: http://${HOST}:${PORT}/"
echo ""

