package org.myswan.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI mySwanOpenAPI() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:8070");
        localServer.setDescription("Local Development Server");

        Server prodServer = new Server();
        prodServer.setUrl("http://jpmaxtricks.com:8070");
        prodServer.setDescription("Production Server");

        Contact contact = new Contact();
        contact.setName("MySwan Trading");
        contact.setEmail("support@myswan.com");

        Info info = new Info()
                .title("MySwan Trading API")
                .version("1.0")
                .description("REST API for MySwan Trading Platform - Stock, Master, and Futures data management")
                .contact(contact);

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer, prodServer));
    }
}

