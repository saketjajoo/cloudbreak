package com.sequenceiq.datalake.configuration;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.sequenceiq.cloudbreak.client.internal.CloudbreakApiClientParams;
import com.sequenceiq.cloudbreak.concurrent.TracingAndMdcCopyingTaskDecorator;
import com.sequenceiq.environment.client.internal.EnvironmentApiClientParams;
import com.sequenceiq.freeipa.api.client.internal.FreeIpaApiClientParams;
import com.sequenceiq.redbeams.client.internal.RedbeamsApiClientParams;

import io.opentracing.Tracer;

@Configuration
@EnableAsync
@EnableScheduling
@EnableRetry
public class AppConfig implements AsyncConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfig.class);

    @Inject
    @Named("cloudbreakUrl")
    private String cloudbreakUrl;

    @Inject
    @Named("environmentServerUrl")
    private String environmentServerUrl;

    @Inject
    @Named("redbeamsServerUrl")
    private String redbeamsServerUrl;

    @Value("${rest.debug:false}")
    private boolean restDebug;

    @Value("${cert.validation:true}")
    private boolean certificateValidation;

    @Value("${cert.ignorePreValidation:true}")
    private boolean ignorePreValidation;

    @Inject
    @Named("freeIpaServerUrl")
    private String freeIpaServerUrl;

    @Value("${datalake.intermediate.threadpool.core.size}")
    private int intermediateCorePoolSize;

    @Value("${datalake.intermediate.threadpool.capacity.size}")
    private int intermediateQueueCapacity;

    @Inject
    private Tracer tracer;

    @Bean
    public CloudbreakApiClientParams cloudbreakApiClientParams() {
        return new CloudbreakApiClientParams(restDebug, certificateValidation, ignorePreValidation, cloudbreakUrl);
    }

    @Bean
    public EnvironmentApiClientParams environmentApiClientParams() {
        return new EnvironmentApiClientParams(restDebug, certificateValidation, ignorePreValidation, environmentServerUrl);
    }

    @Bean
    public RedbeamsApiClientParams redbeamsApiClientParams() {
        return new RedbeamsApiClientParams(restDebug, certificateValidation, ignorePreValidation, redbeamsServerUrl);
    }

    @Bean
    public FreeIpaApiClientParams freeIpaApiClientParams() {
        return new FreeIpaApiClientParams(restDebug, certificateValidation, ignorePreValidation, freeIpaServerUrl);
    }

    @Bean
    @Primary
    public AsyncTaskExecutor intermediateBuilderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(intermediateCorePoolSize);
        executor.setQueueCapacity(intermediateQueueCapacity);
        executor.setThreadNamePrefix("intermediateBuilderExecutor-");
        executor.setTaskDecorator(new TracingAndMdcCopyingTaskDecorator(tracer));
        executor.initialize();
        return executor;
    }
}
