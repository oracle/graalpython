/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import org.junit.internal.JUnitSystem;
import org.junit.internal.RealSystem;
import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;

import junit.runner.Version;

public class GraalPythonJUnitRunner {

    public static void main(String[] args) {
        if (args.length == 0 || args.length % 2 != 0 || (!args[0].contentEquals("--testsfile") && !args[0].contentEquals("--testclass"))) {
            System.err.println("usage: [--testsfile file] [--testclass class]");
            System.exit(1);
        }

        String testsFile = args[0].contentEquals("--testsfile") ? args[1] : null;
        String testClass = args[0].contentEquals("--testclass") ? args[1] : null;

        Class<?>[] classes = null;
        try {
            if (testClass != null) {
                classes = new Class<?>[1];
                classes[0] = Class.forName(testClass);

            } else { // testsFile != null
                ArrayList<Class<?>> tests = new ArrayList<>();
                try (BufferedReader r = new BufferedReader(new FileReader(testsFile))) {
                    while ((testClass = r.readLine()) != null) {
                        tests.add(Class.forName(testClass));
                    }
                    classes = new Class<?>[tests.size()];
                    tests.toArray(classes);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    System.exit(2);
                }
            }
        } catch (ClassNotFoundException ex) {
            System.err.println("No class named: " + testClass);
        }

        if (classes == null) {
            System.err.println("No junit classes specified!");
            System.exit(1);
        }

        System.out.println("JUnit version " + Version.id());
        if (classes.length == 1) {
            System.out.println("testing " + classes[0] + " junit class..");
        } else {
            System.out.println("testing " + classes.length + " junit classes..");
        }

        JUnitSystem system = new RealSystem();
        JUnitCore core = new JUnitCore();
        core.addListener(new TextListener(system));
        System.exit(core.run(classes).wasSuccessful() ? 0 : 1);
    }

}
