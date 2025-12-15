package com.bacnet.emulator.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "network_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NetworkConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String configKey = "default";
    
    @Column(nullable = false)
    private Integer port = 47808;
    
    @Column(nullable = false)
    private String bindAddress = "0.0.0.0";
    
    @Column(nullable = false)
    private String broadcastAddress = "255.255.255.255";
    
    private Integer defaultDeviceInstanceId = 1000;
}

