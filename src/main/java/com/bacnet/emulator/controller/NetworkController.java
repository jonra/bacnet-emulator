package com.bacnet.emulator.controller;

import com.bacnet.emulator.dto.NetworkConfigDto;
import com.bacnet.emulator.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/network")
@RequiredArgsConstructor
public class NetworkController {
    
    private final ConfigService configService;
    
    @GetMapping("/config")
    public String showConfig(Model model) {
        model.addAttribute("config", configService.getNetworkConfig());
        return "network/config";
    }
    
    @PostMapping("/config")
    public String updateConfig(@Valid @ModelAttribute("config") NetworkConfigDto configDto,
                              BindingResult result,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "network/config";
        }
        
        try {
            configService.updateNetworkConfig(configDto);
            redirectAttributes.addFlashAttribute("success", "Network configuration updated successfully. Restart required.");
            return "redirect:/network/config";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating configuration: " + e.getMessage());
            return "redirect:/network/config";
        }
    }
}

