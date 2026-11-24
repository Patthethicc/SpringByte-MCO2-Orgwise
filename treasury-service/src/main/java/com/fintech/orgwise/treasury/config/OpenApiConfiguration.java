package com.fintech.orgwise.treasury.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata for treasury-service, collected into the gateway's Swagger UI. */
@Configuration
public class OpenApiConfiguration {

    private static final String BEARER = "bearerAuth";

    /**
     * @return API description with JWT bearer authentication
     */
    @Bean
    public OpenAPI treasuryOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Orgwise Treasury Service API")
                        .description("Treasury balance, transfer ledger and the disbursement saga")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
