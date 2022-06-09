/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.annotations.ArgumentClinic.VALUE_EMPTY_STRING;
import static com.oracle.graal.python.annotations.ArgumentClinic.VALUE_NONE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.source.SourceSection;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PCode)
public class CodeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CodeBuiltinsFactory.getFactories();
    }

    @Builtin(name = "co_freevars", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFreeVarsNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.co_freevars(factory());
        }
    }

    @Builtin(name = "co_cellvars", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCellVarsNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.co_cellvars(factory());
        }
    }

    @Builtin(name = "co_filename", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFilenameNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected static Object get(PCode self) {
            String filename = self.getFilename();
            if (filename != null) {
                return filename;
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "co_firstlineno", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLinenoNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected static Object get(PCode self) {
            return self.getFirstLineNo();
        }
    }

    @Builtin(name = "co_name", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected static Object get(PCode self) {
            return self.co_name();
        }
    }

    @Builtin(name = "co_argcount", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetArgCountNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected static Object get(PCode self) {
            return self.co_argcount();
        }
    }

    @Builtin(name = "co_posonlyargcount", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetPosOnlyArgCountNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected static Object get(PCode self) {
            return self.co_posonlyargcount();
        }
    }

    @Builtin(name = "co_kwonlyargcount", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetKnownlyArgCountNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected static Object get(PCode self) {
            return self.co_kwonlyargcount();
        }
    }

    @Builtin(name = "co_nlocals", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNLocalsNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected static Object get(PCode self) {
            return self.co_nlocals();
        }
    }

    @Builtin(name = "co_stacksize", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetStackSizeNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected static Object get(PCode self) {
            return self.getStacksize();
        }
    }

    @Builtin(name = "co_flags", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFlagsNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected static Object get(PCode self) {
            return self.co_flags();
        }
    }

    @Builtin(name = "co_code", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.co_code(factory());
        }
    }

    @Builtin(name = "co_consts", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetConstsNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.co_consts(factory());
        }
    }

    @Builtin(name = "co_names", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNamesNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.co_names(factory());
        }
    }

    @Builtin(name = "co_varnames", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetVarNamesNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.co_varnames(factory());
        }
    }

    @Builtin(name = "co_lnotab", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLNoTabNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            byte[] lnotab = self.getLnotab();
            if (lnotab == null) {
                // TODO: this is for the moment undefined (see co_code)
                lnotab = PythonUtils.EMPTY_BYTE_ARRAY;
            }
            return factory().createBytes(lnotab);
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CodeReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object repr(PCode self) {
            return self.toString();
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CodeEqNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean eq(PCode self, PCode other) {
            if (self == other) {
                return true;
            }
            // it's quite difficult for our deserialized code objects to tell if they are the same
            if (self.getRootNode() != null) {
                if (other.getRootNode() == null) {
                    return false;
                }
                if (!self.getName().equals(other.getName())) {
                    return false;
                }
                if (!self.getFilename().equals(other.getFilename())) {
                    return false;
                }
                // we cannot really check the "code string" or AST for equality, so we just compare
                // the source string
                if (self.getRootNode() != other.getRootNode()) {
                    SourceSection selfSrcSec = self.getRootNode().getSourceSection();
                    SourceSection otherSrcSec = other.getRootNode().getSourceSection();
                    if (selfSrcSec != otherSrcSec) {
                        if (selfSrcSec != null) {
                            if (otherSrcSec == null) {
                                return false;
                            }
                            if (!selfSrcSec.getCharacters().equals(otherSrcSec.getCharacters())) {
                                return false;
                            }
                        }
                    }
                }
                // compare names, varnames, freevars, and cellvars, because those are parsing scope
                // dependent and cannot be gleaned by comparing the source string
                Object[] l = self.getNames();
                Object[] r = other.getNames();
                Arrays.sort(l);
                Arrays.sort(r);
                if (!Arrays.equals(l, r)) {
                    return false;
                }
                l = self.getVarnames();
                r = other.getVarnames();
                Arrays.sort(l);
                Arrays.sort(r);
                if (!Arrays.equals(l, r)) {
                    return false;
                }
                l = self.getFreeVars();
                r = other.getFreeVars();
                Arrays.sort(l);
                Arrays.sort(r);
                if (!Arrays.equals(l, r)) {
                    return false;
                }
                l = self.getCellVars();
                r = other.getCellVars();
                Arrays.sort(l);
                Arrays.sort(r);
                if (!Arrays.equals(l, r)) {
                    return false;
                }
                return true;
            }
            return false;
        }

        @SuppressWarnings("unused")
        @Fallback
        boolean fail(Object self, Object other) {
            return false;
        }
    }

    @Builtin(name = __HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CodeHashNode extends PythonUnaryBuiltinNode {
        @Specialization
        long hash(VirtualFrame frame, PCode self,
                        @Cached PyObjectHashNode hashNode) {
            long h, h0, h1, h2, h3, h4, h5, h6;
            PythonObjectFactory factory = factory();

            h0 = hashNode.execute(frame, self.co_name());
            h1 = hashNode.execute(frame, self.co_code(factory));
            h2 = hashNode.execute(frame, self.co_consts(factory));
            h3 = hashNode.execute(frame, self.co_names(factory));
            h4 = hashNode.execute(frame, self.co_varnames(factory));
            h5 = hashNode.execute(frame, self.co_freevars(factory));
            h6 = hashNode.execute(frame, self.co_cellvars(factory));

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
                    "co_code", "co_consts", "co_names", "co_varnames", "co_freevars", "co_cellvars", "co_filename", "co_name", "co_lnotab"})
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
    @ArgumentClinic(name = "co_filename", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = VALUE_EMPTY_STRING, useDefaultForNone = true)
    @ArgumentClinic(name = "co_name", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = VALUE_EMPTY_STRING, useDefaultForNone = true)
    @ArgumentClinic(name = "co_lnotab", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer, defaultValue = VALUE_NONE, useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class ReplaceNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodeBuiltinsClinicProviders.ReplaceNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PCode create(VirtualFrame frame, PCode self, int coArgcount,
                        int coPosonlyargcount, int coKwonlyargcount,
                        int coNlocals, int coStacksize, int coFlags,
                        int coFirstlineno, Object coCode,
                        Object[] coConsts, Object[] coNames,
                        Object[] coVarnames, Object[] coFreevars,
                        Object[] coCellvars, String coFilename,
                        String coName, Object coLnotab,
                        @Cached CodeNodes.CreateCodeNode createCodeNode,
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
                                coConsts.length == 0 ? self.getConstants(factory()) : coConsts,
                                coNames.length == 0 ? self.getNames() : coNames,
                                coVarnames.length == 0 ? self.getVarnames() : coVarnames,
                                coFreevars.length == 0 ? self.getFreeVars() : coFreevars,
                                coCellvars.length == 0 ? self.getCellVars() : coCellvars,
                                coFilename.isEmpty() ? self.co_filename() : coFilename,
                                coName.isEmpty() ? self.co_name() : coName,
                                coFirstlineno == -1 ? self.co_firstlineno() : coFirstlineno,
                                PGuards.isNone(coLnotab) ? self.getLnotab() : bufferLib.getInternalOrCopiedByteArray(coLnotab));
            } finally {
                if (!PGuards.isNone(coCode)) {
                    bufferLib.release(coCode, frame, this);
                }
                if (!PGuards.isNone(coLnotab)) {
                    bufferLib.release(coLnotab, frame, this);
                }
            }
        }
    }
}
