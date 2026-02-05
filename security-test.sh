#!/bin/bash
# Security Vulnerability Testing Script
# Tests the vulnerabilities found in the BACnet Emulator
# WARNING: Only run this on test environments, not production!

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
TARGET_HOST="${TARGET_HOST:-localhost}"
TARGET_PORT="${TARGET_PORT:-8080}"
BACNET_PORT="${BACNET_PORT:-47808}"
BASE_URL="http://${TARGET_HOST}:${TARGET_PORT}"

echo "======================================================"
echo "BACnet Emulator Security Vulnerability Testing"
echo "======================================================"
echo "Target: ${BASE_URL}"
echo "BACnet UDP Port: ${BACNET_PORT}"
echo "Date: $(date)"
echo "======================================================"
echo ""

# Test counter
TESTS_RUN=0
TESTS_FAILED=0
TESTS_PASSED=0

# Helper functions
log_test() {
    TESTS_RUN=$((TESTS_RUN + 1))
    echo -e "\n${YELLOW}[TEST $TESTS_RUN]${NC} $1"
}

log_pass() {
    TESTS_PASSED=$((TESTS_PASSED + 1))
    echo -e "${GREEN}✓ PASSED:${NC} $1"
}

log_fail() {
    TESTS_FAILED=$((TESTS_FAILED + 1))
    echo -e "${RED}✗ FAILED:${NC} $1"
}

log_vulnerable() {
    echo -e "${RED}🔓 VULNERABLE:${NC} $1"
}

log_secure() {
    echo -e "${GREEN}🔒 SECURE:${NC} $1"
}

# Test 1: No Authentication on API endpoints
log_test "Testing for missing authentication (Critical #1)"
echo "Attempting to access protected API endpoints without credentials..."

response=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/devices")
http_code=$(echo "$response" | tail -n 1)
body=$(echo "$response" | head -n -1)

if [ "$http_code" = "200" ]; then
    log_vulnerable "API endpoint /api/devices is accessible without authentication"
    log_fail "No authentication required"
    echo "Response preview: $(echo "$body" | head -c 100)..."
else
    log_secure "API endpoint requires authentication"
    log_pass "Authentication is enforced"
fi

# Test 2: H2 Console Access
log_test "Testing H2 Console exposure (Critical #2)"
echo "Attempting to access H2 database console..."

response=$(curl -s -w "\n%{http_code}" "${BASE_URL}/h2-console")
http_code=$(echo "$response" | tail -n 1)

if [ "$http_code" = "200" ]; then
    log_vulnerable "H2 Console is accessible at /h2-console"
    log_fail "Database console exposed"
    
    # Try to check if it requires authentication
    if echo "$response" | grep -q "Login"; then
        echo "  - Console is accessible but may require credentials"
    else
        echo "  - Console may be directly accessible"
    fi
else
    log_secure "H2 Console is not accessible or disabled"
    log_pass "H2 Console properly secured"
fi

# Test 3: Actuator Endpoints Exposure
log_test "Testing Spring Boot Actuator exposure (High #6)"
echo "Checking for exposed actuator endpoints..."

endpoints=("health" "info" "env" "beans" "mappings" "threaddump")
exposed_count=0

for endpoint in "${endpoints[@]}"; do
    response=$(curl -s -w "\n%{http_code}" "${BASE_URL}/actuator/${endpoint}")
    http_code=$(echo "$response" | tail -n 1)
    
    if [ "$http_code" = "200" ]; then
        echo "  - /actuator/${endpoint}: EXPOSED"
        exposed_count=$((exposed_count + 1))
    fi
done

if [ $exposed_count -gt 0 ]; then
    log_vulnerable "${exposed_count} actuator endpoints are publicly accessible"
    log_fail "Actuator endpoints should be restricted"
else
    log_secure "Actuator endpoints are properly secured"
    log_pass "No sensitive actuator endpoints exposed"
fi

# Test 4: CSRF Protection
log_test "Testing for CSRF protection (High #7)"
echo "Attempting state-changing operation without CSRF token..."

response=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d '{"deviceInstanceId":9999,"deviceName":"CSRF Test","vendorId":1,"modelName":"Test","enabled":true}' \
    "${BASE_URL}/api/devices")
http_code=$(echo "$response" | tail -n 1)

if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
    log_vulnerable "POST request succeeded without CSRF token"
    log_fail "CSRF protection is not enabled"
    
    # Cleanup: try to delete the test device
    curl -s -X DELETE "${BASE_URL}/api/devices/9999" >/dev/null 2>&1 || true
else
    log_secure "CSRF protection may be enabled"
    log_pass "POST request was rejected"
fi

# Test 5: Input Validation
log_test "Testing input validation (Medium #9)"
echo "Sending malformed data to test input validation..."

# Test with negative device instance ID
response=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d '{"deviceInstanceId":-1,"deviceName":"Test","vendorId":1,"modelName":"Test","enabled":true}' \
    "${BASE_URL}/api/devices")
http_code=$(echo "$response" | tail -n 1)

if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
    log_vulnerable "Negative device instance ID was accepted"
    log_fail "Input validation is insufficient"
    
    # Cleanup
    curl -s -X DELETE "${BASE_URL}/api/devices/-1" >/dev/null 2>&1 || true
else
    log_secure "Invalid input was rejected"
    log_pass "Input validation is working"
fi

# Test 6: Extremely long input (potential DoS)
log_test "Testing for DoS via long input (Medium #9)"
echo "Sending extremely long device name..."

long_name=$(python3 -c "print('A' * 10000)")
response=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "{\"deviceInstanceId\":9998,\"deviceName\":\"${long_name}\",\"vendorId\":1,\"modelName\":\"Test\",\"enabled\":true}" \
    "${BASE_URL}/api/devices" 2>&1)
http_code=$(echo "$response" | tail -n 1)

if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
    log_vulnerable "Extremely long input was accepted"
    log_fail "No size limits on input fields"
    
    # Cleanup
    curl -s -X DELETE "${BASE_URL}/api/devices/9998" >/dev/null 2>&1 || true
elif [ "$http_code" = "400" ] || [ "$http_code" = "413" ]; then
    log_secure "Long input was rejected"
    log_pass "Size limits are enforced"
else
    echo "  - Unexpected response code: $http_code"
fi

# Test 7: Information Disclosure via Error Messages
log_test "Testing information disclosure in error messages (High #5)"
echo "Requesting non-existent resource to check error message..."

response=$(curl -s "${BASE_URL}/api/devices/999999")

if echo "$response" | grep -q "999999"; then
    log_vulnerable "Error message includes user input (potential info disclosure)"
    log_fail "Error messages should be sanitized"
    echo "  - Error response: $(echo "$response" | head -c 200)..."
else
    log_secure "Error messages are properly sanitized"
    log_pass "No sensitive information in errors"
fi

# Test 8: UDP Socket Security (requires netcat or hping3)
log_test "Testing BACnet UDP socket (Medium #8)"

if command -v nc >/dev/null 2>&1; then
    echo "Sending test packet to BACnet UDP port..."
    
    # Send a simple packet to check if port is open
    timeout 2 bash -c "echo 'test' | nc -u ${TARGET_HOST} ${BACNET_PORT}" 2>&1 >/dev/null
    result=$?
    
    if [ $result -eq 0 ] || [ $result -eq 124 ]; then
        log_vulnerable "BACnet UDP port ${BACNET_PORT} is accepting packets"
        echo "  - Port is open and accepting connections from any source"
        log_fail "No IP filtering on UDP socket"
    else
        log_secure "BACnet UDP port is not accessible or filtered"
        log_pass "UDP socket properly secured"
    fi
else
    echo "  - Skipping: netcat (nc) not available"
fi

# Test 9: Rate Limiting
log_test "Testing for API rate limiting (Medium #13)"
echo "Sending multiple rapid requests..."

start_time=$(date +%s)
success_count=0

for i in {1..20}; do
    response=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/devices" -o /dev/null)
    http_code=$(echo "$response" | tail -n 1)
    
    if [ "$http_code" = "200" ]; then
        success_count=$((success_count + 1))
    fi
done

end_time=$(date +%s)
duration=$((end_time - start_time))

if [ $success_count -eq 20 ]; then
    log_vulnerable "All 20 requests succeeded in ${duration}s"
    log_fail "No rate limiting detected"
    echo "  - Rate: $(echo "scale=2; 20 / $duration" | bc) requests/second"
else
    log_secure "Some requests were rate limited"
    log_pass "Rate limiting is working (${success_count}/20 succeeded)"
fi

# Test 10: Unauthorized Device Deletion
log_test "Testing unauthorized resource deletion (Critical #1)"
echo "Attempting to delete devices without authorization..."

# First, create a test device
create_response=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d '{"deviceInstanceId":7777,"deviceName":"Delete Test","vendorId":1,"modelName":"Test","enabled":true}' \
    "${BASE_URL}/api/devices")

if echo "$create_response" | grep -q '"id"'; then
    device_id=$(echo "$create_response" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
    
    # Try to delete it without auth
    delete_response=$(curl -s -w "\n%{http_code}" -X DELETE "${BASE_URL}/api/devices/${device_id}")
    http_code=$(echo "$delete_response" | tail -n 1)
    
    if [ "$http_code" = "200" ] || [ "$http_code" = "204" ]; then
        log_vulnerable "Device was deleted without authentication"
        log_fail "Deletion should require authentication"
    else
        log_secure "Delete operation was blocked"
        log_pass "Authorization is enforced"
        
        # Cleanup
        curl -s -X DELETE "${BASE_URL}/api/devices/${device_id}" >/dev/null 2>&1 || true
    fi
else
    echo "  - Could not create test device for deletion test"
fi

# Summary
echo ""
echo "======================================================"
echo "TEST SUMMARY"
echo "======================================================"
echo "Total Tests Run: ${TESTS_RUN}"
echo -e "${GREEN}Passed: ${TESTS_PASSED}${NC}"
echo -e "${RED}Failed: ${TESTS_FAILED}${NC}"
echo "======================================================"

# Calculate vulnerability score
if [ $TESTS_FAILED -gt 7 ]; then
    echo -e "${RED}SECURITY RATING: CRITICAL - Immediate action required${NC}"
elif [ $TESTS_FAILED -gt 4 ]; then
    echo -e "${YELLOW}SECURITY RATING: HIGH - Major vulnerabilities found${NC}"
elif [ $TESTS_FAILED -gt 2 ]; then
    echo -e "${YELLOW}SECURITY RATING: MEDIUM - Some security improvements needed${NC}"
else
    echo -e "${GREEN}SECURITY RATING: LOW - Generally secure${NC}"
fi

echo ""
echo "For detailed remediation steps, see SECURITY_ANALYSIS_REPORT.md"
echo ""

# Exit with appropriate code
if [ $TESTS_FAILED -gt 0 ]; then
    exit 1
else
    exit 0
fi
