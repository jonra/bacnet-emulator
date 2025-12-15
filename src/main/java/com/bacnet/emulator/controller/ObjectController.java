package com.bacnet.emulator.controller;

import com.bacnet.emulator.dto.DeviceDto;
import com.bacnet.emulator.dto.ObjectDto;
import com.bacnet.emulator.service.DeviceService;
import com.bacnet.emulator.service.MonitorService;
import com.bacnet.emulator.service.ObjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/objects")
@RequiredArgsConstructor
public class ObjectController {
    
    private final ObjectService objectService;
    private final DeviceService deviceService;
    private final MonitorService monitorService;
    
    @GetMapping
    public String listObjects(@RequestParam(required = false) Long deviceId, Model model) {
        if (deviceId != null) {
            model.addAttribute("objects", objectService.getObjectsByDevice(deviceId));
            model.addAttribute("device", deviceService.getDeviceById(deviceId));
        } else {
            model.addAttribute("objects", objectService.getAllObjects());
        }
        model.addAttribute("devices", deviceService.getAllDevices());
        return "objects/list";
    }
    
    @GetMapping("/create")
    public String showCreateForm(@RequestParam(required = false) Long deviceId, Model model) {
        ObjectDto object = new ObjectDto();
        if (deviceId != null) {
            object.setDeviceId(deviceId);
        }
        model.addAttribute("object", object);
        model.addAttribute("devices", deviceService.getAllDevices());
        model.addAttribute("objectTypes", getObjectTypes());
        return "objects/create";
    }
    
    @PostMapping("/create")
    public String createObject(@Valid @ModelAttribute("object") ObjectDto objectDto,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirectAttributes,
                              HttpServletRequest request) {
        if (result.hasErrors()) {
            model.addAttribute("devices", deviceService.getAllDevices());
            model.addAttribute("objectTypes", getObjectTypes());
            return "objects/create";
        }
        
        try {
            ObjectDto created = objectService.createObject(objectDto);
            DeviceDto device = deviceService.getDeviceById(created.getDeviceId());
            monitorService.log("INFO",
                String.format("Object created via UI: %s (Type: %s, Instance: %d)", created.getObjectName(),
                    created.getObjectTypeName(), created.getObjectInstance()),
                request.getRemoteAddr(), "UI - CreateObject", device.getDeviceInstanceId(),
                created.getObjectType(), created.getObjectInstance(),
                String.format("Present Value: %s, Writable: %s, COV: %s", created.getPresentValue(),
                    created.getWritable(), created.getCovEnabled()));
            redirectAttributes.addFlashAttribute("success", "Object created successfully");
            return "redirect:/objects?deviceId=" + objectDto.getDeviceId();
        } catch (Exception e) {
            result.rejectValue("objectInstance", "error.object", e.getMessage());
            model.addAttribute("devices", deviceService.getAllDevices());
            model.addAttribute("objectTypes", getObjectTypes());
            return "objects/create";
        }
    }
    
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        ObjectDto object = objectService.getObjectById(id);
        model.addAttribute("object", object);
        model.addAttribute("devices", deviceService.getAllDevices());
        model.addAttribute("objectTypes", getObjectTypes());
        return "objects/edit";
    }
    
    @PostMapping("/{id}/edit")
    public String updateObject(@PathVariable Long id,
                              @Valid @ModelAttribute("object") ObjectDto objectDto,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("devices", deviceService.getAllDevices());
            model.addAttribute("objectTypes", getObjectTypes());
            return "objects/edit";
        }
        
        try {
            objectService.updateObject(id, objectDto);
            redirectAttributes.addFlashAttribute("success", "Object updated successfully");
            return "redirect:/objects?deviceId=" + objectDto.getDeviceId();
        } catch (Exception e) {
            result.rejectValue("objectInstance", "error.object", e.getMessage());
            model.addAttribute("devices", deviceService.getAllDevices());
            model.addAttribute("objectTypes", getObjectTypes());
            return "objects/edit";
        }
    }
    
    @PostMapping("/{id}/value")
    public String updateValue(@PathVariable Long id,
                             @RequestParam String presentValue,
                             RedirectAttributes redirectAttributes,
                             HttpServletRequest request) {
        try {
            ObjectDto updated = objectService.updateObjectValue(id, presentValue);
            DeviceDto device = deviceService.getDeviceById(updated.getDeviceId());
            monitorService.log("INFO",
                String.format("Object value updated via UI: %s = %s", updated.getObjectName(), updated.getPresentValue()),
                request.getRemoteAddr(), "UI - UpdateValue", device.getDeviceInstanceId(),
                updated.getObjectType(), updated.getObjectInstance(),
                String.format("Value changed to: %s", presentValue));
            redirectAttributes.addFlashAttribute("success", "Value updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating value: " + e.getMessage());
        }
        return "redirect:/objects";
    }
    
    @PostMapping("/{id}/delete")
    public String deleteObject(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            ObjectDto object = objectService.getObjectById(id);
            Long deviceId = object.getDeviceId();
            objectService.deleteObject(id);
            redirectAttributes.addFlashAttribute("success", "Object deleted successfully");
            return "redirect:/objects?deviceId=" + deviceId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting object: " + e.getMessage());
            return "redirect:/objects";
        }
    }
    
    private java.util.Map<Integer, String> getObjectTypes() {
        return java.util.Map.of(
                0, "Analog Input",
                1, "Analog Output",
                3, "Binary Input",
                4, "Binary Output"
        );
    }
}

