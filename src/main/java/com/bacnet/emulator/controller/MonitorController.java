package com.bacnet.emulator.controller;

import com.bacnet.emulator.model.BacnetLogEntry;
import com.bacnet.emulator.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/monitor")
@RequiredArgsConstructor
public class MonitorController {
    
    private final LogEntryRepository logEntryRepository;
    
    @GetMapping("/logs")
    public String showLogs(@RequestParam(defaultValue = "100") int limit, Model model) {
        List<BacnetLogEntry> logs = logEntryRepository.findTop100ByOrderByTimestampDesc();
        model.addAttribute("logs", logs);
        return "monitor/logs";
    }
}

