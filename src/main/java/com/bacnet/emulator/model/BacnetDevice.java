package com.bacnet.emulator.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bacnet_devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BacnetDevice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private Integer deviceInstanceId;
    
    @Column(nullable = false)
    private String deviceName;
    
    private String vendorId;
    
    private String modelName;
    
    private String description;
    
    @Column(nullable = false)
    private Boolean enabled = true;
    
    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BacnetObject> objects = new ArrayList<>();
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

