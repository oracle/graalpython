/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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

package com.oracle.graal.python.builtins.objects.code;

import static com.oracle.graal.python.annotations.ArgumentClinic.VALUE_EMPTY_TSTRING;
import static com.oracle.graal.python.annotations.ArgumentClinic.VALUE_NONE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.StringLiterals.T_NONE;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_BYTE_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.objectArrayToTruffleStringArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.str.StringNodes.InternStringNode;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.compiler.BytecodeCodeUnit;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.OpCodes;
import com.oracle.graal.python.compiler.SourceMap;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.introspection.BytecodeIntrospection;
import com.oracle.truffle.api.bytecode.introspection.SourceInformation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PCode)
public final class CodeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CodeBuiltinsFactory.getFactories();
    }

    @Builtin(name = "co_freevars", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFreeVarsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self,
                        @Bind("this") Node inliningTarget,
                        @Cached InternStringNode internStringNode,
                        @Cached PythonObjectFactory factory) {
            return internStrings(inliningTarget, self.getFreeVars(), internStringNode, factory);
        }
    }

    @Builtin(name = "co_cellvars", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCellVarsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self,
                        @Bind("this") Node inliningTarget,
                        @Cached InternStringNode internStringNode,
                        @Cached PythonObjectFactory factory) {
            return internStrings(inliningTarget, self.getCellVars(), internStringNode, factory);
        }
    }

    @Builtin(name = "co_filename", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFilenameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self,
                        @Bind("this") Node inliningTarget,
                        @Cached InternStringNode internStringNode) {
            TruffleString filename = self.getFilename();
            if (filename != null) {
                return internStringNode.execute(inliningTarget, filename);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "co_firstlineno", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLinenoNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self) {
            return self.getFirstLineNo();
        }
    }

    @Builtin(name = "co_name", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self,
                        @Bind("this") Node inliningTarget,
                        @Cached InternStringNode internStringNode) {
            return internStringNode.execute(inliningTarget, self.co_name());
        }
    }

    @Builtin(name = "co_qualname", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetQualNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self,
                        @Bind("this") Node inliningTarget,
                        @Cached InternStringNode internStringNode) {
            return internStringNode.execute(inliningTarget, self.co_qualname());
        }
    }

    @Builtin(name = "co_argcount", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetArgCountNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self) {
            return self.co_argcount();
        }
    }

    @Builtin(name = "co_posonlyargcount", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetPosOnlyArgCountNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self) {
            return self.co_posonlyargcount();
        }
    }

    @Builtin(name = "co_kwonlyargcount", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetKnownlyArgCountNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self) {
            return self.co_kwonlyargcount();
        }
    }

    @Builtin(name = "co_nlocals", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNLocalsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self) {
            return self.co_nlocals();
        }
    }

    @Builtin(name = "co_stacksize", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetStackSizeNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self) {
            return self.getStacksize();
        }
    }

    @Builtin(name = "co_flags", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFlagsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self) {
            return self.co_flags();
        }
    }

    @Builtin(name = "co_code", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self,
                        @Cached PythonObjectFactory factory) {
            return self.co_code(factory);
        }
    }

    @Builtin(name = "co_consts", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetConstsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self,
                        @Bind("this") Node inliningTarget,
                        @Cached InternStringNode internStringNode,
                        @Cached PythonObjectFactory factory) {
            return internStrings(inliningTarget, self.getConstants(), internStringNode, factory);
        }
    }

    @Builtin(name = "co_names", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNamesNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self,
                        @Bind("this") Node inliningTarget,
                        @Cached InternStringNode internStringNode,
                        @Cached PythonObjectFactory factory) {
            return internStrings(inliningTarget, self.getNames(), internStringNode, factory);
        }
    }

    @Builtin(name = "co_varnames", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetVarNamesNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self,
                        @Bind("this") Node inliningTarget,
                        @Cached InternStringNode internStringNode,
                        @Cached PythonObjectFactory factory) {
            return internStrings(inliningTarget, self.getVarnames(), internStringNode, factory);
        }
    }

    // They are not the same, but we don't really implement either properly
    @Builtin(name = "co_lnotab", minNumOfPositionalArgs = 1, isGetter = true)
    @Builtin(name = "co_linetable", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLineTableNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PCode self,
                        @Cached PythonObjectFactory factory) {
            byte[] linetable = self.getLinetable();
            if (linetable == null) {
                // TODO: this is for the moment undefined (see co_code)
                linetable = PythonUtils.EMPTY_BYTE_ARRAY;
            }
            return factory.createBytes(linetable);
        }
    }

    @Builtin(name = "co_exceptiontable", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetExceptionTableNode extends PythonUnaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object get(PCode self,
                        @Cached PythonObjectFactory factory) {
            // We store our exception table together with the bytecode, not in this field
            return factory.createBytes(EMPTY_BYTE_ARRAY);
        }
    }

    @Builtin(name = "co_lines", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CoLinesNode extends PythonUnaryBuiltinNode {
        private static final class IteratorData {
            int start = 0;
            int line = -1;
        }

        @Specialization
        @TruffleBoundary
        static Object lines(PCode self) {
            PythonObjectFactory factory = PythonContext.get(null).factory();
            PTuple tuple;
            CodeUnit co = self.getCodeUnit();
            if (co != null) {
                if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                    BytecodeIntrospection introspectionData = ((PBytecodeDSLRootNode) self.getRootNode()).getIntrospectionData();
                    List<PTuple> lines = new ArrayList<>();
                    for (SourceInformation sourceInfo : introspectionData.getSourceInformation()) {
                        lines.add(factory.createTuple(new int[]{sourceInfo.getStartBci(), sourceInfo.getEndBci(), sourceInfo.getSourceSection().getStartLine()}));
                    }
                    tuple = factory.createTuple(lines.toArray());
                } else {
                    BytecodeCodeUnit bytecodeCo = (BytecodeCodeUnit) co;
                    SourceMap map = bytecodeCo.getSourceMap();
                    List<PTuple> lines = new ArrayList<>();
                    if (map != null && map.startLineMap.length > 0) {
                        IteratorData data = new IteratorData();
                        data.line = map.startLineMap[0];
                        bytecodeCo.iterateBytecode((int bci, OpCodes op, int oparg, byte[] followingArgs) -> {
                            int nextStart = bci + op.length();
                            if (map.startLineMap[bci] != data.line || nextStart == bytecodeCo.code.length) {
                                lines.add(factory.createTuple(new int[]{data.start, nextStart, data.line}));
                                data.line = map.startLineMap[bci];
                                data.start = nextStart;
                            }
                        });
                    }
                    tuple = factory.createTuple(lines.toArray());
                }
            } else {
                tuple = factory.createEmptyTuple();
            }
            return PyObjectGetIter.executeUncached(tuple);
        }
    }

    @Builtin(name = "co_positions", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CoPositionsNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object positions(PCode self) {
            PythonObjectFactory factory = PythonContext.get(null).factory();
            PTuple tuple;
            CodeUnit co = self.getCodeUnit();
            if (co != null) {
                if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                    // TODO implement
                    throw new UnsupportedOperationException("not implemented");
                } else {
                    BytecodeCodeUnit bytecodeCo = (BytecodeCodeUnit) co;
                    SourceMap map = bytecodeCo.getSourceMap();
                    List<PTuple> lines = new ArrayList<>();
                    if (map != null && map.startLineMap.length > 0) {
                        byte[] bytecode = bytecodeCo.code;
                        for (int i = 0; i < bytecode.length;) {
                            lines.add(factory.createTuple(new int[]{map.startLineMap[i], map.endLineMap[i], map.startColumnMap[i], map.endColumnMap[i]}));
                            i += OpCodes.fromOpCode(bytecode[i]).length();
                        }
                    }
                    tuple = factory.createTuple(lines.toArray());
                }
            } else {
                tuple = factory.createEmptyTuple();
            }
            return PyObjectGetIter.executeUncached(tuple);
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CodeReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(PCode self,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            TruffleString codeName = self.getName() == null ? T_NONE : self.getName();
            TruffleString codeFilename = self.getFilename() == null ? T_NONE : self.getFilename();
            int codeFirstLineNo = self.getFirstLineNo() == 0 ? -1 : self.getFirstLineNo();
            return simpleTruffleStringFormatNode.format("<code object %s, file \"%s\", line %d>", codeName, codeFilename, codeFirstLineNo);
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CodeEqNode extends PythonBinaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        boolean eq(PCode self, PCode other) {
            if (self == other) {
                return true;
            }
            // it's quite difficult for our deserialized code objects to tell if they are the same
            if (self.getRootNode() != null && other.getRootNode() != null) {
                if (!self.getName().equalsUncached(other.getName(), TS_ENCODING)) {
                    return false;
                }
                if (self.co_argcount() != other.co_argcount() || self.co_posonlyargcount() != other.co_posonlyargcount() || self.co_kwonlyargcount() != other.co_kwonlyargcount() ||
                                self.co_nlocals() != other.co_nlocals() || self.co_flags() != other.co_flags() || self.co_firstlineno() != other.co_firstlineno()) {
                    return false;
                }
                if (!Arrays.equals(self.getCodestring(), other.getCodestring())) {
                    return false;
                }
                // TODO compare co_const
                return Arrays.equals(self.getNames(), other.getNames()) && Arrays.equals(self.getVarnames(), other.getVarnames()) && Arrays.equals(self.getFreeVars(), other.getFreeVars()) &&
                                Arrays.equals(self.getCellVars(), other.getCellVars());
            }
            return false;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object fail(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CodeHashNode extends PythonUnaryBuiltinNode {
        @Specialization
        static long hash(VirtualFrame frame, PCode self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectHashNode hashNode,
                        @Cached PythonObjectFactory factory) {
            long h, h0, h1, h2, h3, h4, h5, h6;

            h0 = hashNode.execute(frame, inliningTarget, self.co_name());
            h1 = hashNode.execute(frame, inliningTarget, self.co_code(factory));
            h2 = hashNode.execute(frame, inliningTarget, self.co_consts(factory));
            h3 = hashNode.execute(frame, inliningTarget, self.co_names(factory));
            h4 = hashNode.execute(frame, inliningTarget, self.co_varnames(factory));
            h5 = hashNode.execute(frame, inliningTarget, self.co_freevars(factory));
            h6 = hashNode.execute(frame, inliningTarget, self.co_cellvars(factory));

            h = h0 ^ h1 ^ h2 ^ h3 ^ h4 ^ h5 ^ h6 ^
                            self.co_argcount() ^ self.co_posonlyargcount() ^ self.co_kwonlyargcount() ^
                            self.co_nlocals() ^ self.co_flags();
            if (h == -1) {
                h = -2;
            }
            return h;
        }
    }

    @Builtin(name = "replace", minNumOfPositionalArgs = 1, parameterNames = {"$self",
                    "co_argcount", "co_posonlyargcount", "co_kwonlyargcount", "co_nlocals", "co_stacksize", "co_flags", "co_firstlineno",
                    "co_code", "co_consts", "co_names", "co_varnames", "co_freevars", "co_cellvars",
                    "co_filename", "co_name", "co_qualname", "co_linetable", "co_exceptiontable"})
    @ArgumentClinic(name = "co_argcount", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ArgumentClinic(name = "co_posonlyargcount", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ArgumentClinic(name = "co_kwonlyargcount", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ArgumentClinic(name = "co_nlocals", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ArgumentClinic(name = "co_stacksize", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ArgumentClinic(name = "co_flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ArgumentClinic(name = "co_firstlineno", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ArgumentClinic(name = "co_code", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer, defaultValue = VALUE_NONE, useDefaultForNone = true)
    @ArgumentClinic(name = "co_consts", conversion = ArgumentClinic.ClinicConversion.Tuple)
    @ArgumentClinic(name = "co_names", conversion = ArgumentClinic.ClinicConversion.Tuple)
    @ArgumentClinic(name = "co_varnames", conversion = ArgumentClinic.ClinicConversion.Tuple)
    @ArgumentClinic(name = "co_freevars", conversion = ArgumentClinic.ClinicConversion.Tuple)
    @ArgumentClinic(name = "co_cellvars", conversion = ArgumentClinic.ClinicConversion.Tuple)
    @ArgumentClinic(name = "co_filename", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = VALUE_EMPTY_TSTRING, useDefaultForNone = true)
    @ArgumentClinic(name = "co_name", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = VALUE_EMPTY_TSTRING, useDefaultForNone = true)
    @ArgumentClinic(name = "co_qualname", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = VALUE_EMPTY_TSTRING, useDefaultForNone = true)
    @ArgumentClinic(name = "co_linetable", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer, defaultValue = VALUE_NONE, useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class ReplaceNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodeBuiltinsClinicProviders.ReplaceNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PCode create(VirtualFrame frame, PCode self, int coArgcount,
                        int coPosonlyargcount, int coKwonlyargcount,
                        int coNlocals, int coStacksize, int coFlags,
                        int coFirstlineno, Object coCode,
                        Object[] coConsts, Object[] coNames,
                        Object[] coVarnames, Object[] coFreevars,
                        Object[] coCellvars, TruffleString coFilename,
                        TruffleString coName, TruffleString coQualname,
                        Object coLnotab, @SuppressWarnings("unused") Object coExceptiontable,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached CodeNodes.CreateCodeNode createCodeNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
            try {
                return createCodeNode.execute(frame,
                                coArgcount == -1 ? self.co_argcount() : coArgcount,
                                coPosonlyargcount == -1 ? self.co_posonlyargcount() : coPosonlyargcount,
                                coKwonlyargcount == -1 ? self.co_kwonlyargcount() : coKwonlyargcount,
                                coNlocals == -1 ? self.co_nlocals() : coNlocals,
                                coStacksize == -1 ? self.co_stacksize() : coStacksize,
                                coFlags == -1 ? self.co_flags() : coFlags,
                                PGuards.isNone(coCode) ? self.getCodestring() : bufferLib.getInternalOrCopiedByteArray(coCode),
                                coConsts.length == 0 ? null : coConsts,
                                coNames.length == 0 ? null : objectArrayToTruffleStringArray(inliningTarget, coNames, castToTruffleStringNode),
                                coVarnames.length == 0 ? null : objectArrayToTruffleStringArray(inliningTarget, coVarnames, castToTruffleStringNode),
                                coFreevars.length == 0 ? null : objectArrayToTruffleStringArray(inliningTarget, coFreevars, castToTruffleStringNode),
                                coCellvars.length == 0 ? null : objectArrayToTruffleStringArray(inliningTarget, coCellvars, castToTruffleStringNode),
                                coFilename.isEmpty() ? self.co_filename() : coFilename,
                                coName.isEmpty() ? self.co_name() : coName,
                                coQualname.isEmpty() ? self.co_qualname() : coQualname,
                                coFirstlineno == -1 ? self.co_firstlineno() : coFirstlineno,
                                PGuards.isNone(coLnotab) ? self.getLinetable() : bufferLib.getInternalOrCopiedByteArray(coLnotab));
            } finally {
                if (!PGuards.isNone(coCode)) {
                    bufferLib.release(coCode, frame, indirectCallData);
                }
                if (!PGuards.isNone(coLnotab)) {
                    bufferLib.release(coLnotab, frame, indirectCallData);
                }
            }
        }
    }

    private static boolean hasStrings(Object[] values) {
        for (Object o : values) {
            if (o instanceof TruffleString) {
                return true;
            }
        }
        return false;
    }

    private static PTuple internStrings(Node inliningTarget, Object[] values, InternStringNode internStringNode, PythonObjectFactory factory) {
        if (values == null) {
            return factory.createEmptyTuple();
        }
        Object[] result;
        if (!hasStrings(values)) {
            result = values;
        } else {
            result = new Object[values.length];
            for (int i = 0; i < values.length; ++i) {
                if (values[i] instanceof TruffleString) {
                    result[i] = internStringNode.execute(inliningTarget, values[i]);
                } else {
                    result[i] = values[i];
                }
            }
        }
        return factory.createTuple(result);
    }
}
