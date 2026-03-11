# CodeQL vs Semgrep: Detailed Comparison

## Side-by-Side Analysis

| Aspect | CodeQL | Semgrep (Custom Rules) |
|--------|--------|------------------------|
| **Total Findings** | 0 | 25 |
| **Analysis Approach** | Data flow + taint tracking | Pattern matching on AST |
| **Detection Speed** | Slower (deep analysis) | Faster (syntax patterns) |
| **False Positive Rate** | Low | Medium |
| **Custom Rules** | QL language (complex) | YAML patterns (simple) |

---

## What Each Tool Excels At

### CodeQL Strengths 💪

#### 1. Data Flow Analysis
Tracks tainted data from source to sink across method boundaries:
```java
String userInput = request.getParameter("id");  // Source
String query = "SELECT * FROM users WHERE id = " + userInput;  // Flow
stmt.executeQuery(query);  // Sink → SQL Injection detected
```

**Why it's powerful:** Understands semantics, not just syntax

#### 2. Cross-File Analysis
Follows data through multiple files:
```java
// File 1: UserController.java
String data = request.getParam("data");
return service.processData(data);

// File 2: DataService.java  
void processData(String input) {
    Runtime.getRuntime().exec(input);  // Command injection detected
}
```

#### 3. Known CVE Patterns
Has extensive database of known vulnerability patterns:
- Deserialization of untrusted data
- XXE (XML External Entity) injection
- SSRF (Server-Side Request Forgery)
- Path traversal
- Reflected XSS

#### 4. Language-Specific Security Models
Deep understanding of framework semantics:
- Spring Security configurations
- JPA query construction
- Servlet request handling
- JAXB XML parsing

### Semgrep Strengths 💪

#### 1. Quick Custom Rules
Write rules in minutes vs hours:
```yaml
- id: unsafe-array-access
  pattern: $ARR[$IDX]
  message: "Array access without bounds check"
```

#### 2. Protocol-Specific Patterns
Can detect domain-specific issues:
```yaml
- id: bacnet-byte-parsing
  patterns:
    - pattern-inside: class BacnetService { ... }
    - pattern: $DATA[$OFFSET] & 0xFF
  message: "Unsafe BACnet parsing"
```

#### 3. Framework Convention Checks
Enforces best practices:
```yaml
- id: missing-validation
  pattern: |
    @RestController
    class $C {
      public $R $F(@RequestBody $T $V) { ... }
    }
  pattern-not: |
    public $R $F(@Valid @RequestBody $T $V) { ... }
```

#### 4. Fast Iteration
Can scan codebase in seconds, ideal for CI/CD

---

## Why CodeQL Found 0 Issues in This Project

### 1. No Traditional Web Vulnerabilities

**CodeQL looks for:**
- ❌ SQL injection → None (JPA used correctly)
- ❌ XSS → None (Thymeleaf escapes by default)
- ❌ Path traversal → None (no file operations)
- ❌ Command injection → None (no Runtime.exec())

**This project has:**
- ✅ JPA repositories with method queries (safe)
- ✅ Thymeleaf templates (auto-escaping)
- ✅ No file upload/download features
- ✅ No system command execution

### 2. Adequate Basic Bounds Checking

**What CodeQL saw:**
```java
// BacnetService.java:141
if (data.length < 4) {
    return;  // ✅ CodeQL: "Basic check present, looks safe"
}
int type = data[0] & 0xFF;
```

**What it missed:**
- The check is at the START of the method
- Subsequent parsing at lines 161, 169, 175, etc. rely on this check
- OFF-BY-ONE errors in extraction methods (< vs <=)
- TOCTOU issues (Time Of Check, Time Of Use)

### 3. Exception Handling Mask Issues

**What CodeQL saw:**
```java
try {
    // Parsing code with potential issues
    int value = data[offset] & 0xFF;
} catch (Exception e) {
    log.error("Error", e);  // ✅ CodeQL: "Exceptions caught, safe"
}
```

**What it missed:**
- Catching `Exception` hides ArrayIndexOutOfBoundsException
- Application crashes, but CodeQL considers it "handled"
- For a dev tool, crashes are a reliability issue, not just a handled exception

### 4. Java Memory Safety

**What CodeQL knows:**
- Java has no pointer arithmetic
- Array access throws exception, doesn't corrupt memory
- No traditional buffer overflow exploitation possible
- No memory disclosure through pointer manipulation

**Why issues still matter:**
- DoS via repeated crashes
- Stability/reliability of dev tool
- Test interruption and confusion

### 5. No Protocol-Specific Rules

**CodeQL standard packs include:**
- Web application security (OWASP Top 10)
- Common CVE patterns
- Language-specific best practices

**CodeQL standard packs DO NOT include:**
- BACnet protocol parsing rules
- Industrial protocol security patterns
- IoT/embedded protocol validation
- Custom binary protocol parsing

---

## What Semgrep Found That CodeQL Missed

### Finding Category 1: Protocol Parsing (16 findings)

**Why Semgrep Found It:**
```yaml
- pattern-inside: class BacnetService { ... }
- pattern: $DATA[$OFFSET] & 0xFF
- pattern-not-inside: if ($OFFSET < $DATA.length) { ... }
```

**Why CodeQL Missed It:**
1. No BACnet-specific rules in standard library
2. Basic bounds checks at method entry satisfied requirements
3. Exception handling made it appear robust
4. Focus on taint tracking, not local bounds checking

**Example:**
```java
// Line 586-589
if (offset + 4 < data.length) {  // BUG: should be <=
    return ((data[offset] & 0xFF) << 24) |
           ((data[offset + 1] & 0xFF) << 16) |  
           ((data[offset + 2] & 0xFF) << 8) |
           (data[offset + 3] & 0xFF);  // Can be out of bounds!
}
```

### Finding Category 2: Framework Conventions (2 findings)

**Why Semgrep Found It:**
```yaml
- pattern: public $R $F(@RequestBody $T $V) { ... }
- pattern-not: public $R $F(@Valid @RequestBody $T $V) { ... }
```

**Why CodeQL Missed It:**
1. Not a direct security vulnerability
2. Spring still does basic type validation
3. No tainted data flow to dangerous sink
4. Convention/best practice, not vulnerability

**Example:**
```java
@PutMapping("/config/network")
public ResponseEntity<NetworkConfigDto> updateNetworkConfig(
    @RequestBody NetworkConfigDto configDto) {  // Missing @Valid
```

### Finding Category 3: Resource Management (1 finding)

**Why Semgrep Found It:**
```yaml
- pattern: Executors.newFixedThreadPool($N)
- message: "Thread pool without resource limits"
```

**Why CodeQL Missed It:**
1. Resource exhaustion requires runtime analysis
2. Configuration is context-dependent
3. Fixed thread pool IS bounded (better than cached)
4. No code injection or data flow issue

**Example:**
```java
executorService = Executors.newFixedThreadPool(10);
// Semgrep: "Could be exhausted"
// CodeQL: "No security issue detected"
```

---

## When to Use Each Tool

### Use CodeQL When:
- ✅ Looking for traditional web vulnerabilities (OWASP Top 10)
- ✅ Need cross-file/cross-module data flow analysis
- ✅ Working with well-known frameworks (Spring, Struts, etc.)
- ✅ Want low false-positive rate
- ✅ Investigating known CVE patterns
- ✅ Production security assessment

### Use Semgrep When:
- ✅ Need quick custom rules for specific patterns
- ✅ Enforcing coding standards and conventions
- ✅ Domain-specific vulnerability checks (protocols, embedded)
- ✅ Fast feedback in CI/CD pipeline
- ✅ Complementing CodeQL with additional patterns
- ✅ Prototype/dev tool security checks

### Use Both When:
- ✅ Comprehensive security coverage needed
- ✅ Multiple threat models (web + protocol)
- ✅ Custom application with standard frameworks
- ✅ **This project** - combines web app + custom protocol

---

## Lesson: Different Tools, Different Perspectives

| Question | CodeQL Answer | Semgrep Answer | Truth |
|----------|---------------|----------------|-------|
| Is there SQL injection? | ✅ No | ✅ No | ✅ Correct |
| Is there XSS? | ✅ No | ✅ No | ✅ Correct |
| Are array accesses safe? | ✅ Yes (basic checks) | ❌ No (off-by-one) | ⚠️ Partially |
| Is input validated? | ⚠️ Unclear | ❌ Missing @Valid | ⚠️ Basic only |
| Can it crash? | ⚠️ Exceptions caught | ✅ Yes | ✅ Semgrep correct |

---

## Recommendations

### For This Project:
1. **Keep both CodeQL and Semgrep** in CI/CD
2. **CodeQL** catches traditional web vulnerabilities
3. **Semgrep** catches BACnet protocol issues
4. **Review all findings** - neither tool is 100% accurate

### For Similar Projects:
1. **Custom protocols** → Need Semgrep rules
2. **Standard web apps** → CodeQL sufficient
3. **Framework-heavy** → CodeQL excels
4. **Fast iteration** → Semgrep in pre-commit hooks

### General Best Practices:
1. Use multiple tools for comprehensive coverage
2. Write custom rules for domain-specific issues
3. Don't rely solely on automated scanning
4. Manual code review still essential
5. Understand tool limitations

---

## Conclusion

**CodeQL found 0 issues** ✅ for what it looks for (web vulnerabilities)  
**Semgrep found 25 issues** ✅ for what we told it to look for (protocol parsing)

Both tools are correct within their scope. The lesson:
> **Security tools complement, not replace, each other.**

The real vulnerability wasn't that CodeQL "missed" something - it's that **no single tool catches everything**. Comprehensive security requires:
- Multiple automated tools (CodeQL + Semgrep)
- Custom rules for domain-specific issues  
- Manual code review
- Threat modeling
- Understanding tool limitations

---

**Report Date:** February 5, 2026  
**Tools Compared:** CodeQL (GitHub native) vs Semgrep 1.151.0  
**Analysis Type:** Comparative security tool evaluation
