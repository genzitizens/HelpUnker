package com.helpunker.common.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "HelpUnker API",
                        version = "v1",
                        description = "API documentation for the HelpUnker backend service.",
                        contact = @Contact(name = "HelpUnker Team", email = "support@helpunker.com")))
public class OpenApiConfiguration {}
