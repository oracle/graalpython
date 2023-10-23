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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.CField;
import static com.oracle.graal.python.nodes.ErrorMessages.ABSTRACT_CLASS;
import static com.oracle.graal.python.nodes.ErrorMessages.ANONYMOUS_MUST_BE_A_SEQUENCE;
import static com.oracle.graal.python.nodes.ErrorMessages.FIELDS_MUST_BE_A_SEQUENCE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_IS_SPECIFIED_IN_ANONYMOUS_BUT_NOT_IN_FIELDS;
import static com.oracle.graal.python.nodes.ErrorMessages.UNEXPECTED_TYPE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SIZEOF__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltinsFactory.PyTypeStgDictNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PySequenceCheckNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetAnyAttributeNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.StgDict)
public final class StgDictBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StgDictBuiltinsFactory.getFactories();
    }

    protected static final TruffleString T__ANONYMOUS_ = tsLiteral("_anonymous_");

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class InitNode extends PythonBuiltinNode {

        @Specialization
        Object init(VirtualFrame frame, StgDictObject self, Object[] args, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached CallNode callNode) {
            Object initMethod = lookup.execute(frame, inliningTarget, PythonBuiltinClassType.PDict, SpecialMethodNames.T___INIT__);
            Object[] dictArgs;
            if (args.length > 0) {
                dictArgs = new Object[args.length + 1];
                dictArgs[0] = self;
                PythonUtils.arraycopy(args, 0, dictArgs, 1, args.length);
            } else {
                dictArgs = new Object[]{self};
            }
            callNode.execute(frame, initMethod, dictArgs, kwargs);
            self.format = null;
            self.ndim = 0;
            self.shape = null;
            return PNone.NONE;
        }
    }

    @Builtin(name = J___SIZEOF__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class SizeOfNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object doit(VirtualFrame frame, StgDictObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetDictIfExistsNode getDict,
                        @Cached ObjectBuiltins.SizeOfNode sizeOfNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode) {
            long size = asSizeNode.executeLossy(frame, inliningTarget, sizeOfNode.execute(frame, getDict.execute(self)));
            // size += sizeof(StgDictObject) - sizeof(PyDictObject);
            if (self.format != null) {
                size += codePointLengthNode.execute(self.format, TS_ENCODING) + 1;
            }
            size += self.ndim * Integer.BYTES;
            if (self.ffi_type_pointer.elements != null) {
                size += self.ffi_type_pointer.elements.length * FFIType.typeSize();
            }
            return size;
        }
    }

    @ImportStatic(StructUnionTypeBuiltins.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 112 -> 94
    protected abstract static class MakeFieldsNode extends PNodeWithContext {

        abstract void execute(VirtualFrame frame, Object type, CFieldObject descr, int index, int offset, PythonObjectFactory factory);

        /*
         * descr is the descriptor for a field marked as anonymous. Get all the _fields_ descriptors
         * from descr.proto, create new descriptors with offset and index adjusted, and stuff them
         * into type.
         */
        @Specialization
        static void MakeFields(VirtualFrame frame, Object type, CFieldObject descr, int index, int offset, PythonObjectFactory factory,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached GetAnyAttributeNode getAttributeNode,
                        @Cached SetAttributeNode.Dynamic setAttributeNode,
                        @Cached PySequenceCheckNode sequenceCheckNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectGetItem getItemNode,
                        @Cached GetInternalObjectArrayNode getArray,
                        @Cached("create(T__FIELDS_)") GetAttributeNode getAttrString,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object fields = getAttrString.executeObject(frame, descr.proto);
            if (!sequenceCheckNode.execute(inliningTarget, fields)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, FIELDS_MUST_BE_A_SEQUENCE);
            }

            PythonContext context = PythonContext.get(inliningTarget);
            for (int i = 0; i < sizeNode.execute(frame, inliningTarget, fields); ++i) {
                PTuple pair = (PTuple) getItemNode.execute(frame, inliningTarget, fields, i); /*
                                                                                               * borrowed
                                                                                               */
                /* Convert to PyArg_UnpackTuple... */
                // PyArg_ParseTuple(pair, "OO|O", & fname, &ftype, &bits);
                Object[] array = getArray.execute(inliningTarget, pair.getSequenceStorage());
                Object fname = array[0];
                CFieldObject fdescr = (CFieldObject) getAttributeNode.executeObject(frame, descr.proto, fname);
                if (getClassNode.execute(inliningTarget, fdescr) != context.lookupType(CField)) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, UNEXPECTED_TYPE);
                }
                if (fdescr.anonymous != 0) {
                    MakeFields(frame, type, fdescr, index + fdescr.index, offset + fdescr.offset, factory,
                                    inliningTarget, getClassNode, getAttributeNode, setAttributeNode,
                                    sequenceCheckNode, sizeNode, getItemNode, getArray, getAttrString, raiseNode);
                    continue;
                }
                CFieldObject new_descr = factory.createCFieldObject(CField);
                // assert (Py_TYPE(new_descr) == PythonBuiltinClassType.CField);
                new_descr.size = fdescr.size;
                new_descr.offset = fdescr.offset + offset;
                new_descr.index = fdescr.index + index;
                new_descr.proto = fdescr.proto;
                new_descr.getfunc = fdescr.getfunc;
                new_descr.setfunc = fdescr.setfunc;

                setAttributeNode.execute(frame, type, fname, new_descr);
            }
        }

    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    protected abstract static class PyTypeStgDictNode extends Node {

        abstract StgDictObject execute(Node inliningTarget, Object type);

        static StgDictObject executeUncached(Object type) {
            return PyTypeStgDictNodeGen.getUncached().execute(null, type);
        }

        protected StgDictObject checkAbstractClass(Node inliningTarget, Object type, PRaiseNode.Lazy raiseNode) {
            StgDictObject dict = execute(inliningTarget, type);
            if (dict == null) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ABSTRACT_CLASS);
            }
            return dict;
        }

        /* May return NULL, but does not set an exception! */
        @Specialization
        static StgDictObject PyType_stgdict(Node inliningTarget, Object obj,
                        @Cached IsTypeNode isTypeNode,
                        @Cached(inline = false) GetDictIfExistsNode getDict) {
            if (!isTypeNode.execute(inliningTarget, obj)) {
                return null;
            }
            PDict dict = getDict.execute(obj);
            if (!PGuards.isStgDict(dict)) {
                return null;
            }
            return (StgDictObject) dict;
        }
    }

    /*
     * This function should be as fast as possible, so we don't call PyType_stgdict above but inline
     * the code, and avoid the PyType_Check().
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    protected abstract static class PyObjectStgDictNode extends Node {

        abstract StgDictObject execute(Node inliningTarget, Object type);

        /* May return null, but does not raise an exception! */
        @Specialization
        static StgDictObject PyObject_stgdict(Node inliningTarget, Object self,
                        @Cached GetClassNode getType,
                        @Cached(inline = false) GetDictIfExistsNode getDict) {
            Object type = getType.execute(inliningTarget, self);
            PDict dict = getDict.execute(type);
            if (!PGuards.isStgDict(dict)) {
                return null;
            }
            return (StgDictObject) dict;
        }
    }

    @ImportStatic(StgDictBuiltins.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 132 -> 115
    protected abstract static class MakeAnonFieldsNode extends Node {

        abstract void execute(VirtualFrame frame, Object type, PythonObjectFactory factory);

        /*
         * Iterate over the names in the type's _anonymous_ attribute, if present,
         */
        @Specialization
        static void MakeAnonFields(VirtualFrame frame, Object type, PythonObjectFactory factory,
                        @Bind("this") Node inliningTarget,
                        @Cached PySequenceCheckNode sequenceCheckNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectGetItem getItemNode,
                        @Cached MakeFieldsNode makeFieldsNode,
                        @Cached GetClassNode getClassNode,
                        @Cached GetAnyAttributeNode getAttr,
                        @Cached PyObjectLookupAttr lookupAnon,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object anon = lookupAnon.execute(frame, inliningTarget, type, T__ANONYMOUS_);
            if (PGuards.isPNone(anon)) {
                return;
            }
            if (!sequenceCheckNode.execute(inliningTarget, anon)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ANONYMOUS_MUST_BE_A_SEQUENCE);
            }

            for (int i = 0; i < sizeNode.execute(frame, inliningTarget, anon); ++i) {
                Object fname = getItemNode.execute(frame, inliningTarget, anon, i); /* borrowed */
                CFieldObject descr = (CFieldObject) getAttr.executeObject(frame, type, fname);
                if (getClassNode.execute(inliningTarget, descr) != CField) {
                    throw raiseNode.get(inliningTarget).raise(AttributeError, S_IS_SPECIFIED_IN_ANONYMOUS_BUT_NOT_IN_FIELDS, fname);
                }
                descr.anonymous = 1;

                /* descr is in the field descriptor. */
                makeFieldsNode.execute(frame, type, descr, descr.index, descr.offset, factory);
            }
        }
    }
}
