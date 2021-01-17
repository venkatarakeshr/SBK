/**
 * Copyright (c) KMG. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.sbk.api.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsFactory;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusRenameFilter;
import io.sbk.api.Action;
import io.sbk.api.MetricsConfig;
import io.sbk.api.Parameters;
import io.sbk.api.Print;
import io.sbk.api.TimeUnit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;


/**
 * Class for Recoding/Printing benchmark results on micrometer Composite Meter Registry.
 */
public class PrometheusLogger extends SystemLogger {
    final static String CONFIGFILE = "metrics.properties";
    private MetricsConfig config;
    private boolean disabled;
    private double[] percentilesIndices;
    private MetricsLogger metricsLogger;
    private Print printer;

    public PrometheusLogger() {
        super();
    }

    @Override
    public void addArgs(final Parameters params) throws IllegalArgumentException {
        super.addArgs(params);
        final ObjectMapper mapper = new ObjectMapper(new JavaPropsFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            config = mapper.readValue(io.sbk.api.impl.Sbk.class.getClassLoader().getResourceAsStream(CONFIGFILE),
                    MetricsConfig.class);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new IllegalArgumentException(ex);
        }

        String[] percentilesList = config.percentiles.split(",");
        percentilesIndices = new double[percentilesList.length];
        for (int i = 0; i < percentilesList.length; i++) {
            percentilesIndices[i] = Double.parseDouble(percentilesList[i].trim());
        }
        Arrays.sort(percentilesIndices);

        params.addOption("context", true, "Prometheus Metric context;" +
                "default context: " + config.port + config.context + "; 'no' disables the metrics");

    }

    @Override
    public void parseArgs(final Parameters params) throws IllegalArgumentException {
        super.parseArgs(params);
        final String fullContext =  params.getOptionValue("context", config.port + config.context);
        if (fullContext.equalsIgnoreCase("no")) {
            disabled = true;
        } else {
            disabled = false;
            String[] str = fullContext.split("/", 2);
            config.port = Integer.parseInt(str[0]);
            if (str.length == 2 && str[1] != null) {
                config.context = "/" + str[1];
            }
        }

    }

    public MeterRegistry createPrometheusRegistry() throws IOException {
        final PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        prometheusRegistry.config().meterFilter(new PrometheusRenameFilter());
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port), 0);
        server.createContext(config.context, httpExchange -> {
            String response = prometheusRegistry.scrape();
            httpExchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = httpExchange.getResponseBody()) {
                    os.write(response.getBytes());
            }
        });
        new Thread(server::start).start();
        return prometheusRegistry;
    }

    @Override
    public int getReportingIntervalSeconds() {
        return config.reportingSeconds;
    }

    @Override
    public TimeUnit getTimeUnit() {
        return config.timeUnit;
    }

    @Override
    public int getMinLatency() {
        return config.minLatency;
    }

    @Override
    public int getMaxWindowLatency() {
        return config.maxWindowLatency;
    }

    @Override
    public int getMaxLatency() {
        return config.maxLatency;
    }

    @Override
    public double[] getPercentileIndices() {
        return percentilesIndices;
    }


    @Override
    public void open(final Parameters params, final String storageName, Action action) throws IllegalArgumentException, IOException {
        super.open(params, storageName, action);
        if (disabled) {
            printer = super::print;
        } else {
            final CompositeMeterRegistry compositeRegistry = Metrics.globalRegistry;
            compositeRegistry.add(new JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM));
            compositeRegistry.add(createPrometheusRegistry());
            metricsLogger = new MetricsLogger(storageName, action.name(), timeUnit, percentiles,
                    params.getWritersCount(), params.getReadersCount(), compositeRegistry);
            printer = this::printMetrics;
        }
    }

    @Override
    public void close(final Parameters params) throws IllegalArgumentException, IOException  {
        if (metricsLogger != null) {
            metricsLogger.close();
        }
        super.close(params);
    }

    private void printMetrics(long bytes, long records, double recsPerSec, double mbPerSec, double avgLatency, int maxLatency,
                      long lowerDiscard, long higherDiscard, int[] percentileValues) {
        super.print( bytes, records, recsPerSec, mbPerSec, avgLatency, maxLatency,
                lowerDiscard, higherDiscard, percentileValues);
        metricsLogger.print( bytes, records, recsPerSec, mbPerSec, avgLatency, maxLatency,
                lowerDiscard, higherDiscard, percentileValues);
    }

    @Override
    public void print(long bytes, long records, double recsPerSec, double mbPerSec, double avgLatency, int maxLatency,
               long lowerDiscard, long higherDiscard, int[] percentileValues) {
        printer.print(bytes, records, recsPerSec, mbPerSec, avgLatency, maxLatency,
                lowerDiscard, higherDiscard, percentileValues);
    }
}