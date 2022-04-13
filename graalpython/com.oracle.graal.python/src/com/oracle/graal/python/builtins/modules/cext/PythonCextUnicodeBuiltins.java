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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.ChrNode;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.CodecsEncodeNode;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.InternNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.NativeBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.NativeEncoderNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.NativeUnicodeBuiltin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins.DecodeNode;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EncodeNativeStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetByteArrayNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.str.NativeCharSequence;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EncodeNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EndsWithNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EqNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.FindNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.LtNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.ModNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.RFindNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.ReplaceNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.StartsWithNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextUnicodeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextUnicodeBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "PyUnicode_FromObject", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyUnicodeFromObjectNode extends PythonBuiltinNode {
        @Specialization
        public static String fromObject(String s) {
            return s;
        }

        @Specialization(guards = "isPStringType(s, getClassNode)")
        public static PString fromObject(PString s,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode) {
            return s;
        }

        @Specialization(guards = {"!isPStringType(obj, getClassNode)", "isStringSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object fromObject(VirtualFrame frame, Object obj,
                        @Cached StrNode strNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return strNode.executeWith(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isStringSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object fromObject(VirtualFrame frame, Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, ErrorMessages.CANT_CONVERT_TO_STR_IMPLICITLY, obj);
        }

        protected boolean isStringSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }

        protected boolean isPStringType(Object obj, GetClassNode getClassNode) {
            return getClassNode.execute(obj) == PythonBuiltinClassType.PString;
        }
    }

    @Builtin(name = "PyUnicode_GetLength", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyUnicodeGetLengthNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int fromObject(String s) {
            return s.length();
        }

        @Specialization(guards = {"!isJavaString(obj)", "isStringSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object getLength(VirtualFrame frame, Object obj,
                        @Cached com.oracle.graal.python.builtins.objects.str.StringBuiltins.LenNode lenNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return lenNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isJavaString(obj)", "!isStringSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object getLength(VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }

        protected boolean isStringSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @Builtin(name = "PyUnicode_Concat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyUnicodeConcatNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"isString(left) || isStringSubtype(frame, left, getClassNode, isSubtypeNode)", "isString(right) || isStringSubtype(frame, right, getClassNode, isSubtypeNode)"})
        public Object concat(VirtualFrame frame, Object left, Object right,
                        @Cached StringBuiltins.AddNode addNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return addNode.execute(frame, left, right);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isString(left)", "!isStringSubtype(frame, left, getClassNode, isSubtypeNode)"})
        public Object leftNotString(VirtualFrame frame, Object left, @SuppressWarnings("unused") Object right,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, ErrorMessages.MUST_BE_STR_NOT_P, left);
        }

        @Specialization(guards = {"!isString(right)", "!isStringSubtype(frame, right, getClassNode, isSubtypeNode)"})
        public Object rightNotString(VirtualFrame frame, @SuppressWarnings("unused") Object left, Object right,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, ErrorMessages.MUST_BE_STR_NOT_P, right);
        }

        protected boolean isStringSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @Builtin(name = "PyUnicode_FromEncodedObject", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PyUnicodeFromEncodedObjectNode extends PythonTernaryBuiltinNode {
        @Specialization
        public Object fromBytes(VirtualFrame frame, PBytesLike obj, String encoding, String errors,
                        @Cached DecodeNode decodeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            return decode(frame, obj, encoding, errors, decodeNode, transformExceptionToNativeNode);
        }

        @Specialization(guards = {"!isBytes(obj)", "!isString(obj)", "!isStringSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object fromEncoded(VirtualFrame frame, Object obj, String encoding, String errors,
                        @Cached DecodeNode decodeNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            return decode(frame, obj, encoding, errors, decodeNode, transformExceptionToNativeNode);
        }

        private Object decode(VirtualFrame frame, Object obj, String encoding, String errors, DecodeNode decodeNode, TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return decodeNode.execute(frame, obj, encoding, errors);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "isString(obj) || isStringSubtype(frame, obj, getClassNode, isSubtypeNode)")
        public Object concat(VirtualFrame frame, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") String encoding, @SuppressWarnings("unused") String errors,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, ErrorMessages.DECODING_STR_NOT_SUPPORTED);
        }

        protected boolean isStringSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @Builtin(name = "PyUnicode_InternInPlace", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyUnicodeInternInPlaceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = {"!isJavaString(obj)", "isStringSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object intern(VirtualFrame frame, Object obj,
                        @Cached InternNode internNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return internNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isJavaString(obj)", "!isStringSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object intern(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            assert false;
            return PNone.NONE;
        }

        protected boolean isStringSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @Builtin(name = "PyUnicode_Format", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyUnicodeFormatNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"isString(format) || isStringSubtype(frame, format, getClassNode, isSubtypeNode)"})
        public Object find(VirtualFrame frame, Object format, Object args,
                        @Cached ModNode modNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return modNode.execute(frame, format, args);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isJavaString(format)", "isStringSubtype(frame, format, getClassNode, isSubtypeNode)"})
        public Object find(VirtualFrame frame, Object format, @SuppressWarnings("unused") Object args,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, ErrorMessages.MUST_BE_STR_NOT_P, format);
        }

        protected boolean isStringSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @Builtin(name = "PyUnicode_FindChar", minNumOfPositionalArgs = 5)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    public abstract static class PyUnicodeFindCharNode extends PythonBuiltinNode {
        @Specialization(guards = {"isString(string) || isStringSubtype(frame, string, getClassNode, isSubtypeNode)", "direction > 0"})
        public static Object find(VirtualFrame frame, Object string, Object c, long start, long end, @SuppressWarnings("unused") long direction,
                        @Cached ChrNode chrNode,
                        @Cached FindNode findNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return findNode.execute(frame, string, chrNode.execute(frame, c), start, end);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"isString(string) || isStringSubtype(frame, string, getClassNode, isSubtypeNode)", "direction <= 0"})
        public static Object find(VirtualFrame frame, Object string, Object c, long start, long end, @SuppressWarnings("unused") long direction,
                        @Cached ChrNode chrNode,
                        @Cached RFindNode rFindNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return rFindNode.execute(frame, string, chrNode.execute(frame, c), start, end);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isJavaString(string)", "isStringSubtype(frame, string, getClassNode, isSubtypeNode)"})
        public static Object find(VirtualFrame frame, Object string, @SuppressWarnings("unused") Object c, @SuppressWarnings("unused") Object start, @SuppressWarnings("unused") Object end,
                        @SuppressWarnings("unused") Object direction,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, TypeError, ErrorMessages.MUST_BE_STR_NOT_P, string);
        }

        protected boolean isStringSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @Builtin(name = "PyUnicode_Substring", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    public abstract static class PyUnicodeSubstringNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = {"isString(s) || isStringSubtype(frame, s, getClassNode, isSubtypeNode)"})
        public Object find(VirtualFrame frame, Object s, long start, long end,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached SliceLiteralNode sliceNode,
                        @Cached CallNode callNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object getItemCallable = lookupAttrNode.execute(frame, s, __GETITEM__);
                return callNode.execute(getItemCallable, sliceNode.execute(frame, start, end, PNone.NONE));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isJavaString(s)", "isStringSubtype(frame, s, getClassNode, isSubtypeNode)"})
        public Object find(VirtualFrame frame, Object s, @SuppressWarnings("unused") Object start, @SuppressWarnings("unused") Object end,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, ErrorMessages.MUST_BE_STR_NOT_P, s);
        }

        protected boolean isStringSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @Builtin(name = "PyUnicode_Join", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    public abstract static class PyUnicodeJoinNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"isString(separator) || isStringSubtype(frame, separator, getClassNode, isSubtypeNode)"})
        public Object find(VirtualFrame frame, Object separator, Object seq,
                        @Cached StringBuiltins.JoinNode joinNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return joinNode.execute(frame, separator, seq);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isJavaString(separator)", "isStringSubtype(frame, separator, getClassNode, isSubtypeNode)"})
        public Object find(VirtualFrame frame, Object separator, @SuppressWarnings("unused") Object seq,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, ErrorMessages.MUST_BE_STR_NOT_P, separator);
        }

        protected boolean isStringSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @Builtin(name = "PyUnicode_Compare", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    public abstract static class PyUnicodeCompareNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"isAnyString(frame, left, getClassNode, isSubtypeNode)", "isAnyString(frame, right, getClassNode, isSubtypeNode)"})
        public static Object compare(VirtualFrame frame, Object left, Object right,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached EqNode eqNode,
                        @Cached LtNode ltNode,
                        @Cached ConditionProfile eqProfile) {
            if (eqProfile.profile((boolean) eqNode.execute(frame, left, right))) {
                return 0;
            } else {
                return (boolean) ltNode.execute(frame, left, right) ? -1 : 1;
            }
        }

        @Specialization(guards = {"!isAnyString(frame, left, getClassNode, isSubtypeNode) || !isAnyString(frame, right, getClassNode, isSubtypeNode)"})
        public static Object compare(VirtualFrame frame, Object left, Object right,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, TypeError, ErrorMessages.CANT_COMPARE, left, right);
        }

        protected boolean isAnyString(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return PGuards.isString(obj) || isStringSubtype(frame, obj, getClassNode, isSubtypeNode);
        }

        private static boolean isStringSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @Builtin(name = "PyUnicode_Tailmatch", minNumOfPositionalArgs = 5)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    public abstract static class PyUnicodeTailmatchNode extends PythonBuiltinNode {
        @Specialization(guards = {"isAnyString(frame, string, getClassNode, isSubtypeNode)", "isAnyString(frame, substring, getClassNode, isSubtypeNode)", "direction > 0"})
        public static int tailmatch(VirtualFrame frame, Object string, Object substring, long start, long end, @SuppressWarnings("unused") long direction,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached SliceLiteralNode sliceNode,
                        @Cached CallNode callNode,
                        @Cached EndsWithNode endsWith,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object getItemCallable = lookupAttrNode.execute(frame, string, __GETITEM__);
                Object slice = callNode.execute(getItemCallable, sliceNode.execute(frame, start, end, PNone.NONE));
                return (boolean) endsWith.execute(frame, slice, substring, start, end) ? 1 : 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"isAnyString(frame, string, getClassNode, isSubtypeNode)", "isAnyString(frame, substring, getClassNode, isSubtypeNode)", "direction <= 0"})
        public static int tailmatch(VirtualFrame frame, Object string, Object substring, long start, long end, @SuppressWarnings("unused") long direction,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached SliceLiteralNode sliceNode,
                        @Cached CallNode callNode,
                        @Cached StartsWithNode endsWith,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object getItemCallable = lookupAttrNode.execute(frame, string, __GETITEM__);
                Object slice = callNode.execute(getItemCallable, sliceNode.execute(frame, start, end, PNone.NONE));
                return (boolean) endsWith.execute(frame, slice, substring, start, end) ? 1 : 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isAnyString(frame, string, getClassNode, isSubtypeNode) || !isAnyString(frame, substring, getClassNode, isSubtypeNode)"})
        public static Object find(VirtualFrame frame, Object string, Object substring, Object start, Object end, Object direction,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, TypeError, ErrorMessages.MUST_BE_STR_NOT_P, string);
        }

        protected boolean isAnyString(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return PGuards.isString(obj) || isStringSubtype(frame, obj, getClassNode, isSubtypeNode);
        }

        private static boolean isStringSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @Builtin(name = "PyUnicode_AsEncodedString", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PyUnicodeAsEncodedStringNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = "isString(obj) || isStringSubtype(frame, obj, getClassNode, isSubtypeNode)")
        public Object encode(VirtualFrame frame, Object obj, String encoding, String errors,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached EncodeNode encodeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return encodeNode.execute(frame, obj, encoding, errors);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isString(obj)", "!isStringSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object encode(VirtualFrame frame, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") String encoding, @SuppressWarnings("unused") String errors,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }

        protected static boolean isStringSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @Builtin(name = "PyUnicode_Replace", minNumOfPositionalArgs = 4)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    public abstract static class PyUnicodeReplaceNode extends PythonQuaternaryBuiltinNode {
        @Specialization(guards = {"isString(s)", "isString(substr)", "isString(replstr)"})
        public Object replace(VirtualFrame frame, Object s, Object substr, Object replstr, long count,
                        @Cached ReplaceNode replaceNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return replaceNode.execute(frame, s, substr, replstr, count);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isString(s)", "!isString(substr)", "!isString(replstr)",
                        "isStringSubtype(frame, s, getClassNode, isSubtypeNode)",
                        "isStringSubtype(frame, substr, getClassNode, isSubtypeNode)",
                        "isStringSubtype(frame, replstr, getClassNode, isSubtypeNode)"})
        public Object replace(VirtualFrame frame, Object s, Object substr, Object replstr, long count,
                        @Cached ReplaceNode replaceNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            return replace(frame, s, substr, replstr, count, replaceNode, transformExceptionToNativeNode);
        }

        @Specialization(guards = {"!isString(s)", "!isString(substr)", "!isString(replstr)",
                        "!isStringSubtype(frame, s, getClassNode, isSubtypeNode)",
                        "!isStringSubtype(frame, substr, getClassNode, isSubtypeNode)",
                        "!isStringSubtype(frame, replstr, getClassNode, isSubtypeNode)"})
        public Object replace(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") String s, @SuppressWarnings("unused") String substr,
                        @SuppressWarnings("unused") String replstr, @SuppressWarnings("unused") long count,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            return getContext().getNativeNull();
        }

        protected static boolean isStringSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @Builtin(name = "PyUnicode_AsUnicodeEscapeString", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    public abstract static class PyUnicodeAsUnicodeEscapeStringNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "isString(s)")
        public Object escape(VirtualFrame frame, Object s,
                        @Cached CodecsEncodeNode encodeNode,
                        @Cached com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.GetItemNode getItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return getItemNode.execute(frame, encodeNode.execute(frame, s, "unicode_escape", PNone.NO_VALUE), 0);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isString(s)", "isStringSubtype(frame, s, getClassNode, isSubtypeNode)"})
        public Object escape(VirtualFrame frame, Object s,
                        @Cached CodecsEncodeNode encodeNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.GetItemNode getItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            return escape(frame, s, encodeNode, getItemNode, transformExceptionToNativeNode);
        }

        @Specialization(guards = {"!isString(obj)", "!isStringSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object escape(VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }

        protected static boolean isStringSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PString);
        }
    }

    @Builtin(name = "PyUnicode_ReadChar", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyUnicodeReadChar extends PythonBinaryBuiltinNode {
        @Specialization
        int doGeneric(Object type, long lindex,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                try {
                    String s = castToJavaStringNode.execute(type);
                    int index = PInt.intValueExact(lindex);
                    // avoid StringIndexOutOfBoundsException
                    if (index < 0 || index >= PString.length(s)) {
                        throw raise(IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
                    }
                    return PString.charAt(s, index);
                } catch (CannotCastException e) {
                    throw raise(TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
                } catch (OverflowException e) {
                    throw raise(IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyUnicode_New", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyUnicodeNewNode extends PythonBuiltinNode {
        @Specialization
        Object doGeneric(Object ptr, int elementSize, int isAscii,
                        @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.execute(factory().createString(new NativeCharSequence(ptr, elementSize, isAscii != 0)));
        }
    }

    @Builtin(name = "PyUnicode_FromString", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyUnicodeFromStringNode extends PythonUnaryBuiltinNode {
        @Specialization
        PString run(String str) {
            return factory().createString(str);
        }

        @Specialization
        static PString run(PString str) {
            return str;
        }
    }

    @Builtin(name = "PyUnicode_Contains", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyUnicodeContains extends PythonBinaryBuiltinNode {
        @Specialization
        static int contains(VirtualFrame frame, Object haystack, Object needle,
                        @Cached StringBuiltins.ContainsNode containsNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return containsNode.executeBool(haystack, needle) ? 1 : 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }
    }

    @Builtin(name = "PyUnicode_Split", minNumOfPositionalArgs = 4, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyUnicodeSplit extends PythonQuaternaryBuiltinNode {
        @Specialization
        Object split(VirtualFrame frame, Object module, Object string, Object sep, Object maxsplit,
                        @Cached StringBuiltins.SplitNode splitNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return splitNode.execute(frame, string, sep, maxsplit);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "_PyUnicode_AsUTF8String", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyUnicodeAsUTF8String extends NativeEncoderNode {

        protected PyUnicodeAsUTF8String() {
            super(StandardCharsets.UTF_8);
        }
    }

    @Builtin(name = "PyUnicode_DecodeUTF8Stateful", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyUnicodeDecodeUTF8StatefulNode extends NativeUnicodeBuiltin {

        @Specialization
        Object doUtf8Decode(VirtualFrame frame, Object cByteArray, String errors, @SuppressWarnings("unused") int reportConsumed,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached GetByteArrayNode getByteArrayNode) {

            try {
                ByteBuffer inputBuffer = wrap(getByteArrayNode.execute(cByteArray, -1));
                int n = remaining(inputBuffer);
                CharBuffer resultBuffer = allocateCharBuffer(n * 4);
                decodeUTF8(resultBuffer, inputBuffer, errors);
                return toSulongNode.execute(factory().createTuple(new Object[]{toString(resultBuffer), n - remaining(inputBuffer)}));
            } catch (InteropException e) {
                return raiseNative(frame, getContext().getNativeNull(), PythonErrorType.TypeError, "%m", e);
            } catch (OverflowException e) {
                return raiseNative(frame, getContext().getNativeNull(), PythonErrorType.SystemError, ErrorMessages.INPUT_TOO_LONG);
            }
        }

        @TruffleBoundary
        private void decodeUTF8(CharBuffer resultBuffer, ByteBuffer inputBuffer, String errors) {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, this);
            decoder.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(action).decode(inputBuffer, resultBuffer, true);
        }
    }

    @Builtin(name = "PyUnicode_Decode", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyUnicodeDecodeNode extends NativeBuiltin {

        @Specialization
        Object doDecode(VirtualFrame frame, PMemoryView mv, String encoding, String errors,
                        @Cached CodecsModuleBuiltins.DecodeNode decodeNode,
                        @Cached CExtNodes.ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return toSulongNode.execute(decodeNode.executeWithStrings(frame, mv, encoding, errors));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toSulongNode.execute(getContext().getNativeNull());
            }
        }
    }

    @Builtin(name = "PyUnicode_EncodeFSDefault", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyUnicodeEncodeFSDefaultNode extends PythonBuiltinNode {
        @Specialization
        PBytes fromObject(String s,
                        @Shared("encode") @Cached EncodeNativeStringNode encode) {
            byte[] array = encode.execute(StandardCharsets.UTF_8, s, "replace");
            return factory().createBytes(array);
        }

        @Specialization
        PBytes fromObject(Object s,
                        @Cached CastToJavaStringNode castStr,
                        @Shared("encode") @Cached EncodeNativeStringNode encode) {
            byte[] array = encode.execute(StandardCharsets.UTF_8, castStr.execute(s), "replace");
            return factory().createBytes(array);
        }
    }
}
