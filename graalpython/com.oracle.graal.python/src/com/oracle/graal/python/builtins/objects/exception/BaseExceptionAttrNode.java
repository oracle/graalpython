/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic(PGuards.class)
@GenerateUncached
public abstract class BaseExceptionAttrNode extends Node {
    public interface StorageFactory {
        Object[] create(Object[] args, PythonObjectFactory factory);

        default Object[] create(Object[] args) {
            return create(args, null);
        }

        default Object[] create() {
            return create(null);
        }
    }

    public final Object get(PBaseException self, int index, StorageFactory factory) {
        return execute(self, PNone.NO_VALUE, index, factory);
    }

    public final int getInt(PBaseException self, int index, StorageFactory factory) {
        final Object val = execute(self, PNone.NO_VALUE, index, factory);
        assert val instanceof Integer : "expected PBaseException attribute to be an integer";
        return (int) val;
    }

    public final Object set(PBaseException self, Object value, int index, StorageFactory factory) {
        return execute(self, value, index, factory);
    }

    public abstract Object execute(PBaseException self, Object value, int index, StorageFactory factory);

    protected static boolean withAttributes(PBaseException self) {
        return self.getExceptionAttributes() != null;
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class EnsureAttrStorageNode extends Node {
        abstract Object[] execute(Node inliningTarget, PBaseException self, StorageFactory storageFactory);

        @Specialization
        static Object[] ensure(Node inliningTarget, PBaseException self, StorageFactory storageFactory,
                        @Cached ExceptionNodes.GetArgsNode getArgsNode,
                        @Cached SequenceStorageNodes.GetInternalObjectArrayNode getInternalObjectArrayNode,
                        @Cached PythonObjectFactory factory) {
            Object[] attributes = self.getExceptionAttributes();
            if (attributes == null) {
                PTuple argsTuple = getArgsNode.execute(inliningTarget, self);
                Object[] args = getInternalObjectArrayNode.execute(argsTuple.getSequenceStorage());
                attributes = storageFactory.create(args, factory);
                self.setExceptionAttributes(attributes);
            }
            return attributes;
        }
    }

    // GET
    @Specialization(guards = {"isNoValue(none)", "withAttributes(self)"})
    public Object getAttrWithStorage(PBaseException self, @SuppressWarnings("unused") PNone none, int index, @SuppressWarnings("unused") StorageFactory factory) {
        Object[] attributes = self.getExceptionAttributes();
        assert index >= 0 && index < attributes.length : "PBaseException attribute index is out of range";
        final Object value = attributes[index];
        return value != null ? value : PNone.NONE;
    }

    @Specialization(guards = {"isNoValue(none)", "!withAttributes(self)"})
    public Object getAttrNoStorage(PBaseException self, @SuppressWarnings("unused") PNone none, int index, StorageFactory factory,
                    @Bind("this") Node inliningTarget,
                    @Shared @Cached EnsureAttrStorageNode ensureAttrStorageNode) {
        Object[] attributes = ensureAttrStorageNode.execute(inliningTarget, self, factory);
        assert attributes != null : "PBaseException attributes field is null";
        return getAttrWithStorage(self, none, index, factory);
    }

    // SET
    @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)", "withAttributes(self)"})
    public Object setAttrWithStorage(PBaseException self, Object value, int index, @SuppressWarnings("unused") StorageFactory factory) {
        Object[] attributes = self.getExceptionAttributes();
        assert index >= 0 && index < attributes.length : "PBaseException attribute index is out of range";
        attributes[index] = value;
        return PNone.NONE;
    }

    @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)", "!withAttributes(self)"})
    public Object setAttrNoStorage(PBaseException self, Object value, int index, StorageFactory factory,
                    @Bind("this") Node inliningTarget,
                    @Shared @Cached EnsureAttrStorageNode ensureAttrStorageNode) {
        Object[] attributes = ensureAttrStorageNode.execute(inliningTarget, self, factory);
        assert attributes != null : "PBaseException attributes field is null";
        return setAttrWithStorage(self, value, index, factory);
    }

    // DEL
    @Specialization(guards = {"!isNoValue(value)", "isDeleteMarker(value)", "withAttributes(self)"})
    public Object delAttrWithStorage(PBaseException self, @SuppressWarnings("unused") Object value, int index, @SuppressWarnings("unused") StorageFactory factory) {
        Object[] attributes = self.getExceptionAttributes();
        assert index >= 0 && index < attributes.length : "PBaseException attribute index is out of range";
        attributes[index] = null;
        return PNone.NONE;
    }

    @Specialization(guards = {"!isNoValue(value)", "isDeleteMarker(value)", "!withAttributes(self)"})
    public Object delAttrNoStorage(PBaseException self, Object value, int index, StorageFactory factory,
                    @Bind("this") Node inliningTarget,
                    @Shared @Cached EnsureAttrStorageNode ensureAttrStorageNode) {
        Object[] attributes = ensureAttrStorageNode.execute(inliningTarget, self, factory);
        assert attributes != null : "PBaseException attributes field is null";
        return delAttrWithStorage(self, value, index, factory);
    }
}
