package com.bacnet.emulator.controller;

import com.bacnet.emulator.dto.DeviceDto;
import com.bacnet.emulator.service.DeviceService;
import com.bacnet.emulator.service.MonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {
    
    private final DeviceService deviceService;
    private final MonitorService monitorService;
    
    @GetMapping
    public String listDevices(Model model) {
        model.addAttribute("devices", deviceService.getAllDevices());
        return "devices/list";
    }
    
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("device", new DeviceDto());
        return "devices/create";
    }
    
    @PostMapping("/create")
    public String createDevice(@Valid @ModelAttribute("device") DeviceDto deviceDto,
                              BindingResult result,
                              RedirectAttributes redirectAttributes,
                              HttpServletRequest request) {
        if (result.hasErrors()) {
            return "devices/create";
        }
        
        try {
            DeviceDto created = deviceService.createDevice(deviceDto);
            monitorService.log("INFO",
                String.format("Device created via UI: %s (Instance ID: %d)", created.getDeviceName(), created.getDeviceInstanceId()),
                request.getRemoteAddr(), "UI - CreateDevice", created.getDeviceInstanceId(), null, null,
                String.format("Device ID: %d, Vendor: %s, Model: %s", created.getId(), created.getVendorId(), created.getModelName()));
            redirectAttributes.addFlashAttribute("success", "Device created successfully");
            return "redirect:/devices";
        } catch (Exception e) {
            result.rejectValue("deviceInstanceId", "error.device", e.getMessage());
            return "devices/create";
        }
    }
    
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        DeviceDto device = deviceService.getDeviceById(id);
        model.addAttribute("device", device);
        return "devices/edit";
    }
    
    @PostMapping("/{id}/edit")
    public String updateDevice(@PathVariable Long id,
                              @Valid @ModelAttribute("device") DeviceDto deviceDto,
                              BindingResult result,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "devices/edit";
        }
        
        try {
            deviceService.updateDevice(id, deviceDto);
            redirectAttributes.addFlashAttribute("success", "Device updated successfully");
            return "redirect:/devices";
        } catch (Exception e) {
            result.rejectValue("deviceInstanceId", "error.device", e.getMessage());
            return "devices/edit";
        }
    }
    
    @PostMapping("/{id}/delete")
    public String deleteDevice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            deviceService.deleteDevice(id);
            redirectAttributes.addFlashAttribute("success", "Device deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting device: " + e.getMessage());
        }
        return "redirect:/devices";
    }
}

