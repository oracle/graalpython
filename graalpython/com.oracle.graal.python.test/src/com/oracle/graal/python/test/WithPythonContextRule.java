/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test;

import java.util.HashMap;
import java.util.Map;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.Consumer;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class WithPythonContextRule implements MethodRule {

    private Map<String, String> options;
    private Consumer<Map<String, String>> optionsProvider;
    private PythonContext pythonContext;

    public WithPythonContextRule(Map<String, String> options) {
        this.options = options;
    }

    public WithPythonContextRule(Consumer<Map<String, String>> optionsProvider) {
        this.optionsProvider = optionsProvider;
    }

    public PythonContext getPythonContext() {
        return pythonContext;
    }

    private Map<String, String> getOptions() {
        if (options != null) {
            return options;
        }
        Map<String, String> opt = new HashMap<>();
        if (optionsProvider != null) {
            optionsProvider.accept(opt);
        }
        return opt;
    }

    public Statement apply(final Statement base, FrameworkMethod method, Object target) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                PythonTests.enterContext(getOptions(), new String[0]);

                RootCallTarget callTarget = new RootNode(null) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        pythonContext = PythonContext.get(this);
                        try {
                            base.evaluate();
                        } catch (Throwable e) {
                            return e;
                        }
                        return null;
                    }
                }.getCallTarget();
                Throwable result = (Throwable) callTarget.call();
                PythonTests.closeContext();
                if (result != null) {
                    throw result;
                }
            }
        };
    }
}
