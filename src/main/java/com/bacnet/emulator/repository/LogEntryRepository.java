package com.bacnet.emulator.repository;

import com.bacnet.emulator.model.BacnetLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LogEntryRepository extends JpaRepository<BacnetLogEntry, Long> {
    
    List<BacnetLogEntry> findByTimestampAfterOrderByTimestampDesc(LocalDateTime after);
    
    List<BacnetLogEntry> findTop100ByOrderByTimestampDesc();
}

