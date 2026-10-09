package pe.gob.munisanmiguel.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.gob.munisanmiguel.service.AdminSettingsService;
import pe.gob.munisanmiguel.service.AdminSettingsService.*;

@RestController @RequestMapping("/api/settings")
public class AdminSettingsController {
    private final AdminSettingsService service;
    public AdminSettingsController(AdminSettingsService service){this.service=service;}
    @GetMapping("/me") public Preferences mine(Authentication auth){return service.forUser(auth.getName());}
    @GetMapping("/users/{username}") public Preferences user(@PathVariable String username,Authentication auth){return service.forUsername(username,auth.getName());}
    @PutMapping("/users/{username}") public Preferences update(@PathVariable String username,@RequestBody Preferences request,Authentication auth){return service.update(username,request,auth.getName());}
    @GetMapping("/daily-limit") public DailyLimit dailyLimit(){return new DailyLimit(service.dailyLimit());}
    @PutMapping("/daily-limit") public DailyLimit dailyLimit(@RequestBody DailyLimit request,Authentication auth){return service.updateLimit(request.limite(),auth.getName());}
}
