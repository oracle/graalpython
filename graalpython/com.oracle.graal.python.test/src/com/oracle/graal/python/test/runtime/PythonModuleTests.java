/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.graal.python.test.runtime;

import static com.oracle.graal.python.nodes.BuiltinNames.__BUILTINS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__PACKAGE__;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class PythonModuleTests {
    private PythonContext context;

    private static class PythonModuleTestRootNode extends RootNode {
        @Child private CallNode body;

        public PythonModuleTestRootNode(PythonLanguage language, CallNode body) {
            super(language);
            this.body = body;
            Truffle.getRuntime().createCallTarget(this);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            Object[] argsWithoutSelf = new Object[arguments.length - 1];
            System.arraycopy(arguments, 1, argsWithoutSelf, 0, argsWithoutSelf.length);
            return body.execute(null, arguments[0], argsWithoutSelf);
        }
    }

    private Object callBuiltin(Object... args) {
        PythonModuleTestRootNode rootNode = new PythonModuleTestRootNode(context.getLanguage(), CallNode.create());
        return rootNode.getCallTarget().call(args);
    }

    @Before
    public void setUp() {
        PythonTests.enterContext();
        context = PythonLanguage.getContext();
    }

    @Test
    public void pythonModuleTest() {
        PythonModule module = context.getCore().factory().createPythonModule("testModule");

        assertEquals("testModule", module.getAttribute(__NAME__).toString());
        assertEquals("None", module.getAttribute(__DOC__).toString());
        assertEquals("None", module.getAttribute(__PACKAGE__).toString());
    }

    @Test
    public void builtinsMinTest() {
        final PythonModule builtins = context.getBuiltins();
        Object min = builtins.getAttribute(BuiltinNames.MIN);
        Object returnValue = callBuiltin(min, 4, 2, 1);
        assertEquals(1, returnValue);
    }

    @Test
    public void builtinsIntTest() {
        final PythonModule builtins = context.getBuiltins();
        PythonBuiltinClass intClass = (PythonBuiltinClass) builtins.getAttribute(BuiltinNames.INT);
        Object intNew = intClass.getAttribute(SpecialMethodNames.__NEW__);
        Object returnValue = callBuiltin(intNew, PythonBuiltinClassType.PInt, "42");
        assertEquals(42, returnValue);
    }

    @Test
    public void mainModuleTest() {
        PythonModule main = context.getMainModule();
        PythonModule builtins = (PythonModule) main.getAttribute(__BUILTINS__);
        PBuiltinMethod abs = (PBuiltinMethod) builtins.getAttribute(BuiltinNames.ABS);
        Object returned = callBuiltin(abs, -42);
        assertEquals(42, returned);
    }
}
