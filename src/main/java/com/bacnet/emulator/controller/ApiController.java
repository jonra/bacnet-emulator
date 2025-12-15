package com.bacnet.emulator.controller;

import com.bacnet.emulator.dto.*;
import com.bacnet.emulator.model.BacnetLogEntry;
import com.bacnet.emulator.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "BACnet Emulator API", description = "RESTful API for managing BACnet devices, objects, and configuration")
public class ApiController {
    
    private final DeviceService deviceService;
    private final ObjectService objectService;
    private final ConfigService configService;
    private final MonitorService monitorService;
    
    // Device endpoints
    @Operation(summary = "Get all devices", description = "Retrieves a list of all configured BACnet devices")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved devices",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceDto.class)))
    })
    @GetMapping("/devices")
    public ResponseEntity<List<DeviceDto>> getAllDevices() {
        return ResponseEntity.ok(deviceService.getAllDevices());
    }
    
    @Operation(summary = "Get device by ID", description = "Retrieves a specific device by its database ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Device found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceDto.class))),
        @ApiResponse(responseCode = "404", description = "Device not found")
    })
    @GetMapping("/devices/{id}")
    public ResponseEntity<DeviceDto> getDevice(
            @Parameter(description = "Device database ID", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(deviceService.getDeviceById(id));
    }
    
    @Operation(summary = "Create a new device", description = "Creates a new virtual BACnet device with the specified properties")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Device created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid device data or duplicate instance ID")
    })
    @PostMapping("/devices")
    public ResponseEntity<DeviceDto> createDevice(
            @Parameter(description = "Device configuration", required = true) @RequestBody DeviceDto deviceDto,
            HttpServletRequest request) {
        DeviceDto created = deviceService.createDevice(deviceDto);
        monitorService.log("INFO", 
            String.format("Device created via API: %s (Instance ID: %d)", created.getDeviceName(), created.getDeviceInstanceId()),
            request.getRemoteAddr(), "REST API - CreateDevice", created.getDeviceInstanceId(), null, null,
            String.format("Device ID: %d, Vendor: %s, Model: %s", created.getId(), created.getVendorId(), created.getModelName()));
        return ResponseEntity.ok(created);
    }
    
    @Operation(summary = "Update device", description = "Updates an existing device's properties")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Device updated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceDto.class))),
        @ApiResponse(responseCode = "404", description = "Device not found"),
        @ApiResponse(responseCode = "400", description = "Invalid device data")
    })
    @PutMapping("/devices/{id}")
    public ResponseEntity<DeviceDto> updateDevice(
            @Parameter(description = "Device database ID", required = true) @PathVariable Long id,
            @Parameter(description = "Updated device configuration", required = true) @RequestBody DeviceDto deviceDto,
            HttpServletRequest request) {
        DeviceDto updated = deviceService.updateDevice(id, deviceDto);
        monitorService.log("INFO",
            String.format("Device updated via API: %s (Instance ID: %d)", updated.getDeviceName(), updated.getDeviceInstanceId()),
            request.getRemoteAddr(), "REST API - UpdateDevice", updated.getDeviceInstanceId(), null, null,
            String.format("Enabled: %s", updated.getEnabled()));
        return ResponseEntity.ok(updated);
    }
    
    @Operation(summary = "Delete device", description = "Deletes a device and all its associated objects")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Device deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Device not found")
    })
    @DeleteMapping("/devices/{id}")
    public ResponseEntity<Void> deleteDevice(
            @Parameter(description = "Device database ID", required = true) @PathVariable Long id) {
        deviceService.deleteDevice(id);
        return ResponseEntity.ok().build();
    }
    
    // Object endpoints
    @Operation(summary = "Get all objects", description = "Retrieves all BACnet objects, optionally filtered by device ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved objects",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ObjectDto.class)))
    })
    @GetMapping("/objects")
    public ResponseEntity<List<ObjectDto>> getAllObjects(
            @Parameter(description = "Optional device ID to filter objects") @RequestParam(required = false) Long deviceId) {
        if (deviceId != null) {
            return ResponseEntity.ok(objectService.getObjectsByDevice(deviceId));
        }
        return ResponseEntity.ok(objectService.getAllObjects());
    }
    
    @Operation(summary = "Get object by ID", description = "Retrieves a specific object by its database ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Object found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ObjectDto.class))),
        @ApiResponse(responseCode = "404", description = "Object not found")
    })
    @GetMapping("/objects/{id}")
    public ResponseEntity<ObjectDto> getObject(
            @Parameter(description = "Object database ID", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(objectService.getObjectById(id));
    }
    
    @Operation(summary = "Create a new object", description = "Creates a new BACnet object (Analog Input/Output, Binary Input/Output) for a device")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Object created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ObjectDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid object data or duplicate object identifier"),
        @ApiResponse(responseCode = "404", description = "Device not found")
    })
    @PostMapping("/objects")
    public ResponseEntity<ObjectDto> createObject(
            @Parameter(description = "Object configuration", required = true) @RequestBody ObjectDto objectDto,
            HttpServletRequest request) {
        ObjectDto created = objectService.createObject(objectDto);
        DeviceDto device = deviceService.getDeviceById(created.getDeviceId());
        monitorService.log("INFO",
            String.format("Object created via API: %s (Type: %s, Instance: %d)", created.getObjectName(), 
                created.getObjectTypeName(), created.getObjectInstance()),
            request.getRemoteAddr(), "REST API - CreateObject", device.getDeviceInstanceId(), 
            created.getObjectType(), created.getObjectInstance(),
            String.format("Present Value: %s, Writable: %s, COV: %s", created.getPresentValue(), 
                created.getWritable(), created.getCovEnabled()));
        return ResponseEntity.ok(created);
    }
    
    @Operation(summary = "Update object", description = "Updates an existing object's properties and configuration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Object updated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ObjectDto.class))),
        @ApiResponse(responseCode = "404", description = "Object not found"),
        @ApiResponse(responseCode = "400", description = "Invalid object data")
    })
    @PutMapping("/objects/{id}")
    public ResponseEntity<ObjectDto> updateObject(
            @Parameter(description = "Object database ID", required = true) @PathVariable Long id,
            @Parameter(description = "Updated object configuration", required = true) @RequestBody ObjectDto objectDto) {
        return ResponseEntity.ok(objectService.updateObject(id, objectDto));
    }
    
    @Operation(summary = "Update object present value", description = "Updates only the present value of an object (useful for quick value changes)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Value updated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ObjectDto.class))),
        @ApiResponse(responseCode = "404", description = "Object not found")
    })
    @PutMapping("/objects/{id}/value")
    public ResponseEntity<ObjectDto> updateObjectValue(
            @Parameter(description = "Object database ID", required = true) @PathVariable Long id,
            @Parameter(description = "Request body containing presentValue", required = true) @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        String value = request.get("presentValue");
        ObjectDto updated = objectService.updateObjectValue(id, value);
        DeviceDto device = deviceService.getDeviceById(updated.getDeviceId());
        monitorService.log("INFO",
            String.format("Object value updated via API: %s = %s", updated.getObjectName(), updated.getPresentValue()),
            httpRequest.getRemoteAddr(), "REST API - UpdateValue", device.getDeviceInstanceId(),
            updated.getObjectType(), updated.getObjectInstance(),
            String.format("Previous value changed to: %s", value));
        return ResponseEntity.ok(updated);
    }
    
    @Operation(summary = "Delete object", description = "Deletes an object from its device")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Object deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Object not found")
    })
    @DeleteMapping("/objects/{id}")
    public ResponseEntity<Void> deleteObject(
            @Parameter(description = "Object database ID", required = true) @PathVariable Long id) {
        objectService.deleteObject(id);
        return ResponseEntity.ok().build();
    }
    
    // Configuration endpoints
    @Operation(summary = "Get network configuration", description = "Retrieves the current BACnet/IP network configuration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Network configuration retrieved",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NetworkConfigDto.class)))
    })
    @GetMapping("/config/network")
    public ResponseEntity<NetworkConfigDto> getNetworkConfig() {
        return ResponseEntity.ok(configService.getNetworkConfig());
    }
    
    @Operation(summary = "Update network configuration", description = "Updates the BACnet/IP network settings. Note: Changes require server restart to take effect.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Network configuration updated",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NetworkConfigDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid configuration data")
    })
    @PutMapping("/config/network")
    public ResponseEntity<NetworkConfigDto> updateNetworkConfig(
            @Parameter(description = "Network configuration", required = true) @RequestBody NetworkConfigDto configDto) {
        return ResponseEntity.ok(configService.updateNetworkConfig(configDto));
    }
    
    @Operation(summary = "Get emulator configuration", description = "Retrieves the current emulator behavior configuration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Emulator configuration retrieved",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmulatorConfigDto.class)))
    })
    @GetMapping("/config/emulator")
    public ResponseEntity<EmulatorConfigDto> getEmulatorConfig() {
        return ResponseEntity.ok(configService.getEmulatorConfig());
    }
    
    @Operation(summary = "Update emulator configuration", description = "Updates emulator behavior settings (response delays, error simulation, etc.)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Emulator configuration updated",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmulatorConfigDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid configuration data")
    })
    @PutMapping("/config/emulator")
    public ResponseEntity<EmulatorConfigDto> updateEmulatorConfig(
            @Parameter(description = "Emulator configuration", required = true) @RequestBody EmulatorConfigDto configDto) {
        return ResponseEntity.ok(configService.updateEmulatorConfig(configDto));
    }
    
    // Statistics endpoint
    @Operation(summary = "Get system statistics", description = "Retrieves current system statistics (device counts, object counts, etc.)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistics retrieved",
            content = @Content(mediaType = "application/json"))
    })
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = Map.of(
                "totalDevices", deviceService.getAllDevices().size(),
                "enabledDevices", deviceService.getEnabledDevices().size(),
                "totalObjects", objectService.getAllObjects().size()
        );
        return ResponseEntity.ok(stats);
    }
}

