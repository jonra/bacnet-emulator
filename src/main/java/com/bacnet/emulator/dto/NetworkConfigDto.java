package com.bacnet.emulator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NetworkConfigDto {
    
    private Long id;
    
    @NotNull(message = "Port is required")
    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private Integer port;
    
    @NotNull(message = "Bind Address is required")
    private String bindAddress;
    
    @NotNull(message = "Broadcast Address is required")
    private String broadcastAddress;
    
    @NotNull(message = "Default Device Instance ID is required")
    @Min(value = 1, message = "Device Instance ID must be positive")
    private Integer defaultDeviceInstanceId;
}

