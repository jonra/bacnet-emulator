package com.bacnet.emulator.service;

import com.bacnet.emulator.dto.DeviceDto;
import com.bacnet.emulator.model.BacnetDevice;
import com.bacnet.emulator.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing BACnet device entities.
 * 
 * <p>This service provides CRUD operations for virtual BACnet devices. Each device
 * represents a virtual BACnet device that can be discovered and interacted with
 * by BACnet clients.
 * 
 * <p>Key responsibilities:
 * <ul>
 *   <li>Create, read, update, and delete devices</li>
 *   <li>Validate device instance ID uniqueness</li>
 *   <li>Manage device enable/disable state</li>
 *   <li>Convert between entity and DTO objects</li>
 * </ul>
 * 
 * <p>Device instance IDs must be unique across all devices. When a device is
 * disabled, it will not respond to Who-Is requests and will not be discoverable
 * by BACnet clients.
 * 
 * @author BACnet Emulator Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
public class DeviceService {
    
    private final DeviceRepository deviceRepository;
    
    public List<DeviceDto> getAllDevices() {
        return deviceRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    public List<DeviceDto> getEnabledDevices() {
        return deviceRepository.findByEnabledTrue().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    public DeviceDto getDeviceById(Long id) {
        return deviceRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new RuntimeException("Device not found: " + id));
    }
    
    public DeviceDto getDeviceByInstanceId(Integer deviceInstanceId) {
        return deviceRepository.findByDeviceInstanceId(deviceInstanceId)
                .map(this::toDto)
                .orElseThrow(() -> new RuntimeException("Device not found: " + deviceInstanceId));
    }
    
    @Transactional
    public DeviceDto createDevice(DeviceDto dto) {
        if (deviceRepository.existsByDeviceInstanceId(dto.getDeviceInstanceId())) {
            throw new RuntimeException("Device with instance ID " + dto.getDeviceInstanceId() + " already exists");
        }
        
        BacnetDevice device = toEntity(dto);
        device = deviceRepository.save(device);
        return toDto(device);
    }
    
    @Transactional
    public DeviceDto updateDevice(Long id, DeviceDto dto) {
        BacnetDevice device = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Device not found: " + id));
        
        // Check if device instance ID is being changed and if it conflicts
        if (!device.getDeviceInstanceId().equals(dto.getDeviceInstanceId())) {
            if (deviceRepository.existsByDeviceInstanceId(dto.getDeviceInstanceId())) {
                throw new RuntimeException("Device with instance ID " + dto.getDeviceInstanceId() + " already exists");
            }
        }
        
        device.setDeviceInstanceId(dto.getDeviceInstanceId());
        device.setDeviceName(dto.getDeviceName());
        device.setVendorId(dto.getVendorId());
        device.setModelName(dto.getModelName());
        device.setDescription(dto.getDescription());
        device.setEnabled(dto.getEnabled());
        
        device = deviceRepository.save(device);
        return toDto(device);
    }
    
    @Transactional
    public void deleteDevice(Long id) {
        if (!deviceRepository.existsById(id)) {
            throw new RuntimeException("Device not found: " + id);
        }
        deviceRepository.deleteById(id);
    }
    
    private DeviceDto toDto(BacnetDevice device) {
        return new DeviceDto(
                device.getId(),
                device.getDeviceInstanceId(),
                device.getDeviceName(),
                device.getVendorId(),
                device.getModelName(),
                device.getDescription(),
                device.getEnabled()
        );
    }
    
    private BacnetDevice toEntity(DeviceDto dto) {
        BacnetDevice device = new BacnetDevice();
        device.setDeviceInstanceId(dto.getDeviceInstanceId());
        device.setDeviceName(dto.getDeviceName());
        device.setVendorId(dto.getVendorId());
        device.setModelName(dto.getModelName());
        device.setDescription(dto.getDescription());
        device.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        return device;
    }
}

