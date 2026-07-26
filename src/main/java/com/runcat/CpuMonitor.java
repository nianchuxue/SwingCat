package com.runcat;


import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CpuMonitor {
    private final OperatingSystemMXBean bean;
    private double currentCpu = 0;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public CpuMonitor() {
        bean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        startMonitoring();
    }

    private void startMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            double cpu = bean.getCpuLoad() * 100;
            if (cpu >= 0) {
                currentCpu = cpu;
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    public double getCpu() {
        return currentCpu;
    }

    public void stop() {
        scheduler.shutdown();
    }
}


