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

import org.graalvm.polyglot.*;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

public class PyNbody extends BenchRunner {

    @Param({"5000000"}) public int arg1;

    private Value combinations;
    private Value dictValues;
    private Value dictGetValue;
    private Value create;
    private Value BODIES;
    private Value BODIES_ref;
    private Value SYSTEM;
    private Value PAIRS;

    @Setup
    public void setup() {
        System.out.println("### setup ...");
        this.combinations = this.context.eval("python", //
                        "def combinations(l):\n" +
                                        "    result = []\n" +
                                        "    for x in range(len(l) - 1):\n" +
                                        "        ls = l[x+1:]\n" +
                                        "        for y in ls:\n" +
                                        "            result.append((l[x],y))\n" +
                                        "    return result\n" + //
                                        "combinations");
        this.dictValues = this.context.eval("python", //
                        "def dict_values(d):" + //
                                        "  return list(d.values())\n" + //
                                        "dict_values");
        this.dictGetValue = this.context.eval("python", //
                        "def dict_get(d, s):" + //
                                        "  return d[s]\n" + //
                                        "dict_get");
        this.create = this.context.eval("python", //
                        "PI = 3.14159265358979323\n" + //
                                        "SOLAR_MASS = 4 * PI * PI\n" + //
                                        "DAYS_PER_YEAR = 365.24\n" + //
                                        "\n" + //
                                        "BODIES = {\n" + //
                                        "    'sun': ([0.0, 0.0, 0.0], [0.0, 0.0, 0.0], SOLAR_MASS),\n" + //
                                        "\n" + //
                                        "    'jupiter': ([4.84143144246472090e+00,\n" + //
                                        "                 -1.16032004402742839e+00,\n" + //
                                        "                 -1.03622044471123109e-01],\n" + //
                                        "                [1.66007664274403694e-03 * DAYS_PER_YEAR,\n" + //
                                        "                 7.69901118419740425e-03 * DAYS_PER_YEAR,\n" + //
                                        "                 -6.90460016972063023e-05 * DAYS_PER_YEAR],\n" + //
                                        "                9.54791938424326609e-04 * SOLAR_MASS),\n" + //
                                        "\n" + //
                                        "    'saturn': ([8.34336671824457987e+00,\n" + //
                                        "                4.12479856412430479e+00,\n" + //
                                        "                -4.03523417114321381e-01],\n" + //
                                        "               [-2.76742510726862411e-03 * DAYS_PER_YEAR,\n" + //
                                        "                4.99852801234917238e-03 * DAYS_PER_YEAR,\n" + //
                                        "                2.30417297573763929e-05 * DAYS_PER_YEAR],\n" + //
                                        "               2.85885980666130812e-04 * SOLAR_MASS),\n" + //
                                        "\n" + //
                                        "    'uranus': ([1.28943695621391310e+01,\n" + //
                                        "                -1.51111514016986312e+01,\n" + //
                                        "                -2.23307578892655734e-01],\n" + //
                                        "               [2.96460137564761618e-03 * DAYS_PER_YEAR,\n" + //
                                        "                2.37847173959480950e-03 * DAYS_PER_YEAR,\n" + //
                                        "                -2.96589568540237556e-05 * DAYS_PER_YEAR],\n" + //
                                        "               4.36624404335156298e-05 * SOLAR_MASS),\n" + //
                                        "\n" + //
                                        "    'neptune': ([1.53796971148509165e+01,\n" + //
                                        "                 -2.59193146099879641e+01,\n" + //
                                        "                 1.79258772950371181e-01],\n" + //
                                        "                [2.68067772490389322e-03 * DAYS_PER_YEAR,\n" + //
                                        "                 1.62824170038242295e-03 * DAYS_PER_YEAR,\n" + //
                                        "                 -9.51592254519715870e-05 * DAYS_PER_YEAR],\n" + //
                                        "                5.15138902046611451e-05 * SOLAR_MASS) }\n" + //
                                        "def create(): return BODIES\n" + //
                                        "create");
        this.BODIES = create.execute();
        this.BODIES_ref = dictGetValue.execute(BODIES, context.asValue("sun"));
        this.SYSTEM = dictValues.execute(BODIES);
        this.PAIRS = combinations.execute(SYSTEM);
    }

    @Benchmark
    public void nbody3(@SuppressWarnings("unused") Blackhole bh) {
        nbody(arg1);
    }

    private void nbody(int n) {
        offset_momentum(BODIES_ref, SYSTEM);
        report_energy(SYSTEM, PAIRS);
        advance(0.01, n, SYSTEM, PAIRS);
        report_energy(SYSTEM, PAIRS);
    }

    private static void report_energy(Value bodies, Value pairs) {
        double e = 0.;
        double dx, dy, dz;
        for (int i = 0; i < pairs.getArraySize(); i++) {
            // (((x1, y1, z1), v1, m1), ((x2, y2, z2), v2, m2))
            Value pair1 = pairs.getArrayElement(i).getArrayElement(0);
            Value xyz1 = pair1.getArrayElement(0);
            double x1 = getd(xyz1, 0), y1 = getd(xyz1, 1), z1 = getd(xyz1, 2);
            double m1 = getd(pair1, 2);
            Value pair2 = pairs.getArrayElement(i).getArrayElement(1);
            Value xyz2 = pair2.getArrayElement(0);
            double x2 = getd(xyz2, 0), y2 = getd(xyz2, 1), z2 = getd(xyz2, 2);
            double m2 = getd(pair2, 2);
            dx = x1 - x2;
            dy = y1 - y2;
            dz = z1 - z2;
            e -= (m1 * m2) / Math.pow(dx * dx + dy * dy + dz * dz, 0.5);
        }
        for (int i = 0; i < bodies.getArraySize(); i++) {
            Value body = bodies.getArrayElement(i);
            Value v = body.getArrayElement(1);
            double vx = getd(v, 0), vy = getd(v, 1), vz = getd(v, 2);
            double m = getd(body, 2);
            e += m * (vx * vx + vy * vy + vz * vz) / 2.;
        }
        System.out.println(String.format("%.9f", e));
    }

    private static void setAdd(Value a, int i, double v) {
        a.setArrayElement(i, getd(a, i) + v);
    }

    private static void setSubtract(Value a, int i, double v) {
        a.setArrayElement(i, getd(a, i) - v);
    }

    private static void advance(double dt, int n, Value bodies, Value pairs) {
        double dx, dy, dz, mag, b1m, b2m;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < pairs.getArraySize(); j++) {
                // (([x1, y1, z1], v1, m1), ([x2, y2, z2], v2, m2))
                Value pair1 = pairs.getArrayElement(j).getArrayElement(0);
                Value xyz1 = pair1.getArrayElement(0);
                double x1 = getd(xyz1, 0), y1 = getd(xyz1, 1), z1 = getd(xyz1, 2);
                Value v1 = pair1.getArrayElement(1);
                double m1 = getd(pair1, 2);
                Value pair2 = pairs.getArrayElement(j).getArrayElement(1);
                Value xyz2 = pair2.getArrayElement(0);
                double x2 = getd(xyz2, 0), y2 = getd(xyz2, 1), z2 = getd(xyz2, 2);
                Value v2 = pair2.getArrayElement(1);
                double m2 = getd(pair2, 2);
                dx = x1 - x2;
                dy = y1 - y2;
                dz = z1 - z2;
                mag = dt * Math.pow(dx * dx + dy * dy + dz * dz, -1.5);
                b1m = m1 * mag;
                b2m = m2 * mag;
                setSubtract(v1, 0, dx * b2m);
                setSubtract(v1, 1, dy * b2m);
                setSubtract(v1, 2, dz * b2m);
                setAdd(v2, 0, dx * b1m);
                setAdd(v2, 1, dy * b1m);
                setAdd(v2, 2, dz * b1m);
            }
            for (int j = 0; j < bodies.getArraySize(); j++) {
                // (r, [vx, vy, vz], m)
                Value body = bodies.getArrayElement(j);
                Value r = body.getArrayElement(0);
                Value v = body.getArrayElement(1);
                double vx = getd(v, 0), vy = getd(v, 1), vz = getd(v, 2);
                setAdd(r, 0, dt * vx);
                setAdd(r, 1, dt * vy);
                setAdd(r, 2, dt * vz);
            }
        }

    }

    private static void offset_momentum(Value ref, Value bodies) {
        double px = 0.0, py = 0.0, pz = 0.0;
        for (int j = 0; j < bodies.getArraySize(); j++) {
            // (r, [vx, vy, vz], m)
            Value body = bodies.getArrayElement(j);
            Value v = body.getArrayElement(1);
            double vx = getd(v, 0), vy = getd(v, 1), vz = getd(v, 2);
            double m = getd(body, 2);
            px -= vx * m;
            py -= vy * m;
            pz -= vz * m;
        }
        // (r, v, m)
        Value v = ref.getArrayElement(1);
        double m = getd(ref, 2);
        setd(v, 0, px / m);
        setd(v, 1, py / m);
        setd(v, 2, pz / m);
    }
}
