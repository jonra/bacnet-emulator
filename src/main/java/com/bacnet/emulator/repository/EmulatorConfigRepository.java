package com.bacnet.emulator.repository;

import com.bacnet.emulator.model.EmulatorConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmulatorConfigRepository extends JpaRepository<EmulatorConfig, Long> {
    
    Optional<EmulatorConfig> findByConfigKey(String configKey);
}

