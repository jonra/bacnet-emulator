package com.bacnet.emulator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bacnetEmulatorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BACnet Emulator API")
                        .description("RESTful API for managing virtual BACnet devices, objects, and configuration. " +
                                "This API allows programmatic access to all emulator functionality.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("BACnet Emulator Team")
                                .url("https://github.com/yourusername/bacnet-emulator")
                                .email("support@example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development server"),
                        new Server()
                                .url("https://api.example.com")
                                .description("Production server")
                ));
    }
}

