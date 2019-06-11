/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.datamodel;

import com.oracle.graal.python.nodes.NodeContextManager;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithGlobalState;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.datamodel.PDataModelEmulationNode.PDataModelEmulationContextManager;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.frame.VirtualFrame;

@ImportStatic({PGuards.class, SpecialMethodNames.class})
public abstract class PDataModelEmulationNode extends PNodeWithGlobalState<PDataModelEmulationContextManager> {

    protected abstract boolean execute(Object object);

    @Override
    public PDataModelEmulationContextManager withGlobalState(ContextReference<PythonContext> contextRef, VirtualFrame frame) {
        return new PDataModelEmulationContextManager(this, contextRef.get(), frame);
    }

    @Override
    public PDataModelEmulationContextManager passState() {
        return new PDataModelEmulationContextManager(this, null, null);
    }

    public static boolean check(PDataModelEmulationNode isMapping, ContextReference<PythonContext> contextRef, VirtualFrame frame, Object obj) {
        try (PDataModelEmulationContextManager ctxManager = isMapping.withGlobalState(contextRef, frame)) {
            return ctxManager.execute(obj);
        }
    }

    public static final class PDataModelEmulationContextManager extends NodeContextManager {

        private final PDataModelEmulationNode delegate;

        public PDataModelEmulationContextManager(PDataModelEmulationNode delegate, PythonContext context, VirtualFrame frame) {
            super(context, frame, delegate);
            this.delegate = delegate;
        }

        public boolean execute(Object object) {
            return delegate.execute(object);
        }
    }
}
