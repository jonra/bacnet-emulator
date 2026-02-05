#!/bin/bash
# Script to create GitHub issues from security vulnerability reports
# Requires: GitHub CLI (gh) to be installed and authenticated

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ISSUES_DIR="$SCRIPT_DIR"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Creating GitHub Issues from Security Reports${NC}"
echo "=============================================="
echo ""

# Check if gh is installed
if ! command -v gh &> /dev/null; then
    echo -e "${RED}ERROR: GitHub CLI (gh) is not installed${NC}"
    echo "Install it from: https://cli.github.com/"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo -e "${RED}ERROR: Not authenticated with GitHub CLI${NC}"
    echo "Run: gh auth login"
    exit 1
fi

echo -e "${GREEN}Found GitHub CLI and authenticated${NC}"
echo ""

# Function to extract title from markdown file
get_title() {
    local file=$1
    # Extract the first line and remove the leading "# "
    head -n 1 "$file" | sed 's/^# //'
}

# Function to extract labels from markdown file
get_labels() {
    local file=$1
    # Extract labels from the second line
    grep "^\*\*Labels\*\*:" "$file" | sed 's/^**Labels**: //' | sed 's/`//g' | tr ',' '\n' | xargs
}

# Function to extract body (everything after labels line)
get_body() {
    local file=$1
    # Skip first two lines (title and labels), get the rest
    tail -n +4 "$file"
}

# Process each issue file
issue_count=0
created_count=0

for issue_file in "$ISSUES_DIR"/issue-*.md; do
    if [ ! -f "$issue_file" ]; then
        echo -e "${YELLOW}No issue files found${NC}"
        exit 0
    fi
    
    issue_count=$((issue_count + 1))
    filename=$(basename "$issue_file")
    
    echo -e "${YELLOW}Processing: $filename${NC}"
    
    # Extract issue details
    title=$(get_title "$issue_file")
    labels=$(get_labels "$issue_file")
    body=$(get_body "$issue_file")
    
    echo "  Title: $title"
    echo "  Labels: $labels"
    
    # Confirm before creating
    read -p "  Create this issue? (y/n/q to quit): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Qq]$ ]]; then
        echo -e "${YELLOW}Quitting...${NC}"
        break
    fi
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        # Create the issue
        echo "  Creating issue..."
        
        # Build label arguments
        label_args=""
        for label in $labels; do
            label_args="$label_args --label \"$label\""
        done
        
        # Create issue using gh CLI
        if issue_url=$(echo "$body" | gh issue create --title "$title" $label_args --body-file -); then
            echo -e "  ${GREEN}✓ Created: $issue_url${NC}"
            created_count=$((created_count + 1))
        else
            echo -e "  ${RED}✗ Failed to create issue${NC}"
        fi
    else
        echo -e "  ${YELLOW}Skipped${NC}"
    fi
    
    echo ""
done

echo "=============================================="
echo -e "${GREEN}Summary:${NC}"
echo "  Processed: $issue_count files"
echo "  Created: $created_count issues"
echo ""
echo -e "${GREEN}Done!${NC}"
