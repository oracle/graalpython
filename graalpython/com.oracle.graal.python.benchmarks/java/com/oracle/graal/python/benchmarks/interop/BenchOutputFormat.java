/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.benchmarks.interop;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.format.OutputFormat;
import org.openjdk.jmh.runner.options.VerboseMode;
import org.openjdk.jmh.util.Utils;

public class BenchOutputFormat implements OutputFormat {

    final VerboseMode verbose;
    final PrintStream out;

    final String name;

    final HashMap<String, ArrayList<Double>> raws;

    public BenchOutputFormat(PrintStream out, VerboseMode verbose, String name) {
        this.out = out;
        this.verbose = verbose;
        this.raws = new HashMap<>();
        this.name = name;
    }

    public BenchOutputFormat(PrintStream out, VerboseMode verbose) {
        this(out, verbose, null);
    }

    private void add(BenchmarkParams params, double value) {
        if (!raws.containsKey(params.getBenchmark())) {
            raws.put(params.getBenchmark(), new ArrayList<>());
        }
        raws.get(params.getBenchmark()).add(value);
    }

    protected String benchName(BenchmarkParams params) {
        if (name != null) {
            return name;
        }
        return params.getBenchmark();
    }

    @Override
    public void startBenchmark(BenchmarkParams params) {
        String opts = Utils.join(params.getJvmArgs(), " ");
        if (opts.trim().isEmpty()) {
            opts = "<none>";
        }

        println("# JMH version: " + params.getJmhVersion());
        println("# VM version: JDK " + params.getJdkVersion() + ", " + params.getVmName() + ", " + params.getVmVersion());

        println("# VM invoker: " + params.getJvm());
        println("# VM options: " + opts);
        println("# Benchmark mode: " + params.getMode().longLabel());

        hline();
        String benchName = benchName(params);
        IterationParams warmup = params.getWarmup();
        IterationParams measurement = params.getMeasurement();
        String info = "### %s, %d warmup iterations, %d bench iterations";
        println(String.format(info, benchName, warmup.getCount(), measurement.getCount()));
        String s = "";
        boolean isFirst = true;
        for (String k : params.getParamsKeys()) {
            if (isFirst) {
                isFirst = false;
            } else {
                s += ", ";
            }
            s += k + " = " + params.getParam(k);
        }

        println(String.format("### args =  [%s]", s));
        hline();
        println("### start benchmark ...");
    }

    @Override
    public void iteration(BenchmarkParams benchmarkParams, IterationParams params, int iteration) {
    }

    @Override
    public void iterationResult(BenchmarkParams benchmParams, IterationParams params, int iteration, IterationResult data) {
        double value = data.getPrimaryResult().getScore();
        add(benchmParams, value);
        String benchName = benchName(benchmParams);
        switch (params.getType()) {
            case WARMUP:
                out.println(String.format("### (pre)warming up for %s iteration=%d, duration=%.3f", benchName, iteration, value));
                break;
            case MEASUREMENT:
                out.println(String.format("### iteration=%d, name=%s, duration=%.3f", iteration, benchName, value));
                break;
            default:
                throw new IllegalStateException("Unknown iteration type: " + params.getType());
        }
    }

    @Override
    public void endBenchmark(BenchmarkResult result) {
        final ArrayList<Double> raw = raws.get(result.getParams().getBenchmark());
        final double[] durations = new double[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            durations[i] = raw.get(i);
        }
        hline();
        println("### teardown ...");
        println("### benchmark complete");
        hline();
        double best = min(durations);
        println(String.format("### BEST                duration: %.3f s", best));
        double worst = max(durations);
        println(String.format("### WORST               duration: %.3f s", worst));
        double avg = avg(durations);
        println(String.format("### AVG (all runs)      duration: %.3f s", avg));
        int warmupIndex = detect_warmup(durations);
        println(String.format("### WARMUP detected at iteration: %d", warmupIndex));
        double avg2 = avg(Arrays.copyOfRange(durations, Math.min(durations.length - 1, warmupIndex + 1), durations.length));
        println(String.format("### AVG (no warmup)     duration: %.3f s", avg2));
        hline();
        String s = "";
        for (double d : durations) {
            s += d + ", ";
        }
        println(String.format("### RAW DURATIONS: [%s]", s));
        hline();
    }

    private static int[] cusum(double[] values, double threshold) {
        // double threshold=1.0;
        double drift = 0.0;
        int size = values.length;
        double[] csum_pos = new double[size];
        double[] csum_neg = new double[size];
        int[] change_points = new int[size];
        int cp_idx = -1;
        for (int i = 1; i < size; i++) {
            double diff = values[i] - values[i - 1];
            csum_pos[i] = csum_pos[i - 1] + diff - drift;
            csum_neg[i] = csum_neg[i - 1] - diff - drift;

            if (csum_pos[i] < 0) {
                csum_pos[i] = 0;
            }
            if (csum_neg[i] < 0) {
                csum_neg[i] = 0;
            }

            if (csum_pos[i] > threshold || csum_neg[i] > threshold) {
                cp_idx++;
                change_points[cp_idx] = i;
                csum_pos[i] = 0.;
                csum_neg[i] = 0.;
            }
        }

        return Arrays.copyOf(change_points, cp_idx + 1);

    }

    private static double avg(double[] values) {
        return Arrays.stream(values).average().getAsDouble();
    }

    private static double min(double[] values) {
        return Arrays.stream(values).min().getAsDouble();
    }

    private static double max(double[] values) {
        return Arrays.stream(values).max().getAsDouble();
    }

    private static double[] norm(double[] values) {
        double min = min(values);
        double max = max(values);
        return Arrays.stream(values).map(v -> (v - min) / (max - min) * 100.0).toArray();
    }

    private static double[] pairwise_slopes(double[] values, int[] cp) {
        double[] copy = Arrays.copyOf(values, values.length - 1);
        for (int i = 0; i < copy.length; i++) {
            copy[i] = Math.abs((values[i + 1] - values[i]) / (cp[i + 1] - cp[i]));
        }
        return copy;
    }

    private static double[] last_n_percent_runs(double[] values, double n) {
        int size = values.length;
        int newSize = size - (int) (size * n);
        if (newSize >= size) {
            newSize = size - 1;
        }
        return Arrays.copyOfRange(values, newSize, size);
    }

    private static int warmup(int idx, int[] cp, int max) {
        return cp[idx] < max ? cp[idx] : -1;
    }

    private static int detect_warmup(double[] durations) {
        double cp_threshold = 0.03, stability_slope_grade = 0.01;
        stability_slope_grade *= 100.0;
        cp_threshold *= 100;
        double[] values = norm(durations);
        int size = values.length;
        int[] cp = cusum(values, cp_threshold);
        double[] rolling_avg = new double[cp.length];
        for (int i = 0; i < cp.length; i++) {
            // [avg(values[i:]) for i in cp]
            rolling_avg[i] = avg(Arrays.copyOfRange(values, cp[i], values.length));
        }

        // find the point where the duration avg is below the cp threshold
        for (int i = 0; i < rolling_avg.length; i++) {
            if (rolling_avg[i] <= cp_threshold) {
                return warmup(i, cp, size);
            }
        }

        // could not find something below the CP threshold (noise in the data), use the
        // stabilisation of slopes
        double n = 0.1;
        double[] last_n_vals = last_n_percent_runs(values, n);
        int last_n_idx = size - (int) (size * n);
        int totalSize = cp.length + last_n_vals.length;
        double[] rolling_avg2 = new double[totalSize];
        int[] cp2 = new int[totalSize];
        for (int i = 0; i < totalSize; i++) {
            if (i < cp.length) {
                rolling_avg2[i] = rolling_avg[i];
                cp2[i] = cp[i];
            } else {
                int j = i - cp.length;
                rolling_avg2[i] = last_n_vals[j];
                cp2[i] = last_n_idx++;
            }
        }
        double[] slopes = pairwise_slopes(rolling_avg2, cp2);

        for (int i = 0; i < slopes.length; i++) {
            if (slopes[i] <= stability_slope_grade) {
                return warmup(i, cp, size);
            }
        }

        return -1;
    }

    private void hline() {
        println("-------------------------------------------------------------------------------");
    }

    @Override
    public void print(String s) {
        out.print(s);
    }

    @Override
    public void println(String s) {
        out.println(s);
    }

    @Override
    public void flush() {
        out.flush();
    }

    @Override
    public void verbosePrintln(String s) {
        if (verbose == VerboseMode.EXTRA) {
            out.println(s);
        }
    }

    @Override
    public void write(int b) {
        out.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        out.write(b);
    }

    @Override
    public void close() {
    }

    @Override
    public void startRun() {
    }

    @Override
    public void endRun(Collection<RunResult> runResults) {
    }

}
