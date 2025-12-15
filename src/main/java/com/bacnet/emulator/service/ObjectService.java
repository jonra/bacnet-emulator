package com.bacnet.emulator.service;

import com.bacnet.emulator.dto.ObjectDto;
import com.bacnet.emulator.model.BacnetDevice;
import com.bacnet.emulator.model.BacnetObject;
import com.bacnet.emulator.repository.DeviceRepository;
import com.bacnet.emulator.repository.ObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing BACnet object entities.
 * 
 * <p>This service provides CRUD operations for BACnet objects within devices.
 * Objects represent BACnet data points such as analog inputs, binary outputs, etc.
 * 
 * <p>Supported object types:
 * <ul>
 *   <li>Analog Input (0) - Read-only analog values</li>
 *   <li>Analog Output (1) - Read/write analog values</li>
 *   <li>Binary Input (3) - Read-only binary values</li>
 *   <li>Binary Output (4) - Read/write binary values</li>
 * </ul>
 * 
 * <p>Key responsibilities:
 * <ul>
 *   <li>Create, read, update, and delete objects</li>
 *   <li>Validate object identifier uniqueness within a device</li>
 *   <li>Manage object properties (present value, units, COV settings)</li>
 *   <li>Handle value updates and COV notifications</li>
 *   <li>Convert between entity and DTO objects</li>
 * </ul>
 * 
 * <p>Object identifiers (type + instance) must be unique within each device.
 * Present values are stored as strings and converted based on object type when
 * responding to BACnet requests.
 * 
 * @author BACnet Emulator Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
public class ObjectService {
    
    private final ObjectRepository objectRepository;
    private final DeviceRepository deviceRepository;
    
    public List<ObjectDto> getAllObjects() {
        return objectRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    public List<ObjectDto> getObjectsByDevice(Long deviceId) {
        return objectRepository.findByDeviceId(deviceId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    public ObjectDto getObjectById(Long id) {
        return objectRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new RuntimeException("Object not found: " + id));
    }
    
    public ObjectDto getObjectByDeviceAndTypeAndInstance(Long deviceId, Integer objectType, Integer objectInstance) {
        return objectRepository.findByDeviceIdAndObjectTypeAndObjectInstance(deviceId, objectType, objectInstance)
                .map(this::toDto)
                .orElseThrow(() -> new RuntimeException("Object not found"));
    }
    
    @Transactional
    public ObjectDto createObject(ObjectDto dto) {
        BacnetDevice device = deviceRepository.findById(dto.getDeviceId())
                .orElseThrow(() -> new RuntimeException("Device not found: " + dto.getDeviceId()));
        
        if (objectRepository.findByDeviceIdAndObjectTypeAndObjectInstance(
                dto.getDeviceId(), dto.getObjectType(), dto.getObjectInstance()).isPresent()) {
            throw new RuntimeException("Object already exists");
        }
        
        BacnetObject object = toEntity(dto, device);
        object = objectRepository.save(object);
        return toDto(object);
    }
    
    @Transactional
    public ObjectDto updateObject(Long id, ObjectDto dto) {
        BacnetObject object = objectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Object not found: " + id));
        
        // Check if object identifier is being changed
        if (!object.getObjectType().equals(dto.getObjectType()) || 
            !object.getObjectInstance().equals(dto.getObjectInstance())) {
            if (objectRepository.findByDeviceIdAndObjectTypeAndObjectInstance(
                    dto.getDeviceId(), dto.getObjectType(), dto.getObjectInstance()).isPresent()) {
                throw new RuntimeException("Object with this identifier already exists");
            }
        }
        
        object.setObjectType(dto.getObjectType());
        object.setObjectInstance(dto.getObjectInstance());
        object.setObjectName(dto.getObjectName());
        object.setPresentValue(dto.getPresentValue());
        object.setUnits(dto.getUnits());
        object.setDescription(dto.getDescription());
        object.setWritable(dto.getWritable());
        object.setCovEnabled(dto.getCovEnabled());
        object.setCovIncrement(dto.getCovIncrement());
        object.setOutOfService(dto.getOutOfService());
        
        object = objectRepository.save(object);
        return toDto(object);
    }
    
    @Transactional
    public ObjectDto updateObjectValue(Long id, String presentValue) {
        BacnetObject object = objectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Object not found: " + id));
        
        object.setPresentValue(presentValue);
        object = objectRepository.save(object);
        return toDto(object);
    }
    
    @Transactional
    public void deleteObject(Long id) {
        if (!objectRepository.existsById(id)) {
            throw new RuntimeException("Object not found: " + id);
        }
        objectRepository.deleteById(id);
    }
    
    private ObjectDto toDto(BacnetObject object) {
        ObjectDto dto = new ObjectDto();
        dto.setId(object.getId());
        dto.setDeviceId(object.getDevice().getId());
        dto.setObjectType(object.getObjectType());
        dto.setObjectInstance(object.getObjectInstance());
        dto.setObjectName(object.getObjectName());
        dto.setPresentValue(object.getPresentValue());
        dto.setUnits(object.getUnits());
        dto.setDescription(object.getDescription());
        dto.setWritable(object.getWritable());
        dto.setCovEnabled(object.getCovEnabled());
        dto.setCovIncrement(object.getCovIncrement());
        dto.setOutOfService(object.getOutOfService());
        dto.setObjectTypeName(getObjectTypeName(object.getObjectType()));
        dto.setDeviceName(object.getDevice().getDeviceName());
        return dto;
    }
    
    private BacnetObject toEntity(ObjectDto dto, BacnetDevice device) {
        BacnetObject object = new BacnetObject();
        object.setDevice(device);
        object.setObjectType(dto.getObjectType());
        object.setObjectInstance(dto.getObjectInstance());
        object.setObjectName(dto.getObjectName());
        object.setPresentValue(dto.getPresentValue());
        object.setUnits(dto.getUnits());
        object.setDescription(dto.getDescription());
        object.setWritable(dto.getWritable() != null ? dto.getWritable() : false);
        object.setCovEnabled(dto.getCovEnabled() != null ? dto.getCovEnabled() : false);
        object.setCovIncrement(dto.getCovIncrement());
        object.setOutOfService(dto.getOutOfService() != null ? dto.getOutOfService() : false);
        return object;
    }
    
    private String getObjectTypeName(Integer objectType) {
        return switch (objectType) {
            case 0 -> "Analog Input";
            case 1 -> "Analog Output";
            case 3 -> "Binary Input";
            case 4 -> "Binary Output";
            default -> "Unknown (" + objectType + ")";
        };
    }
}

