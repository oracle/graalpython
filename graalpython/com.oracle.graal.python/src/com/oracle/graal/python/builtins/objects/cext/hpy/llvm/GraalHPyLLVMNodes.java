/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy.llvm;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_WRITE_D;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_WRITE_HPY;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_WRITE_HPY_SSIZE_T;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_WRITE_I32;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_WRITE_I64;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_WRITE_PTR;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EnsureTruffleStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetByteArrayNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.AllocateNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.BulkFreeHandleReferencesNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.FreeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.GetElementPtrNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.IsNullNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadFloatNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadGenericNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadHPyArrayNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadHPyFieldNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadHPyNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadI32Node;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadI64Node;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadI8ArrayNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadPointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WriteDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WriteGenericNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WriteHPyFieldNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WriteHPyNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WriteI32Node;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WriteI64Node;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WritePointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.WriteSizeTNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.GraalHPyHandleReference;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCloseAndGetHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyFieldLoadNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyFieldStoreNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyFromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType;
import com.oracle.graal.python.builtins.objects.cext.hpy.llvm.GraalHPyLLVMNodesFactory.HPyLLVMCallHelperFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

import sun.misc.Unsafe;

abstract class GraalHPyLLVMNodes {

    private GraalHPyLLVMNodes() {
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMIsNullNode extends IsNullNode {
        @Specialization(limit = "2")
        static boolean doGeneric(@SuppressWarnings("unused") GraalHPyContext hpyContext, Object pointer,
                        @CachedLibrary("pointer") InteropLibrary lib) {
            return lib.isNull(pointer);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class LLVMAllocateNode extends AllocateNode {

        @Specialization
        static Object doGeneric(GraalHPyContext ctx, long size, @SuppressWarnings("unused") boolean zero,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperNode) {
            return callHelperNode.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_CALLOC, size, 1L);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMFreeNode extends FreeNode {

        @Specialization
        static void doGeneric(GraalHPyContext ctx, Object pointer,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperNode) {
            callHelperNode.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_FREE, pointer);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMBulkFreeHandleReferencesNode extends BulkFreeHandleReferencesNode {

        @Specialization
        static void doGeneric(GraalHPyContext ctx, GraalHPyHandleReference[] references,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperNode) {
            NativeSpaceArrayWrapper nativeSpaceArrayWrapper = new NativeSpaceArrayWrapper(references);
            callHelperNode.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_BULK_FREE, nativeSpaceArrayWrapper);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMGetElementPtrNode extends GetElementPtrNode {

        @Specialization
        static Object doGeneric(GraalHPyContext ctx, Object pointer, long offset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperNode) {
            return callHelperNode.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_GET_ELEMENT_PTR, pointer, offset);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class LLVMReadI8ArrayNode extends ReadI8ArrayNode {

        @Specialization(limit = "1")
        static byte[] doGeneric(GraalHPyContext ctx, Object pointer, long offset, long n,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("pointer") InteropLibrary interopLib,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached HPyLLVMCallHelperFunctionNode callHPyFunction) {
            if (!PInt.isIntRange(n)) {
                throw CompilerDirectives.shouldNotReachHere("cannot fit long into int");
            }
            Object typedPointer;
            if (!interopLib.hasArrayElements(pointer)) {
                typedPointer = callHPyFunction.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_FROM_I8_ARRAY, pointer, n);
            } else {
                typedPointer = pointer;
            }
            try {
                return getByteArrayNode.execute(typedPointer, n);
            } catch (OverflowException | InteropException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMReadHPyNode extends ReadHPyNode {

        @Specialization(guards = "!close")
        static Object doGet(GraalHPyContext ctx, Object pointer, long offset, @SuppressWarnings("unused") boolean close,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached HPyLLVMCallHelperFunctionNode callHelperNode,
                        @Exclusive @Cached HPyAsPythonObjectNode asPythonObjectNode) {
            Object nativeValue = callHelperNode.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_READ_HPY, pointer, offset);
            return asPythonObjectNode.execute(nativeValue);
        }

        @Specialization(guards = "close")
        static Object doClose(GraalHPyContext ctx, Object pointer, long offset, @SuppressWarnings("unused") boolean close,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached HPyLLVMCallHelperFunctionNode callHelperNode,
                        @Exclusive @Cached HPyCloseAndGetHandleNode closeAndGetHandleNode) {
            Object nativeValue = callHelperNode.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_READ_HPY, pointer, offset);
            return closeAndGetHandleNode.execute(nativeValue);
        }

        @Specialization(replaces = {"doGet", "doClose"})
        static Object doGeneric(GraalHPyContext ctx, Object pointer, long offset, @SuppressWarnings("unused") boolean close,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached HPyLLVMCallHelperFunctionNode callHelperNode,
                        @Exclusive @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Exclusive @Cached HPyCloseAndGetHandleNode closeAndGetHandleNode) {
            Object nativeValue = callHelperNode.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_READ_HPY, pointer, offset);
            if (close) {
                return closeAndGetHandleNode.execute(nativeValue);
            }
            return asPythonObjectNode.execute(nativeValue);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMReadHPyFieldNode extends ReadHPyFieldNode {

        @Specialization
        static Object doGeneric(GraalHPyContext ctx, PythonObject owner, Object pointer, long offset, @SuppressWarnings("unused") boolean close,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperNode,
                        @Cached HPyFieldLoadNode hpyFieldLoadNode) {
            Object nativeValue = callHelperNode.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_READ_HPYFIELD, pointer, offset);
            return hpyFieldLoadNode.execute(inliningTarget, owner, nativeValue);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMReadHPyArrayNode extends ReadHPyArrayNode {

        @Specialization(limit = "1")
        static Object[] doGeneric(GraalHPyContext ctx, Object pointer, long offset, long n,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("pointer") InteropLibrary lib,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode) {
            if (!PInt.isIntRange(n)) {
                throw CompilerDirectives.shouldNotReachHere("cannot fit long into int");
            }
            Object typedArrayPtr = callHelperNode.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_FROM_HPY_ARRAY, pointer, n);
            if (!lib.hasArrayElements(typedArrayPtr)) {
                throw CompilerDirectives.shouldNotReachHere("returned pointer object must have array type");
            }

            Object[] elements = new Object[(int) n];
            try {
                for (int i = 0; i < elements.length; i++) {
                    /*
                     * This will read an element of a 'HPy arr[]' and the returned value will be an
                     * HPy "structure". So, we also need to read element "_i" to get the internal
                     * handle value.
                     */
                    Object hpyStructPtr = lib.readArrayElement(typedArrayPtr, offset + i);
                    elements[i] = asPythonObjectNode.execute(lib.readMember(hpyStructPtr, GraalHPyHandle.J_I));
                }
                return elements;
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } catch (InvalidArrayIndexException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(lib, SystemError, ErrorMessages.CANNOT_ACCESS_IDX, e.getInvalidIndex(), n);
            } catch (UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(lib, SystemError, ErrorMessages.CANNOT_READ_HANDLE_VAL);
            }
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMReadI32Node extends ReadI32Node {

        @Specialization
        static int doGeneric(GraalHPyContext ctx, Object pointer, long offset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperFunction,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {

            Object nativeValue = callHelperFunction.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_READ_I32, pointer, offset);
            if (nativeValue instanceof Integer) {
                return (int) nativeValue;
            }
            if (lib.fitsInInt(nativeValue)) {
                try {
                    return lib.asInt(nativeValue);
                } catch (UnsupportedMessageException e) {
                    // fall through
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMReadI64Node extends ReadI64Node {

        @Specialization
        static long doGeneric(GraalHPyContext ctx, Object pointer, long offset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperFunction,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {

            Object nativeValue = callHelperFunction.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_READ_I64, pointer, offset);
            if (nativeValue instanceof Long) {
                return (long) nativeValue;
            }
            if (lib.fitsInLong(nativeValue)) {
                try {
                    return lib.asLong(nativeValue);
                } catch (UnsupportedMessageException e) {
                    // fall through
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMReadFloatNode extends ReadFloatNode {

        @Specialization
        static double doGeneric(GraalHPyContext ctx, Object pointer, long offset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperFunction,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {

            // note: C function 'graal_hpy_read_f' already returns a C double
            Object nativeValue = callHelperFunction.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_READ_F, pointer, offset);
            if (nativeValue instanceof Double d) {
                return d;
            }
            if (lib.fitsInDouble(nativeValue)) {
                try {
                    return lib.asDouble(nativeValue);
                } catch (UnsupportedMessageException e) {
                    // fall through
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMReadDoubleNode extends ReadDoubleNode {

        @Specialization
        static double doGeneric(GraalHPyContext ctx, Object pointer, long offset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperFunction,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {

            Object nativeValue = callHelperFunction.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_READ_D, pointer, offset);
            if (nativeValue instanceof Double d) {
                return d;
            }
            if (lib.fitsInDouble(nativeValue)) {
                try {
                    return lib.asDouble(nativeValue);
                } catch (UnsupportedMessageException e) {
                    // fall through
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMReadPointerNode extends ReadPointerNode {

        @Specialization
        static Object doGeneric(GraalHPyContext ctx, Object pointer, long offset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperFunction) {
            return callHelperFunction.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_READ_PTR, pointer, offset);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMReadGenericNode extends ReadGenericNode {

        @Override
        protected final int executeInt(GraalHPyContext ctx, Object pointer, long offset, HPyContextSignatureType size) {
            Object object = execute(ctx, pointer, offset, size);
            if (object instanceof Integer i) {
                return i;
            } else if (object instanceof Long l) {
                return (int) (long) l;
            }
            return numberAsInt((Number) object);
        }

        @Override
        protected final long executeLong(GraalHPyContext ctx, Object pointer, long offset, HPyContextSignatureType size) {
            Object object = execute(ctx, pointer, offset, size);
            if (object instanceof Integer i) {
                return (int) i;
            } else if (object instanceof Long l) {
                return l;
            }
            return numberAsLong((Number) object);
        }

        @Specialization
        static Object doGeneric(GraalHPyContext ctx, Object pointer, long offset, HPyContextSignatureType ctype,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperFunction) {
            return callHelperFunction.call(inliningTarget, ctx, getReadAccessorName(ctx, ctype), pointer, offset);
        }

        static GraalHPyNativeSymbol getReadAccessorName(GraalHPyContext ctx, HPyContextSignatureType type) {
            switch (type) {
                case Int8_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_I8;
                case Uint8_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_UI8;
                case Int16_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_I16;
                case Uint16_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_UI16;
                case Int32_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_I32;
                case Uint32_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_UI32;
                case Int64_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_I64;
                case Uint64_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_UI64;
                case Int:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_I;
                case Long:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_L;
                case CFloat:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_F;
                case CDouble:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_D;
                case HPyContextPtr, VoidPtr, VoidPtrPtr, HPyPtr, ConstHPyPtr, Wchar_tPtr, ConstWchar_tPtr, CharPtr, ConstCharPtr, DataPtr, DataPtrPtr, Cpy_PyObjectPtr, HPyModuleDefPtr,
                                HPyType_SpecPtr, HPyType_SpecParamPtr, HPyDefPtr, HPyFieldPtr, HPyGlobalPtr, HPyCapsule_DestructorPtr, PyType_SlotPtr:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_PTR;
                case Bool:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_BOOL;
                case UnsignedInt:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_UI;
                case UnsignedLong:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_UL;
                case HPy_ssize_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_HPY_SSIZE_T;
            }
            int size = ctx.getCTypeSize(type);
            switch (size) {
                case 1:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_I8;
                case 2:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_I16;
                case 4:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_I32;
                case 8:
                    return GraalHPyNativeSymbol.GRAAL_HPY_READ_I64;
            }
            throw CompilerDirectives.shouldNotReachHere("invalid member type");
        }

        @TruffleBoundary
        private int numberAsInt(Number number) {
            return number.intValue();
        }

        @TruffleBoundary
        private long numberAsLong(Number number) {
            return number.longValue();
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMWriteI32Node extends WriteI32Node {

        @Specialization
        static void doGeneric(GraalHPyContext ctx, Object basePointer, long offset, int value,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperFunction) {
            callHelperFunction.call(inliningTarget, ctx, GRAAL_HPY_WRITE_I32, basePointer, offset, value);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMWriteI64Node extends WriteI64Node {

        @Specialization
        static void doGeneric(GraalHPyContext ctx, Object basePointer, long offset, long value,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperFunction) {
            callHelperFunction.call(inliningTarget, ctx, GRAAL_HPY_WRITE_I64, basePointer, offset, value);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMWriteSizeTNode extends WriteSizeTNode {

        @Specialization
        static void doGeneric(GraalHPyContext ctx, Object basePointer, long offset, long value,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperFunction) {
            callHelperFunction.call(inliningTarget, ctx, GRAAL_HPY_WRITE_HPY_SSIZE_T, basePointer, offset, value);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMWriteDoubleNode extends WriteDoubleNode {

        @Specialization
        static void doGeneric(GraalHPyContext ctx, Object basePointer, long offset, double value,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperFunction) {
            callHelperFunction.call(inliningTarget, ctx, GRAAL_HPY_WRITE_D, basePointer, offset, value);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMWriteGenericNode extends WriteGenericNode {

        @Specialization(guards = {"type == cachedType"}, limit = "1")
        static void doCached(GraalHPyContext ctx, Object pointer, long offset, @SuppressWarnings("unused") HPyContextSignatureType type, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached("type") HPyContextSignatureType cachedType,
                        @Exclusive @Cached HPyLLVMCallHelperFunctionNode callHelperFunction) {
            callHelperFunction.call(inliningTarget, ctx, getWriteAccessor(ctx, cachedType), pointer, offset, value);
        }

        @Specialization(replaces = "doCached")
        static void doGeneric(GraalHPyContext ctx, Object pointer, long offset, HPyContextSignatureType type, Object value,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached HPyLLVMCallHelperFunctionNode callHelperFunction) {
            callHelperFunction.call(inliningTarget, ctx, getWriteAccessor(ctx, type), pointer, offset, value);
        }

        static GraalHPyNativeSymbol getWriteAccessor(GraalHPyContext ctx, HPyContextSignatureType type) {
            switch (type) {
                case Int8_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_I8;
                case Uint8_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_UI8;
                case Int16_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_I16;
                case Uint16_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_UI16;
                case Int32_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_I32;
                case Uint32_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_UI32;
                case Int64_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_I64;
                case Uint64_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_UI64;
                case Int:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_I;
                case Long:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_L;
                case CFloat:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_F;
                case CDouble:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_D;
                case HPyContextPtr, VoidPtr, VoidPtrPtr, HPyPtr, ConstHPyPtr, Wchar_tPtr, ConstWchar_tPtr, CharPtr, ConstCharPtr, DataPtr, DataPtrPtr, Cpy_PyObjectPtr, HPyModuleDefPtr,
                                HPyType_SpecPtr, HPyType_SpecParamPtr, HPyDefPtr, HPyFieldPtr, HPyGlobalPtr, HPyCapsule_DestructorPtr, PyType_SlotPtr:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_PTR;
                case Bool:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_BOOL;
                case UnsignedInt:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_UI;
                case UnsignedLong:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_UL;
                case HPy_ssize_t:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_HPY_SSIZE_T;
            }
            int size = ctx.getCTypeSize(type);
            switch (size) {
                case 1:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_I8;
                case 2:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_I16;
                case 4:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_I32;
                case 8:
                    return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_I64;
            }
            throw CompilerDirectives.shouldNotReachHere("invalid member type");
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMWriteHPyNode extends WriteHPyNode {

        @Specialization
        static void doGeneric(GraalHPyContext ctx, Object basePointer, long offset, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyLLVMCallHelperFunctionNode callWriteDataNode) {
            callWriteDataNode.call(inliningTarget, ctx, GRAAL_HPY_WRITE_HPY, basePointer, offset, asHandleNode.execute(object));
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMWriteHPyFieldNode extends WriteHPyFieldNode {

        @Specialization
        static void doGeneric(GraalHPyContext ctx, PythonObject owner, Object pointer, long offset, Object referent,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyFieldStoreNode fieldStoreNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyLLVMCallHelperFunctionNode callGetElementPtr,
                        @Cached HPyLLVMCallHelperFunctionNode callHelperFunctionNode) {
            Object hpyFieldPtr = HPyLLVMGetElementPtrNode.doGeneric(ctx, pointer, offset, inliningTarget, callGetElementPtr);
            Object hpyFieldObject = callHelperFunctionNode.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_GET_FIELD_I, hpyFieldPtr);
            int idx = fieldStoreNode.execute(inliningTarget, owner, hpyFieldObject, referent);
            GraalHPyHandle newHandle = asHandleNode.executeField(referent, idx);
            callHelperFunctionNode.call(inliningTarget, ctx, GraalHPyNativeSymbol.GRAAL_HPY_SET_FIELD_I, hpyFieldPtr, newHandle);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMWritePointerNode extends WritePointerNode {

        @Specialization
        static void doGeneric(GraalHPyContext ctx, Object basePointer, long offset, Object valuePointer,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyLLVMCallHelperFunctionNode callWriteDataNode) {
            callWriteDataNode.call(inliningTarget, ctx, GRAAL_HPY_WRITE_PTR, basePointer, offset, valuePointer);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMFromCharPointerNode extends HPyFromCharPointerNode {

        @Specialization
        @SuppressWarnings("unused")
        static TruffleString doCStringWrapper(GraalHPyContext hpyContext, CStringWrapper cStringWrapper, int n, Encoding encoding, boolean copy) {
            return cStringWrapper.getString();
        }

        @Specialization
        static TruffleString doCByteArrayWrapper(@SuppressWarnings("unused") GraalHPyContext hpyContext, CByteArrayWrapper cByteArrayWrapper, int n, Encoding encoding, boolean copy,
                        @Shared @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            CompilerAsserts.partialEvaluationConstant(encoding);
            CompilerAsserts.partialEvaluationConstant(copy);
            byte[] byteArray = cByteArrayWrapper.getByteArray();
            int length = n < 0 ? byteArray.length : n;
            return switchEncodingNode.execute(fromByteArrayNode.execute(byteArray, 0, length, encoding, copy), TS_ENCODING);
        }

        @Specialization(guards = {"!isCArrayWrapper(charPtr)", "isPointer(lib, charPtr)"})
        static TruffleString doPointer(GraalHPyContext hpyContext, Object charPtr, int n, Encoding encoding, boolean copy,
                        @Shared @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached TruffleString.FromNativePointerNode fromNative,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            CompilerAsserts.partialEvaluationConstant(encoding);
            CompilerAsserts.partialEvaluationConstant(copy);
            long pointer;
            try {
                pointer = lib.asPointer(charPtr);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            int length;
            if (n < 0) {
                length = 0;
                /*
                 * We use 'PythonContext.getUnsafe()' here to ensure that native access is allowed
                 * because this specialization can be reached if 'charPtr' is not a CArrayWrapper
                 * but a pointer. An attacker could create a TruffleObject that answers
                 * 'isPointer()' with 'true' (but isn't really a native pointer).
                 */
                Unsafe unsafe = hpyContext.getContext().getUnsafe();
                while (unsafe.getByte(pointer + length) != 0) {
                    length++;
                }
            } else {
                length = n;
            }
            return switchEncodingNode.execute(fromNative.execute(charPtr, 0, length, encoding, copy), TS_ENCODING);
        }

        @Specialization(guards = {"!isCArrayWrapper(charPtr)", "!isPointer(lib, charPtr)"})
        static TruffleString doForeignArray(@SuppressWarnings("unused") GraalHPyContext hpyContext, Object charPtr, int n, Encoding encoding, @SuppressWarnings("unused") boolean copy,
                        @Shared @CachedLibrary(limit = "2") InteropLibrary lib,
                        @CachedLibrary(limit = "1") InteropLibrary elementLib,
                        @Cached GraalHPyLLVMCallHelperFunctionNode callHelperFunctionNode,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Shared @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            CompilerAsserts.partialEvaluationConstant(encoding);
            CompilerAsserts.partialEvaluationConstant(copy);

            Object typedCharPtr;
            int length;
            try {
                if (!lib.hasArrayElements(charPtr)) {
                    /*
                     * If the foreign object does not have array elements, we assume it is an LLVM
                     * pointer where we can attach a type. We use size 'n' if available, otherwise
                     * we determine the size by looking for the zero-byte.
                     */
                    int size = n < 0 ? Integer.MAX_VALUE : n;
                    typedCharPtr = callHelperFunctionNode.call(hpyContext, GraalHPyNativeSymbol.GRAAL_HPY_FROM_I8_ARRAY, charPtr, size);
                    if (n < 0) {
                        length = 0;
                        while (elementLib.asByte(lib.readArrayElement(typedCharPtr, length)) != 0) {
                            length++;
                        }
                    } else {
                        length = n;
                    }
                } else {
                    /*
                     * Simple case: the foreign object has array elements, so just use the array
                     * size and read the elements.
                     */
                    typedCharPtr = charPtr;
                    length = n < 0 ? PInt.intValueExact(lib.getArraySize(charPtr)) : n;
                }
                assert lib.hasArrayElements(typedCharPtr);
                assert length >= 0;
                byte[] bytes = getByteArrayNode.execute(typedCharPtr, length);
                // since we created a fresh byte array, we don't need to copy it
                return switchEncodingNode.execute(fromByteArrayNode.execute(bytes, 0, bytes.length, encoding, false), TS_ENCODING);
            } catch (InteropException | OverflowException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        static boolean isCArrayWrapper(Object object) {
            return object instanceof CArrayWrapper || object instanceof PySequenceArrayWrapper;
        }

        static boolean isPointer(InteropLibrary lib, Object object) {
            return lib.isPointer(object);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class HPyLLVMImportSymbolNode extends PNodeWithContext {

        public abstract Object execute(Node inliningTarget, GraalHPyContext hpyContext, GraalHPyNativeSymbol symbol);

        @Specialization(guards = {"isSingleContext()", "cachedSymbol == symbol"}, limit = "1")
        static Object doSymbolCached(@SuppressWarnings("unused") GraalHPyContext nativeContext, @SuppressWarnings("unused") GraalHPyNativeSymbol symbol,
                        @SuppressWarnings("unused") @Cached("symbol") GraalHPyNativeSymbol cachedSymbol,
                        @Cached("getLLVMSymbol(nativeContext, symbol)") Object llvmSymbol) {
            return llvmSymbol;
        }

        @Specialization(replaces = "doSymbolCached")
        static Object doGeneric(Node inliningTarget, GraalHPyContext hpyContext, GraalHPyNativeSymbol symbol,
                        @Cached InlinedExactClassProfile exactClassProfile) {
            return getLLVMSymbol(exactClassProfile.profile(inliningTarget, hpyContext), symbol);
        }

        static Object getLLVMSymbol(GraalHPyContext hpyContext, GraalHPyNativeSymbol symbol) {
            if (hpyContext.getBackend() instanceof GraalHPyLLVMContext hpyLLVMContext) {
                return hpyLLVMContext.getNativeSymbolCache()[symbol.ordinal()];
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class HPyLLVMCallHelperFunctionNode extends PNodeWithContext {

        public static Object callUncached(GraalHPyContext context, GraalHPyNativeSymbol name, Object... args) {
            return HPyLLVMCallHelperFunctionNodeGen.getUncached().execute(null, context, name, args);
        }

        public final Object call(Node inliningTarget, GraalHPyContext context, GraalHPyNativeSymbol name, Object... args) {
            return execute(inliningTarget, context, name, args);
        }

        public abstract Object execute(Node inliningTarget, GraalHPyContext context, GraalHPyNativeSymbol name, Object[] args);

        @Specialization
        static Object doIt(Node inliningTarget, GraalHPyContext context, GraalHPyNativeSymbol name, Object[] args,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached HPyLLVMImportSymbolNode importCExtSymbolNode,
                        @Cached EnsureTruffleStringNode ensureTruffleStringNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                Object llvmFunction = importCExtSymbolNode.execute(inliningTarget, context, name);
                return ensureTruffleStringNode.execute(inliningTarget, interopLibrary.execute(llvmFunction, args));
            } catch (UnsupportedTypeException | ArityException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, e);
            } catch (UnsupportedMessageException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.HPY_CAPI_SYM_NOT_CALLABLE, name);
            }
        }
    }
}
