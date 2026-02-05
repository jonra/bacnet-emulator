# Security Findings Visualization

## Finding Distribution

```
ERROR (High Severity)     ████████████████ 16 findings
WARNING (Medium Severity) ███████          7 findings  
INFO (Low Severity)       ██               2 findings
```

## Findings by Component

```
BacnetService.java (Protocol Handler)
├── Unsafe byte parsing         ████████████████ 16 ERROR
├── Array bounds checking       ████             4 WARNING
├── Thread pool config          █                1 WARNING
├── Fixed buffer size           █                1 INFO
└── Number parsing              █                1 INFO

ApiController.java (REST API)
└── Missing validation          ██               2 WARNING
```

## Critical Path Analysis

### Most Critical: UDP Packet Reception → Parsing Pipeline

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. UDP Socket Receives Packet                                  │
│    DatagramSocket.receive() → byte[] buffer (1476 bytes)       │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. handlePacket()                                               │
│    ⚠️  Line 146: type = data[0] & 0xFF                          │
│    Check: data.length < 4 (BASIC)                              │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. parseAndHandleBacnetMessage()                                │
│    ⚠️  Line 161: npduLength = ((data[2] & 0xFF) << 8) | ...     │
│    Check: data.length < 4 (INSUFFICIENT)                       │
│    ❌ No validation that npduLength is reasonable!              │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. Arrays.copyOfRange(data, 4, 4 + npduLength)                 │
│    ❌ CRASH RISK: If npduLength > data.length - 4               │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. handleReadProperty() / handleWriteProperty()                │
│    ⚠️  Lines 254-257: Extract data at hardcoded offsets         │
│    Calls: extractDeviceInstance(npdu, 5)                       │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. extractDeviceInstance()                                      │
│    ⚠️  Line 585: if (offset + 4 < data.length)                  │
│    BUG: Should be <= not <                                     │
│    Can access data[offset+3] when data.length = offset+4      │
└─────────────────────────────────────────────────────────────────┘
```

## Attack Vectors for Local Dev Tool

### 1. Malformed Client Packets (Accidental)
```
Risk: HIGH
Scenario: Client app sends truncated/malformed packet
Result: Emulator crashes, testing interrupted
Likelihood: MEDIUM (during development, bugs happen)
```

### 2. Fuzzing During Testing
```
Risk: MEDIUM  
Scenario: Tester uses fuzzer to validate client robustness
Result: Emulator crashes repeatedly, can't complete tests
Likelihood: LOW (depends on testing methodology)
```

### 3. Malicious Network Traffic (Unlikely for Local Tool)
```
Risk: LOW
Scenario: Attacker sends crafted packets to local emulator
Result: DoS, potential information disclosure
Likelihood: VERY LOW (local dev environment)
```

## Code Quality Heat Map

```
BacnetService.java:

Lines 1-100:    ✅ Configuration and initialization
Lines 101-200:  🔴 CRITICAL - Packet parsing (16 issues)
Lines 201-400:  🟡 WARNING - Protocol handlers (validated but fragile)
Lines 401-600:  ✅ Response building (safe)
Lines 601-700:  🔴 HIGH - Data extraction methods (4 issues)
```

## Comparison: CodeQL vs Semgrep

```
                    CodeQL      Semgrep
SQL Injection       ✅ Yes      ✅ Yes (none found)
XSS                 ✅ Yes      ✅ Yes (none found)
Path Traversal      ✅ Yes      ❌ No
Command Injection   ✅ Yes      ❌ No
Protocol Parsing    ❌ No       ✅ Yes (16 found)
Buffer Bounds       ⚠️ Basic    ✅ Detailed
Input Validation    ❌ No       ✅ Yes (2 found)
Resource Limits     ❌ No       ✅ Yes (1 found)
```

## Remediation Priority Matrix

```
       │ High Impact        │ Medium Impact      │ Low Impact
───────┼────────────────────┼────────────────────┼──────────────
High   │ 🔴 Byte parsing    │                    │
Likely │    (16 issues)     │                    │
       │ FIX IMMEDIATELY    │                    │
───────┼────────────────────┼────────────────────┼──────────────
Medium │                    │ 🟡 Input valid.    │ Thread pool
Likely │                    │    (2 issues)      │    (1 issue)
       │                    │ FIX SOON           │ DOCUMENT
───────┼────────────────────┼────────────────────┼──────────────
Low    │                    │                    │ 🟢 Buffer size
Likely │                    │                    │ 🟢 Num parsing
       │                    │                    │ INFORMATIONAL
```

## Quick Fix Checklist

### Immediate (< 4 hours)
- [ ] Fix `extractDeviceInstance()` bounds check (< → <=)
- [ ] Add validation: `if (npduLength > data.length - 4) return;`
- [ ] Fix `extractObjectType()`, `extractPropertyId()`, `extractValue()`
- [ ] Add unit test with malformed packets

### Short Term (< 1 day)  
- [ ] Add validation annotations to ConfigDto classes
- [ ] Add @Valid to controller methods
- [ ] Add comprehensive packet validation helper method
- [ ] Document thread pool sizing rationale

### Long Term (Future)
- [ ] Consider BACnet library instead of manual parsing
- [ ] Add packet fuzzing to CI/CD
- [ ] Add monitoring and metrics for production use

## References

- **Full Report:** [SECURITY_ANALYSIS_REPORT.md](./SECURITY_ANALYSIS_REPORT.md)
- **Quick Summary:** [SECURITY_ANALYSIS_SUMMARY.md](./SECURITY_ANALYSIS_SUMMARY.md)
- **Tracking CSV:** [SECURITY_FINDINGS.csv](./SECURITY_FINDINGS.csv)
- **Semgrep Rules:** `/tmp/semgrep-rules/bacnet-security.yaml` (custom rules)
