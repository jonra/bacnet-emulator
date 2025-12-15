package com.bacnet.emulator.service;

import com.bacnet.emulator.model.BacnetLogEntry;
import com.bacnet.emulator.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for logging and monitoring BACnet activity.
 * 
 * <p>This service provides logging functionality for all BACnet protocol
 * interactions. Logs are stored in the database and can be viewed through
 * the web interface.
 * 
 * <p>Log entries capture:
 * <ul>
 *   <li>Timestamp of the event</li>
 *   <li>Log level (INFO, DEBUG, WARN, ERROR)</li>
 *   <li>Service type (Who-Is, ReadProperty, WriteProperty, etc.)</li>
 *   <li>Source address of the request</li>
 *   <li>Device and object identifiers</li>
 *   <li>Message and details</li>
 * </ul>
 * 
 * <p>Logs are useful for:
 * <ul>
 *   <li>Debugging BACnet communication issues</li>
 *   <li>Monitoring device interactions</li>
 *   <li>Tracking errors and exceptions</li>
 *   <li>Auditing system activity</li>
 * </ul>
 * 
 * <p>The service provides methods to retrieve recent logs or logs since
 * a specific timestamp. Logs are stored in the database and persist across
 * server restarts.
 * 
 * @author BACnet Emulator Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
public class MonitorService {
    
    private final LogEntryRepository logEntryRepository;
    
    @Transactional
    public void log(String level, String message, String sourceAddress, String serviceType,
                   Integer deviceInstanceId, Integer objectType, Integer objectInstance, String details) {
        BacnetLogEntry entry = new BacnetLogEntry();
        entry.setTimestamp(LocalDateTime.now());
        entry.setLogLevel(level);
        entry.setMessage(message);
        entry.setSourceAddress(sourceAddress);
        entry.setServiceType(serviceType);
        entry.setDeviceInstanceId(deviceInstanceId);
        entry.setObjectType(objectType);
        entry.setObjectInstance(objectInstance);
        entry.setDetails(details);
        logEntryRepository.save(entry);
    }
    
    public List<BacnetLogEntry> getRecentLogs(int limit) {
        return logEntryRepository.findTop100ByOrderByTimestampDesc();
    }
    
    public List<BacnetLogEntry> getLogsSince(LocalDateTime since) {
        return logEntryRepository.findByTimestampAfterOrderByTimestampDesc(since);
    }
}

