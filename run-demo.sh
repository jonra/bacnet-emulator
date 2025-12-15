#!/bin/bash
# Quick script to activate venv and run demo
# Usage: ./run-demo.sh [basic|full|protocol]

set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

VENV_DIR="venv"
SCRIPT_TYPE="${1:-protocol}"

# Check if virtual environment exists
if [ ! -d "$VENV_DIR" ]; then
    echo -e "${RED}❌ Virtual environment not found!${NC}"
    echo ""
    echo "Run setup first:"
    echo -e "   ${BLUE}./setup-demo.sh${NC}"
    exit 1
fi

# Activate virtual environment
echo -e "${BLUE}Activating virtual environment...${NC}"
source $VENV_DIR/bin/activate

# Determine which script to run
case $SCRIPT_TYPE in
    basic)
        SCRIPT="demo-script.py"
        ARGS="--scenario basic"
        ;;
    full)
        SCRIPT="demo-script.py"
        ARGS="--scenario full"
        ;;
    protocol|bacnet)
        SCRIPT="demo-script-bacnet.py"
        ARGS=""
        ;;
    *)
        echo -e "${YELLOW}Unknown script type: $SCRIPT_TYPE${NC}"
        echo "Usage: ./run-demo.sh [basic|full|protocol]"
        echo ""
        echo "  basic     - Run basic scenario (REST API only)"
        echo "  full      - Run full scenario (REST API only)"
        echo "  protocol  - Run full demo with BACnet protocol (default)"
        exit 1
        ;;
esac

# Check if script exists
if [ ! -f "$SCRIPT" ]; then
    echo -e "${RED}❌ Script not found: $SCRIPT${NC}"
    exit 1
fi

# Run the script
echo -e "${GREEN}Running: $SCRIPT $ARGS${NC}"
echo ""
python3 $SCRIPT $ARGS

# Note: Virtual environment stays activated in this shell session
echo ""
echo -e "${YELLOW}Note: Virtual environment is still active in this shell${NC}"
echo "To deactivate: ${BLUE}deactivate${NC}"

