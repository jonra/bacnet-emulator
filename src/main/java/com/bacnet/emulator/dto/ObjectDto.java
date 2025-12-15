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
public class ObjectDto {
    
    private Long id;
    
    @NotNull(message = "Device ID is required")
    private Long deviceId;
    
    @NotNull(message = "Object Type is required")
    @Min(value = 0, message = "Object Type must be non-negative")
    private Integer objectType;
    
    @NotNull(message = "Object Instance is required")
    @Min(value = 1, message = "Object Instance must be positive")
    private Integer objectInstance;
    
    @NotBlank(message = "Object Name is required")
    private String objectName;
    
    private String presentValue;
    
    private String units;
    
    private String description;
    
    private Boolean writable = false;
    
    private Boolean covEnabled = false;
    
    private Double covIncrement;
    
    private Boolean outOfService = false;
    
    // Helper fields for UI
    private String objectTypeName;
    private String deviceName;
}

