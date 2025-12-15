package com.bacnet.emulator.service;

import com.bacnet.emulator.dto.EmulatorConfigDto;
import com.bacnet.emulator.dto.NetworkConfigDto;
import com.bacnet.emulator.model.EmulatorConfig;
import com.bacnet.emulator.model.NetworkConfig;
import com.bacnet.emulator.repository.EmulatorConfigRepository;
import com.bacnet.emulator.repository.NetworkConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing emulator configuration.
 * 
 * <p>This service handles both network and emulator behavior configuration.
 * Configuration is persisted to the database and can be updated through the
 * web interface or REST API.
 * 
 * <p>Configuration types:
 * <ul>
 *   <li><b>Network Configuration</b>: BACnet/IP network settings (port, bind address, broadcast address)</li>
 *   <li><b>Emulator Configuration</b>: Behavior settings (response delays, error simulation, logging)</li>
 * </ul>
 * 
 * <p>Network configuration changes require a server restart to take effect,
 * as they affect the UDP socket binding. Emulator configuration changes take
 * effect immediately.
 * 
 * <p>The service automatically creates default configuration if none exists,
 * ensuring the emulator can start with sensible defaults.
 * 
 * @author BACnet Emulator Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
public class ConfigService {
    
    private final NetworkConfigRepository networkConfigRepository;
    private final EmulatorConfigRepository emulatorConfigRepository;
    
    public NetworkConfigDto getNetworkConfig() {
        return networkConfigRepository.findByConfigKey("default")
                .map(this::toNetworkDto)
                .orElseGet(() -> {
                    NetworkConfig config = new NetworkConfig();
                    config.setConfigKey("default");
                    config.setPort(47808);
                    config.setBindAddress("0.0.0.0");
                    config.setBroadcastAddress("255.255.255.255");
                    config.setDefaultDeviceInstanceId(1000);
                    config = networkConfigRepository.save(config);
                    return toNetworkDto(config);
                });
    }
    
    @Transactional
    public NetworkConfigDto updateNetworkConfig(NetworkConfigDto dto) {
        NetworkConfig config = networkConfigRepository.findByConfigKey("default")
                .orElse(new NetworkConfig());
        
        config.setConfigKey("default");
        config.setPort(dto.getPort());
        config.setBindAddress(dto.getBindAddress());
        config.setBroadcastAddress(dto.getBroadcastAddress());
        config.setDefaultDeviceInstanceId(dto.getDefaultDeviceInstanceId());
        
        config = networkConfigRepository.save(config);
        return toNetworkDto(config);
    }
    
    public EmulatorConfigDto getEmulatorConfig() {
        return emulatorConfigRepository.findByConfigKey("default")
                .map(this::toEmulatorDto)
                .orElseGet(() -> {
                    EmulatorConfig config = new EmulatorConfig();
                    config.setConfigKey("default");
                    config.setDefaultResponseDelayMs(0L);
                    config.setEnableErrorSimulation(false);
                    config.setEnableLogging(true);
                    config.setCovNotificationIntervalMs(1000L);
                    config = emulatorConfigRepository.save(config);
                    return toEmulatorDto(config);
                });
    }
    
    @Transactional
    public EmulatorConfigDto updateEmulatorConfig(EmulatorConfigDto dto) {
        EmulatorConfig config = emulatorConfigRepository.findByConfigKey("default")
                .orElse(new EmulatorConfig());
        
        config.setConfigKey("default");
        config.setDefaultResponseDelayMs(dto.getDefaultResponseDelayMs());
        config.setEnableErrorSimulation(dto.getEnableErrorSimulation());
        config.setEnableLogging(dto.getEnableLogging());
        config.setCovNotificationIntervalMs(dto.getCovNotificationIntervalMs());
        
        config = emulatorConfigRepository.save(config);
        return toEmulatorDto(config);
    }
    
    private NetworkConfigDto toNetworkDto(NetworkConfig config) {
        return new NetworkConfigDto(
                config.getId(),
                config.getPort(),
                config.getBindAddress(),
                config.getBroadcastAddress(),
                config.getDefaultDeviceInstanceId()
        );
    }
    
    private EmulatorConfigDto toEmulatorDto(EmulatorConfig config) {
        return new EmulatorConfigDto(
                config.getId(),
                config.getDefaultResponseDelayMs(),
                config.getEnableErrorSimulation(),
                config.getEnableLogging(),
                config.getCovNotificationIntervalMs()
        );
    }
}

