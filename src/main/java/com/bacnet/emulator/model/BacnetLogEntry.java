package com.bacnet.emulator.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bacnet_log_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BacnetLogEntry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
    
    @Column(nullable = false)
    private String logLevel; // INFO, DEBUG, WARN, ERROR
    
    @Column(nullable = false)
    private String message;
    
    private String sourceAddress;
    
    private String serviceType; // Who-Is, ReadProperty, WriteProperty, etc.
    
    private Integer deviceInstanceId;
    
    private Integer objectType;
    
    private Integer objectInstance;
    
    @Column(columnDefinition = "TEXT")
    private String details;
}

