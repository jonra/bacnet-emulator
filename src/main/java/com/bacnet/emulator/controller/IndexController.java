package com.bacnet.emulator.controller;

import com.bacnet.emulator.service.DeviceService;
import com.bacnet.emulator.service.ObjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class IndexController {
    
    private final DeviceService deviceService;
    private final ObjectService objectService;
    
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("totalDevices", deviceService.getAllDevices().size());
        model.addAttribute("enabledDevices", deviceService.getEnabledDevices().size());
        model.addAttribute("totalObjects", objectService.getAllObjects().size());
        return "index";
    }
}

