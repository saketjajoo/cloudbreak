package com.sequenceiq.periscope.swagger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.sequenceiq.periscope.api.AutoscaleApi;
import com.sequenceiq.periscope.config.EndpointConfig;

import io.swagger.jaxrs.Reader;
import io.swagger.jaxrs.config.SwaggerConfigLocator;
import io.swagger.jaxrs.config.SwaggerContextService;
import io.swagger.models.Swagger;
import io.swagger.util.Json;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = EndpointConfig.class)
@TestPropertySource(locations = "file:./build/resources/main/application.properties")
public class SwaggerGenerator {

    @Autowired
    private EndpointConfig endpointConfig;

    @Test
    public void generateSwaggerJson() throws Exception {
        Set<Class<?>> classes = new HashSet<>(endpointConfig.getClasses());
        classes.add(AutoscaleApi.class);
        Swagger swagger = new Reader(SwaggerConfigLocator.getInstance().getConfig(SwaggerContextService.CONFIG_ID_DEFAULT).configure(new Swagger()))
                .read(classes);
        Path path = Paths.get("./build/swagger/autoscale.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, Json.pretty(swagger));
    }

}
