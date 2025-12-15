package com.bacnet.emulator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmulatorConfigDto {
    
    private Long id;
    
    @NotNull(message = "Default Response Delay is required")
    @Min(value = 0, message = "Response Delay must be non-negative")
    private Long defaultResponseDelayMs;
    
    @NotNull(message = "Error Simulation flag is required")
    private Boolean enableErrorSimulation;
    
    @NotNull(message = "Logging flag is required")
    private Boolean enableLogging;
    
    @NotNull(message = "COV Notification Interval is required")
    @Min(value = 0, message = "COV Notification Interval must be non-negative")
    private Long covNotificationIntervalMs;
}

