package com.bacnet.emulator.repository;

import com.bacnet.emulator.model.BacnetDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<BacnetDevice, Long> {
    
    Optional<BacnetDevice> findByDeviceInstanceId(Integer deviceInstanceId);
    
    List<BacnetDevice> findByEnabledTrue();
    
    boolean existsByDeviceInstanceId(Integer deviceInstanceId);
}

