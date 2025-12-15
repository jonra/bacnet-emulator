#!/bin/bash
# BACnet Emulator Demo Setup Script
# Automatically sets up Python virtual environment and installs dependencies

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

VENV_DIR="venv"
PYTHON_CMD="python3"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}BACnet Emulator Demo Setup${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if Python 3 is available
if ! command -v $PYTHON_CMD &> /dev/null; then
    echo -e "${RED}❌ Error: python3 is not installed${NC}"
    echo "   Please install Python 3.7 or higher"
    exit 1
fi

PYTHON_VERSION=$($PYTHON_CMD --version 2>&1 | awk '{print $2}')
echo -e "${GREEN}✓ Found Python: $PYTHON_VERSION${NC}"

# Check if virtual environment exists
if [ ! -d "$VENV_DIR" ]; then
    echo ""
    echo -e "${BLUE}Creating virtual environment...${NC}"
    $PYTHON_CMD -m venv $VENV_DIR
    echo -e "${GREEN}✓ Virtual environment created${NC}"
else
    echo -e "${GREEN}✓ Virtual environment already exists${NC}"
fi

# Activate virtual environment
echo ""
echo -e "${BLUE}Activating virtual environment...${NC}"
source $VENV_DIR/bin/activate

# Upgrade pip
echo ""
echo -e "${BLUE}Upgrading pip...${NC}"
pip install --quiet --upgrade pip
echo -e "${GREEN}✓ pip upgraded${NC}"

# Install dependencies
echo ""
echo -e "${BLUE}Installing dependencies...${NC}"
echo "   Installing: requests"
pip install --quiet requests

# Check if BAC0 should be installed
echo ""
read -p "Install BAC0 library for full BACnet protocol support? (y/n) [y]: " install_bac0
install_bac0=${install_bac0:-y}

if [[ $install_bac0 =~ ^[Yy]$ ]]; then
    echo -e "${BLUE}   Installing: BAC0${NC}"
    pip install --quiet BAC0 || {
        echo -e "${YELLOW}⚠️  Warning: BAC0 installation failed${NC}"
        echo "   The script will still work but without BACnet protocol interactions"
    }
    echo -e "${GREEN}✓ Dependencies installed${NC}"
else
    echo -e "${YELLOW}⚠️  Skipping BAC0 installation${NC}"
    echo -e "${GREEN}✓ Dependencies installed (requests only)${NC}"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Setup Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "To use the demo scripts:"
echo ""
echo "1. Activate the virtual environment:"
echo -e "   ${BLUE}source venv/bin/activate${NC}"
echo ""
echo "2. Run the demo script:"
echo -e "   ${BLUE}python3 demo-script-bacnet.py${NC}"
echo "   or"
echo -e "   ${BLUE}python3 demo-script.py${NC}"
echo ""
echo "To deactivate the virtual environment:"
echo -e "   ${BLUE}deactivate${NC}"
echo ""

# Ask if user wants to run the demo now
read -p "Run the demo script now? (y/n) [n]: " run_now
run_now=${run_now:-n}

if [[ $run_now =~ ^[Yy]$ ]]; then
    echo ""
    echo -e "${BLUE}Running demo script...${NC}"
    echo ""
    
    if [ -f "demo-script-bacnet.py" ]; then
        python3 demo-script-bacnet.py
    elif [ -f "demo-script.py" ]; then
        python3 demo-script.py
    else
        echo -e "${RED}❌ Error: Demo script not found${NC}"
        exit 1
    fi
else
    echo ""
    echo -e "${YELLOW}Remember to activate the virtual environment before running scripts:${NC}"
    echo -e "   ${BLUE}source venv/bin/activate${NC}"
fi

