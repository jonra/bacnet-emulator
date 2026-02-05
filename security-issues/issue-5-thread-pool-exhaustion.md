# [MEDIUM] Thread Pool Exhaustion Leading to Denial of Service

**Labels**: `security`, `severity-medium`, `owasp-a04-insecure-design`

## Vulnerability

The `BacnetService.java` class uses a fixed-size thread pool (10 threads) to process incoming BACnet packets without any form of backpressure or queue limiting. The `listenForPackets` method continuously submits packet handling tasks to the executor service without checking if threads are available or if the queue is full. This design allows an attacker (or even legitimate high-volume traffic) to flood the system with UDP packets, exhausting the thread pool and causing denial of service.

## OWASP Classification

**Category**: A04:2021 - Insecure Design  
**Reference**: https://owasp.org/Top10/A04_2021-Insecure_Design/

**Description**: This OWASP category addresses design flaws that enable attacks. The lack of resource limits and backpressure mechanisms represents an insecure design pattern where the system does not protect itself from resource exhaustion. While not a traditional injection or authentication issue, improper resource management is a critical design flaw that can lead to denial of service.

## Location

**File**: `src/main/java/com/bacnet/emulator/service/BacnetService.java`

**Lines 73, 111, 117-131**:

```java
@PostConstruct
public void initialize() {
    try {
        executorService = Executors.newFixedThreadPool(10);  // Fixed pool of 10 threads
        startServer();
        // ...
    }
}

private void startServer() throws SocketException, UnknownHostException {
    // ...
    executorService.submit(this::listenForPackets);  // Listener thread
}

private void listenForPackets() {
    byte[] buffer = new byte[1476];
    
    while (running && socket != null && !socket.isClosed()) {
        try {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            
            // VULNERABILITY: No check for queue size or available threads
            executorService.submit(() -> handlePacket(packet));  // Unbounded submission
        } catch (IOException e) {
            if (running) {
                log.error("Error receiving BACnet packet", e);
            }
        }
    }
}
```

**Issue**: 
1. Fixed thread pool of 10 threads for all packet processing
2. No queue size limit - the unbounded queue in `Executors.newFixedThreadPool()` can grow indefinitely
3. No backpressure - packet handler continues accepting packets even when queue is full
4. No rate limiting - no protection against packet floods

## Risk Assessment

**Impact for BACnet Emulator (Local Development Tool)**:

1. **Denial of Service**: 
   - Attacker on local network can send rapid UDP packets to port 47808
   - Queue grows until memory exhaustion occurs (OutOfMemoryError)
   - Emulator becomes unresponsive or crashes

2. **Development/Testing Disruption**:
   - Legitimate high-volume testing (e.g., load testing, fuzzing) can accidentally DoS the emulator
   - Misconfigured BACnet clients sending rapid requests can overwhelm the system
   - Multiple concurrent clients testing can exhaust thread pool

3. **Resource Starvation**:
   - 10 threads shared between packet listening, cache refresh, and COV notifications
   - Cache refresh thread (line 114) competes for executor resources
   - System becomes unresponsive to legitimate requests

4. **Memory Exhaustion**:
   - Unbounded queue can grow to millions of pending tasks
   - Each pending task holds packet data (1476 bytes + overhead)
   - Eventually triggers OutOfMemoryError, crashing the application

5. **No Graceful Degradation**:
   - System doesn't drop packets when overloaded
   - No error responses to clients when system is overwhelmed
   - Silent accumulation of work until crash

**Severity**: MEDIUM - Can cause complete service failure but requires sustained packet flood. Local network limitation reduces but doesn't eliminate risk.

## Suggested Fix

Implement bounded queue with backpressure and monitoring:

### 1. Replace Fixed Thread Pool with Bounded Queue

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacnetService {
    
    // ... existing fields ...
    
    private ThreadPoolExecutor executorService;
    private final AtomicLong droppedPackets = new AtomicLong(0);
    private final AtomicLong processedPackets = new AtomicLong(0);
    
    // Configuration constants
    private static final int CORE_POOL_SIZE = 5;
    private static final int MAX_POOL_SIZE = 10;
    private static final int QUEUE_CAPACITY = 100;
    private static final long KEEP_ALIVE_TIME = 60L;
    
    @PostConstruct
    public void initialize() {
        try {
            // Create bounded thread pool with explicit queue
            ArrayBlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
            
            // Custom rejection policy: log and count dropped packets
            RejectedExecutionHandler rejectionHandler = (runnable, executor) -> {
                long dropped = droppedPackets.incrementAndGet();
                if (dropped % 100 == 0) {  // Log every 100th dropped packet
                    log.warn("Thread pool saturated. Total dropped packets: {}. " +
                             "Queue size: {}, Active threads: {}", 
                             dropped, executor.getQueue().size(), executor.getActiveCount());
                }
            };
            
            executorService = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                workQueue,
                rejectionHandler
            );
            
            startServer();
            
            // Start monitoring thread
            startMonitoring();
            
            log.info("BACnet service initialized with thread pool: core={}, max={}, queue={}",
                     CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY);
        } catch (Exception e) {
            log.error("Failed to initialize BACnet service", e);
        }
    }
    
    private void listenForPackets() {
        byte[] buffer = new byte[1476];
        
        while (running && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                processedPackets.incrementAndGet();
                
                // Try to submit packet for processing
                try {
                    executorService.execute(() -> handlePacket(packet));
                } catch (RejectedExecutionException e) {
                    // Queue is full - packet will be dropped
                    // Rejection handler already logs this
                }
                
            } catch (IOException e) {
                if (running) {
                    log.error("Error receiving BACnet packet", e);
                }
            }
        }
    }
    
    private void startMonitoring() {
        executorService.submit(() -> {
            while (running) {
                try {
                    Thread.sleep(30000);  // Log stats every 30 seconds
                    
                    long processed = processedPackets.get();
                    long dropped = droppedPackets.get();
                    double dropRate = processed > 0 ? (dropped * 100.0 / processed) : 0.0;
                    
                    log.info("BACnet service stats - Processed: {}, Dropped: {} ({:.2f}%), " +
                             "Queue size: {}, Active threads: {}/{}", 
                             processed, dropped, dropRate,
                             executorService.getQueue().size(),
                             executorService.getActiveCount(),
                             executorService.getMaximumPoolSize());
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    @PreDestroy
    public void shutdown() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("BACnet service shut down. Final stats - Processed: {}, Dropped: {}",
                 processedPackets.get(), droppedPackets.get());
    }
}
```

### 2. Add Configuration Properties

Add to `application.yml`:
```yaml
bacnet:
  network:
    port: 47808
    bind-address: 0.0.0.0
    broadcast-address: 255.255.255.255
  thread-pool:
    core-size: 5
    max-size: 10
    queue-capacity: 100
    keep-alive-seconds: 60
  device:
    default-instance-id: 1000
```

### 3. Add JMX Monitoring (Optional)

```java
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource(objectName = "com.bacnet.emulator:type=BacnetService", description = "BACnet Service Metrics")
public class BacnetService {
    
    @ManagedAttribute(description = "Total packets processed")
    public long getProcessedPackets() {
        return processedPackets.get();
    }
    
    @ManagedAttribute(description = "Total packets dropped")
    public long getDroppedPackets() {
        return droppedPackets.get();
    }
    
    @ManagedAttribute(description = "Current queue size")
    public int getQueueSize() {
        return executorService != null ? executorService.getQueue().size() : 0;
    }
    
    @ManagedAttribute(description = "Active thread count")
    public int getActiveThreads() {
        return executorService != null ? executorService.getActiveCount() : 0;
    }
}
```

## Acceptance Criteria

- [ ] Thread pool uses bounded queue with configurable capacity
- [ ] Packets are dropped (not queued) when queue is full
- [ ] Dropped packet count is tracked and logged
- [ ] Monitoring logs thread pool statistics every 30 seconds
- [ ] Configuration properties allow tuning pool sizes
- [ ] Unit tests verify:
  - Queue rejects tasks when full
  - Rejection handler is invoked
  - No memory leak from dropped tasks
- [ ] Load test confirms:
  - System remains responsive under packet flood
  - Memory usage stays bounded
  - No OutOfMemoryError occurs
  - Drop rate is reasonable (< 1% under normal load)
- [ ] Documentation explains thread pool configuration
- [ ] JMX metrics exposed for monitoring (optional)
- [ ] Graceful shutdown completes pending tasks
