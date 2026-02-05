#!/bin/bash
# Example script showing how to trigger the security issue creation workflow
# This script demonstrates the different ways to use the workflow

set -e

echo "Security Issue Creation Workflow - Usage Examples"
echo "=================================================="
echo ""

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo "⚠️  GitHub CLI (gh) is not installed"
    echo "Install it from: https://cli.github.com/"
    echo ""
    echo "However, you can still:"
    echo "  1. Go to GitHub Actions tab in your repository"
    echo "  2. Select 'Create Security Issues' workflow"
    echo "  3. Click 'Run workflow'"
    exit 0
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo "⚠️  Not authenticated with GitHub"
    echo "Run: gh auth login"
    exit 1
fi

echo "GitHub CLI is installed and authenticated ✅"
echo ""
echo "Choose an option:"
echo "  1) Dry run (preview issues without creating)"
echo "  2) Create all issues (production mode)"
echo "  3) Create issues without creating labels"
echo "  4) Show workflow status"
echo "  5) Exit"
echo ""
read -p "Enter choice [1-5]: " choice

case $choice in
    1)
        echo ""
        echo "Running in DRY RUN mode..."
        echo "This will show what issues would be created without actually creating them."
        echo ""
        gh workflow run create-security-issues.yml -f dry_run=true
        echo ""
        echo "✅ Workflow triggered!"
        echo "View progress: gh run watch"
        ;;
    2)
        echo ""
        echo "⚠️  WARNING: This will create actual GitHub issues!"
        read -p "Are you sure? (yes/no): " confirm
        if [ "$confirm" = "yes" ]; then
            echo ""
            echo "Creating security issues..."
            gh workflow run create-security-issues.yml
            echo ""
            echo "✅ Workflow triggered!"
            echo "View progress: gh run watch"
        else
            echo "Cancelled."
        fi
        ;;
    3)
        echo ""
        echo "Creating issues without creating labels..."
        echo "(Assumes labels already exist)"
        gh workflow run create-security-issues.yml -f create_labels=false
        echo ""
        echo "✅ Workflow triggered!"
        echo "View progress: gh run watch"
        ;;
    4)
        echo ""
        echo "Recent workflow runs:"
        gh run list --workflow=create-security-issues.yml --limit 5
        ;;
    5)
        echo "Exiting..."
        exit 0
        ;;
    *)
        echo "Invalid choice"
        exit 1
        ;;
esac

echo ""
echo "Useful commands:"
echo "  gh run list --workflow=create-security-issues.yml  # List recent runs"
echo "  gh run watch                                       # Watch latest run"
echo "  gh run view [run-id]                               # View specific run"
echo "  gh issue list --label security                     # List created issues"
