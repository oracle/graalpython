/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.test.PythonTests;

public class PythonModuleTests {
    private PythonContext context;

    @Before
    public void setUp() {
        PythonTests.enterContext();
        context = PythonLanguage.getContextRef().get();
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
        Object returnValue = CallNode.create().execute(null, min, 4, 2, 1);
        assertEquals(1, returnValue);
    }

    @Test
    public void builtinsIntTest() {
        final PythonModule builtins = context.getBuiltins();
        PythonBuiltinClass intClass = (PythonBuiltinClass) builtins.getAttribute(BuiltinNames.INT);
        Object intNew = intClass.getAttribute(SpecialAttributeNames.__NEW__);
        Object returnValue = CallNode.create().execute(null, intNew, "42");
        assertEquals(42, returnValue);
    }

    @Test
    public void mainModuleTest() {
        PythonModule main = context.getMainModule();
        PythonModule builtins = (PythonModule) main.getAttribute(__BUILTINS__);
        PBuiltinMethod abs = (PBuiltinMethod) builtins.getAttribute(BuiltinNames.ABS);
        Object returned = CallNode.create().execute(null, abs, -42);
        assertEquals(42, returned);
    }
}
