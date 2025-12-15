package com.bacnet.emulator.repository;

import com.bacnet.emulator.model.BacnetObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ObjectRepository extends JpaRepository<BacnetObject, Long> {
    
    List<BacnetObject> findByDeviceId(Long deviceId);
    
    Optional<BacnetObject> findByDeviceIdAndObjectTypeAndObjectInstance(
            Long deviceId, Integer objectType, Integer objectInstance);
    
    List<BacnetObject> findByDeviceIdAndCovEnabledTrue(Long deviceId);
}

