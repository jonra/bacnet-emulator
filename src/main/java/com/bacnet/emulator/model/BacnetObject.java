package com.bacnet.emulator.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bacnet_objects", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"device_id", "object_type", "object_instance"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BacnetObject {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private BacnetDevice device;
    
    @Column(nullable = false)
    private Integer objectType; // 0=AnalogInput, 1=AnalogOutput, 3=BinaryInput, 4=BinaryOutput
    
    @Column(nullable = false)
    private Integer objectInstance;
    
    @Column(nullable = false)
    private String objectName;
    
    @Column(columnDefinition = "VARCHAR(255)")
    private String presentValue; // Stored as string, converted based on object type
    
    private String units; // For analog objects (e.g., "degreesCelsius", "percent")
    
    private String description;
    
    @Column(nullable = false)
    private Boolean writable = false;
    
    @Column(nullable = false)
    private Boolean covEnabled = false;
    
    private Double covIncrement; // For analog objects
    
    @Column(nullable = false)
    private Boolean outOfService = false;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

