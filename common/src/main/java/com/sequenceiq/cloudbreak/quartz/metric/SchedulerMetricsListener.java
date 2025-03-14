package com.sequenceiq.cloudbreak.quartz.metric;

import java.util.List;

import javax.inject.Inject;

import org.quartz.SchedulerException;
import org.quartz.listeners.SchedulerListenerSupport;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.common.metrics.MetricService;

@Component
public class SchedulerMetricsListener extends SchedulerListenerSupport {

    @Inject
    private List<MetricService> metricServices;

    @Override
    public void schedulerError(String msg, SchedulerException cause) {
        getLog().warn("Scheduler error occured: {}", msg, cause);
        metricServices.forEach(metricService -> metricService.incrementMetricCounter(QuartzMetricType.SCHEDULER_ERROR));
    }

    @Override
    public void schedulingDataCleared() {
        getLog().debug("Scheduling data cleared!");
    }
}
