package com.bacnet.emulator.repository;

import com.bacnet.emulator.model.NetworkConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NetworkConfigRepository extends JpaRepository<NetworkConfig, Long> {
    
    Optional<NetworkConfig> findByConfigKey(String configKey);
}

