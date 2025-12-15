package com.bacnet.emulator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDto {
    
    private Long id;
    
    @NotNull(message = "Device Instance ID is required")
    @Min(value = 1, message = "Device Instance ID must be positive")
    private Integer deviceInstanceId;
    
    @NotBlank(message = "Device Name is required")
    private String deviceName;
    
    private String vendorId;
    
    private String modelName;
    
    private String description;
    
    private Boolean enabled = true;
}

