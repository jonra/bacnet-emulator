package com.bacnet.emulator.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "emulator_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmulatorConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String configKey = "default";
    
    @Column(nullable = false)
    private Long defaultResponseDelayMs = 0L;
    
    @Column(nullable = false)
    private Boolean enableErrorSimulation = false;
    
    @Column(nullable = false)
    private Boolean enableLogging = true;
    
    private Long covNotificationIntervalMs = 1000L;
}

