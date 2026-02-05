# Security Issue Creation Workflow

This directory contains the GitHub Actions workflow and data for automatically creating security vulnerability issues.

## Files

- **`workflows/create-security-issues.yml`**: GitHub Actions workflow that creates security issues
- **`security-vulnerabilities.json`**: Structured data file containing vulnerability information

## Usage

### Option 1: Manual Trigger via GitHub UI

1. Go to the **Actions** tab in your GitHub repository
2. Select the **"Create Security Issues"** workflow from the left sidebar
3. Click the **"Run workflow"** dropdown button
4. Configure options:
   - **create_labels**: Create security labels before creating issues (default: true)
   - **dry_run**: Preview what would be created without actually creating issues (default: false)
5. Click **"Run workflow"**

### Option 2: Using GitHub CLI

```bash
# Run in production mode (creates issues)
gh workflow run create-security-issues.yml

# Run in dry-run mode (preview only)
gh workflow run create-security-issues.yml -f dry_run=true

# Skip label creation
gh workflow run create-security-issues.yml -f create_labels=false
```

### Option 3: Trigger via API

```bash
curl -X POST \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer YOUR_GITHUB_TOKEN" \
  https://api.github.com/repos/jonra/bacnet-emulator/actions/workflows/create-security-issues.yml/dispatches \
  -d '{"ref":"main","inputs":{"dry_run":"false","create_labels":"true"}}'
```

## What Gets Created

### Labels (8 total)

- `security` - Security vulnerability or security-related issue
- `severity-critical` - Critical severity - immediate attention required  
- `severity-high` - High severity - should be addressed soon
- `severity-medium` - Medium severity - should be addressed in due course
- `severity-low` - Low severity - can be addressed when convenient
- `owasp-top-10` - Related to OWASP Top 10 security risks
- `owasp-injection` - OWASP A03:2021 - Injection
- `owasp-insecure-design` - OWASP A04:2021 - Insecure Design

### Issues (6 total)

1. **[CRITICAL]** Buffer Overflow in BACnet Packet Parsing - NPDU Extraction
2. **[HIGH]** Array Index Out of Bounds in extractDeviceInstance and Related Methods
3. **[HIGH]** Integer Overflow in NPDU Length Calculation
4. **[MEDIUM]** Missing Input Validation in encodeValue Method
5. **[MEDIUM]** Race Condition in Device and Object Cache Refresh
6. **[MEDIUM]** ByteBuffer Overflow in Encoding Methods

Each issue includes:
- Detailed vulnerability description
- Code examples showing the problem
- OWASP Top 10 classification
- Specific file locations and line numbers
- Risk assessment
- Suggested fix with code
- Acceptance criteria checklist

## Workflow Features

- ✅ **Duplicate Detection**: Checks for existing issues to avoid duplicates
- ✅ **Dry Run Mode**: Preview issues before creating them
- ✅ **Label Management**: Automatically creates required labels
- ✅ **Summary Report**: Generates a summary in the workflow run
- ✅ **Error Handling**: Gracefully handles failures
- ✅ **Rate Limiting**: Includes delays to avoid GitHub API rate limits

## Permissions Required

The workflow requires:
- `issues: write` - To create issues
- `contents: read` - To read the repository files

These are automatically granted via `GITHUB_TOKEN` when the workflow runs.

## Modifying Vulnerabilities

To add, edit, or remove vulnerabilities:

1. Edit `.github/security-vulnerabilities.json`
2. Follow the existing JSON structure:

```json
{
  "vulnerabilities": [
    {
      "id": 1,
      "severity": "critical|high|medium|low",
      "title": "Issue title",
      "labels": ["label1", "label2"],
      "description": "Full issue description in Markdown"
    }
  ],
  "labels": [
    {
      "name": "label-name",
      "color": "hex-color",
      "description": "Label description"
    }
  ]
}
```

3. Validate JSON syntax (use `jq` or online validator)
4. Commit and push changes
5. Run the workflow again

## Testing

### Test in Dry Run Mode

Always test changes in dry-run mode first:

```bash
gh workflow run create-security-issues.yml -f dry_run=true
```

This will:
- Validate the JSON file
- Show what issues would be created
- Not actually create any issues

### Test Label Creation

Test label creation separately:

```bash
gh workflow run create-security-issues.yml -f create_labels=true -f dry_run=true
```

## Troubleshooting

### "security-vulnerabilities.json not found"
- Ensure the file exists at `.github/security-vulnerabilities.json`
- Check file is committed to the repository

### "Invalid JSON"
- Validate JSON syntax with `jq empty .github/security-vulnerabilities.json`
- Check for missing commas, brackets, or quotes

### "Failed to create issue"
- Check repository permissions
- Verify `GITHUB_TOKEN` has `issues: write` permission
- Check GitHub API rate limits

### Issues Already Exist
- The workflow automatically skips issues with matching titles
- To recreate, close or delete existing issues first
- Or modify titles in the JSON file

## Security Analysis Source

These vulnerabilities were identified through comprehensive security analysis of the bacnet-emulator codebase, focusing on:
- Buffer overflows and unsafe binary parsing
- Protocol parsing bugs in BACnet handling
- Input validation issues
- Memory safety and race conditions

Full analysis documents are available in `/tmp/`:
- `SECURITY_ANALYSIS_SUMMARY.md`
- `security_vulnerabilities_analysis.md`

## Contributing

To add new vulnerabilities:
1. Analyze the codebase for security issues
2. Add vulnerability to `security-vulnerabilities.json`
3. Follow OWASP Top 10 classification
4. Include concrete fix recommendations
5. Test with dry-run mode
6. Submit a pull request

## License

This workflow and vulnerability data are part of the bacnet-emulator project and follow the same license.
