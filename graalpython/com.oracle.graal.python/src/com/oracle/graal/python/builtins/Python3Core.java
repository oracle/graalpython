/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins;

import static com.oracle.graal.python.builtins.modules.ImpModuleBuiltins.T__IMP;
import static com.oracle.graal.python.builtins.objects.str.StringUtils.cat;
import static com.oracle.graal.python.nodes.BuiltinNames.J_POSIX;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MODULES;
import static com.oracle.graal.python.nodes.BuiltinNames.T_NT;
import static com.oracle.graal.python.nodes.BuiltinNames.T_STDERR;
import static com.oracle.graal.python.nodes.BuiltinNames.T_SYS;
import static com.oracle.graal.python.nodes.BuiltinNames.T__WEAKREF;
import static com.oracle.graal.python.nodes.BuiltinNames.T___BUILTINS__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___IMPORT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___PACKAGE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.nodes.StringLiterals.J_PY_EXTENSION;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.nodes.StringLiterals.T_GRAALPYTHON;
import static com.oracle.graal.python.nodes.StringLiterals.T_REF;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ServiceLoader;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.AbcModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ArrayModuleBuiltins;
import com.oracle.graal.python.builtins.modules.AsyncioModuleBuiltins;
import com.oracle.graal.python.builtins.modules.AtexitModuleBuiltins;
import com.oracle.graal.python.builtins.modules.BinasciiModuleBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.CmathModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CollectionsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ContextvarsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CryptModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ErrnoModuleBuiltins;
import com.oracle.graal.python.builtins.modules.FaulthandlerModuleBuiltins;
import com.oracle.graal.python.builtins.modules.FcntlModuleBuiltins;
import com.oracle.graal.python.builtins.modules.GcModuleBuiltins;
import com.oracle.graal.python.builtins.modules.GraalHPyDebugModuleBuiltins;
import com.oracle.graal.python.builtins.modules.GraalHPyTraceModuleBuiltins;
import com.oracle.graal.python.builtins.modules.GraalHPyUniversalModuleBuiltins;
import com.oracle.graal.python.builtins.modules.GraalPythonModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ImpModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ItertoolsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.JArrayModuleBuiltins;
import com.oracle.graal.python.builtins.modules.JavaModuleBuiltins;
import com.oracle.graal.python.builtins.modules.LocaleModuleBuiltins;
import com.oracle.graal.python.builtins.modules.LsprofModuleBuiltins;
import com.oracle.graal.python.builtins.modules.MMapModuleBuiltins;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.builtins.modules.MathModuleBuiltins;
import com.oracle.graal.python.builtins.modules.NtModuleBuiltins;
import com.oracle.graal.python.builtins.modules.OperatorModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PolyglotModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PosixShMemModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PosixSubprocessModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PwdModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PyExpatModuleBuiltins;
import com.oracle.graal.python.builtins.modules.QueueModuleBuiltins;
import com.oracle.graal.python.builtins.modules.RandomModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ReadlineModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ResourceModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SREModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SSLModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SelectModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SignalModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SocketModuleBuiltins;
import com.oracle.graal.python.builtins.modules.StringModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.modules.TermiosModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ThreadModuleBuiltins;
import com.oracle.graal.python.builtins.modules.TimeModuleBuiltins;
import com.oracle.graal.python.builtins.modules.TracemallocModuleBuiltins;
import com.oracle.graal.python.builtins.modules.UnicodeDataModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WeakRefModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WinregModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ast.AstBuiltins;
import com.oracle.graal.python.builtins.modules.ast.AstModuleBuiltins;
import com.oracle.graal.python.builtins.modules.bz2.BZ2CompressorBuiltins;
import com.oracle.graal.python.builtins.modules.bz2.BZ2DecompressorBuiltins;
import com.oracle.graal.python.builtins.modules.bz2.BZ2ModuleBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.CodecCtxBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.CodecsCNModuleBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.CodecsHKModuleBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.CodecsISO2022ModuleBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.CodecsJPModuleBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.CodecsKRModuleBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.CodecsTWModuleBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteIncrementalDecoderBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteIncrementalEncoderBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteStreamReaderBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteStreamWriterBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins;
import com.oracle.graal.python.builtins.modules.csv.CSVDialectBuiltins;
import com.oracle.graal.python.builtins.modules.csv.CSVModuleBuiltins;
import com.oracle.graal.python.builtins.modules.csv.CSVReaderBuiltins;
import com.oracle.graal.python.builtins.modules.csv.CSVWriterBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CArgObjectBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeSequenceBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCArrayBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCArrayTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCPointerBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCPointerTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCSimpleTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCStructTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.SimpleCDataBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StructUnionTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StructureBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.UnionTypeBuiltins;
import com.oracle.graal.python.builtins.modules.functools.FunctoolsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.functools.KeyWrapperBuiltins;
import com.oracle.graal.python.builtins.modules.functools.LruCacheWrapperBuiltins;
import com.oracle.graal.python.builtins.modules.functools.PartialBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.Blake2ModuleBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.Blake2bObjectBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.Blake2sObjectBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.DigestObjectBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.HashObjectBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.HashlibModuleBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.Md5ModuleBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.Sha1ModuleBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.Sha256ModuleBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.Sha3ModuleBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.Sha512ModuleBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.ShakeDigestObjectBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedIOBaseBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedIOMixinBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedRWPairBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedRandomBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedReaderBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedReaderMixinBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedWriterBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedWriterMixinBuiltins;
import com.oracle.graal.python.builtins.modules.io.BytesIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.FileIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.IOBaseBuiltins;
import com.oracle.graal.python.builtins.modules.io.IOBaseDictBuiltins;
import com.oracle.graal.python.builtins.modules.io.IOModuleBuiltins;
import com.oracle.graal.python.builtins.modules.io.IncrementalNewlineDecoderBuiltins;
import com.oracle.graal.python.builtins.modules.io.RawIOBaseBuiltins;
import com.oracle.graal.python.builtins.modules.io.StringIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.TextIOBaseBuiltins;
import com.oracle.graal.python.builtins.modules.io.TextIOWrapperBuiltins;
import com.oracle.graal.python.builtins.modules.json.JSONEncoderBuiltins;
import com.oracle.graal.python.builtins.modules.json.JSONModuleBuiltins;
import com.oracle.graal.python.builtins.modules.json.JSONScannerBuiltins;
import com.oracle.graal.python.builtins.modules.lzma.LZMACompressorBuiltins;
import com.oracle.graal.python.builtins.modules.lzma.LZMADecompressorBuiltins;
import com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins;
import com.oracle.graal.python.builtins.modules.multiprocessing.GraalPySemLockBuiltins;
import com.oracle.graal.python.builtins.modules.multiprocessing.MultiprocessingGraalPyModuleBuiltins;
import com.oracle.graal.python.builtins.modules.multiprocessing.MultiprocessingModuleBuiltins;
import com.oracle.graal.python.builtins.modules.multiprocessing.SemLockBuiltins;
import com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins;
import com.oracle.graal.python.builtins.modules.zlib.ZlibCompressBuiltins;
import com.oracle.graal.python.builtins.modules.zlib.ZlibDecompressBuiltins;
import com.oracle.graal.python.builtins.objects.NoneBuiltins;
import com.oracle.graal.python.builtins.objects.NotImplementedBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.ArrayBuiltins;
import com.oracle.graal.python.builtins.objects.asyncio.AsyncGenSendBuiltins;
import com.oracle.graal.python.builtins.objects.asyncio.AsyncGenThrowBuiltins;
import com.oracle.graal.python.builtins.objects.asyncio.AsyncGeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.asyncio.CoroutineWrapperBuiltins;
import com.oracle.graal.python.builtins.objects.bool.BoolBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.ByteArrayBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.cell.CellBuiltins;
import com.oracle.graal.python.builtins.objects.code.CodeBuiltins;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltins;
import com.oracle.graal.python.builtins.objects.contextvars.ContextBuiltins;
import com.oracle.graal.python.builtins.objects.contextvars.ContextIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.contextvars.ContextVarBuiltins;
import com.oracle.graal.python.builtins.objects.contextvars.TokenBuiltins;
import com.oracle.graal.python.builtins.objects.deque.DequeBuiltins;
import com.oracle.graal.python.builtins.objects.deque.DequeIterBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DefaultDictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictReprBuiltin;
import com.oracle.graal.python.builtins.objects.dict.DictValuesBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictViewBuiltins;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.EllipsisBuiltins;
import com.oracle.graal.python.builtins.objects.enumerate.EnumerateBuiltins;
import com.oracle.graal.python.builtins.objects.exception.BaseExceptionBuiltins;
import com.oracle.graal.python.builtins.objects.exception.ImportErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.KeyErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.OsErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.StopIterationBuiltins;
import com.oracle.graal.python.builtins.objects.exception.SyntaxErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.SystemExitBuiltins;
import com.oracle.graal.python.builtins.objects.exception.UnicodeDecodeErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.UnicodeEncodeErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.UnicodeTranslateErrorBuiltins;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.foreign.ForeignObjectBuiltins;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltins;
import com.oracle.graal.python.builtins.objects.function.AbstractFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.BuiltinFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.FunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.MethodDescriptorBuiltins;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.WrapperDescriptorBuiltins;
import com.oracle.graal.python.builtins.objects.generator.CommonGeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.generator.CoroutineBuiltins;
import com.oracle.graal.python.builtins.objects.generator.GeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorBuiltins;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptorTypeBuiltins;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.MemberDescriptorBuiltins;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.ForeignIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.IteratorBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.PZipBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.SentinelIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.AccumulateBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.ChainBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.CombinationsBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.CompressBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.CountBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.CycleBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.DropwhileBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.FilterfalseBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.GroupByBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.GrouperBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.IsliceBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.PairwiseBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.PermutationsBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.ProductBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.RepeatBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.StarmapBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.TakewhileBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.TeeBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.TeeDataObjectBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.ZipLongestBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.map.MapBuiltins;
import com.oracle.graal.python.builtins.objects.mappingproxy.MappingproxyBuiltins;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltins;
import com.oracle.graal.python.builtins.objects.method.AbstractBuiltinMethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.AbstractMethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.BuiltinClassmethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.BuiltinFunctionOrMethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.ClassmethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.DecoratedMethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.InstancemethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.MethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.MethodWrapperBuiltins;
import com.oracle.graal.python.builtins.objects.method.StaticmethodBuiltins;
import com.oracle.graal.python.builtins.objects.mmap.MMapBuiltins;
import com.oracle.graal.python.builtins.objects.module.ModuleBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.namespace.SimpleNamespaceBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.ordereddict.OrderedDictBuiltins;
import com.oracle.graal.python.builtins.objects.ordereddict.OrderedDictItemsBuiltins;
import com.oracle.graal.python.builtins.objects.ordereddict.OrderedDictIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.ordereddict.OrderedDictKeysBuiltins;
import com.oracle.graal.python.builtins.objects.ordereddict.OrderedDictValuesBuiltins;
import com.oracle.graal.python.builtins.objects.posix.DirEntryBuiltins;
import com.oracle.graal.python.builtins.objects.posix.ScandirIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.property.PropertyBuiltins;
import com.oracle.graal.python.builtins.objects.queue.SimpleQueueBuiltins;
import com.oracle.graal.python.builtins.objects.random.RandomBuiltins;
import com.oracle.graal.python.builtins.objects.range.RangeBuiltins;
import com.oracle.graal.python.builtins.objects.referencetype.ReferenceTypeBuiltins;
import com.oracle.graal.python.builtins.objects.reversed.ReversedBuiltins;
import com.oracle.graal.python.builtins.objects.set.BaseSetBuiltins;
import com.oracle.graal.python.builtins.objects.set.FrozenSetBuiltins;
import com.oracle.graal.python.builtins.objects.set.SetBuiltins;
import com.oracle.graal.python.builtins.objects.slice.SliceBuiltins;
import com.oracle.graal.python.builtins.objects.socket.SocketBuiltins;
import com.oracle.graal.python.builtins.objects.ssl.MemoryBIOBuiltins;
import com.oracle.graal.python.builtins.objects.ssl.SSLContextBuiltins;
import com.oracle.graal.python.builtins.objects.ssl.SSLErrorBuiltins;
import com.oracle.graal.python.builtins.objects.ssl.SSLSocketBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.builtins.objects.superobject.SuperBuiltins;
import com.oracle.graal.python.builtins.objects.thread.LockBuiltins;
import com.oracle.graal.python.builtins.objects.thread.RLockBuiltins;
import com.oracle.graal.python.builtins.objects.thread.ThreadBuiltins;
import com.oracle.graal.python.builtins.objects.thread.ThreadLocalBuiltins;
import com.oracle.graal.python.builtins.objects.traceback.TracebackBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleGetterBuiltins;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.types.GenericAliasBuiltins;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.InputType;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.PythonMapScope;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * The core is intended to the immutable part of the interpreter, including most modules and most
 * types. The core is embedded, using inheritance, into {@link PythonContext} to avoid indirection
 * through an extra field in the context.
 */
public abstract class Python3Core {
    private static final int REC_LIM = 1000;
    private static final int NATIVE_REC_LIM = 8000;
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(Python3Core.class);
    private static final TruffleString T___ANONYMOUS__ = tsLiteral("__anonymous__");
    private static final TruffleString T_IMPORTLIB = tsLiteral("importlib");
    private static final TruffleString T_IMPORTLIB_BOOTSTRAP_EXTERNAL = tsLiteral("importlib._bootstrap_external");
    private static final TruffleString T__FROZEN_IMPORTLIB_EXTERNAL = tsLiteral("_frozen_importlib_external");
    private static final TruffleString T__FROZEN_IMPORTLIB = tsLiteral("_frozen_importlib");
    private static final TruffleString T_IMPORTLIB_BOOTSTRAP = tsLiteral("importlib._bootstrap");
    private final TruffleString[] coreFiles;

    private static TruffleString[] initializeCoreFiles() {
        // Order matters!
        List<TruffleString> coreFiles = new ArrayList<>(Arrays.asList(
                        toTruffleStringUncached("__graalpython__"),
                        toTruffleStringUncached("_weakref"),
                        toTruffleStringUncached("unicodedata"),
                        toTruffleStringUncached("_sre"),
                        toTruffleStringUncached("function"),
                        toTruffleStringUncached("_sysconfig"),
                        toTruffleStringUncached("java"),
                        toTruffleStringUncached("pip_hook"),
                        toTruffleStringUncached("_struct")));
        // add service loader defined python file extensions
        if (!ImageInfo.inImageRuntimeCode()) {
            ServiceLoader<PythonBuiltins> providers = ServiceLoader.load(PythonBuiltins.class, Python3Core.class.getClassLoader());
            PythonOS currentOs = PythonOS.getPythonOS();
            for (PythonBuiltins builtin : providers) {
                CoreFunctions annotation = builtin.getClass().getAnnotation(CoreFunctions.class);
                if (!annotation.pythonFile().isEmpty() &&
                                (annotation.os() == PythonOS.PLATFORM_ANY || annotation.os() == currentOs)) {
                    coreFiles.add(toTruffleStringUncached(annotation.pythonFile()));
                }
            }
        }
        coreFiles.removeAll(Arrays.asList(new TruffleString[]{null}));
        return coreFiles.toArray(new TruffleString[coreFiles.size()]);
    }

    private final PythonBuiltins[] builtins;

    private static final boolean hasProfilerTool;
    static {
        Class<?> c = null;
        try {
            c = Class.forName("com.oracle.truffle.tools.profiler.CPUSampler");
        } catch (LinkageError | ClassNotFoundException e) {
        }
        hasProfilerTool = c != null;
        c = null;
    }

    private static void filterBuiltins(List<PythonBuiltins> builtins) {
        PythonOS currentOs = PythonOS.getPythonOS();
        List<PythonBuiltins> toRemove = new ArrayList<>();
        for (PythonBuiltins builtin : builtins) {
            if (builtin == null) {
                toRemove.add(builtin);
            } else {
                CoreFunctions annotation = builtin.getClass().getAnnotation(CoreFunctions.class);
                if (annotation.os() != PythonOS.PLATFORM_ANY && annotation.os() != currentOs) {
                    toRemove.add(builtin);
                }
            }
        }
        builtins.removeAll(toRemove);
    }

    private static PythonBuiltins[] initializeBuiltins(boolean nativeAccessAllowed, boolean socketIOAllowed) {
        List<PythonBuiltins> builtins = new ArrayList<>(Arrays.asList(new BuiltinConstructors(),
                        new AbcModuleBuiltins(),
                        new BuiltinFunctions(),
                        new DecoratedMethodBuiltins(),
                        new ClassmethodBuiltins(),
                        new StaticmethodBuiltins(),
                        new InstancemethodBuiltins(),
                        new SimpleNamespaceBuiltins(),
                        new PolyglotModuleBuiltins(),
                        new ObjectBuiltins(),
                        new CellBuiltins(),
                        new BoolBuiltins(),
                        new FloatBuiltins(),
                        new BytesBuiltins(),
                        new ByteArrayBuiltins(),
                        new ComplexBuiltins(),
                        new TypeBuiltins(),
                        new IntBuiltins(),
                        new ForeignObjectBuiltins(),
                        new ListBuiltins(),
                        new DictBuiltins(),
                        new DictReprBuiltin(),
                        new DictViewBuiltins(),
                        new DictValuesBuiltins(),
                        new RangeBuiltins(),
                        new SliceBuiltins(),
                        new TupleBuiltins(),
                        new StringBuiltins(),
                        new BaseSetBuiltins(),
                        new SetBuiltins(),
                        new FrozenSetBuiltins(),
                        new IteratorBuiltins(),
                        new ReversedBuiltins(),
                        new PZipBuiltins(),
                        new EnumerateBuiltins(),
                        new MapBuiltins(),
                        new NotImplementedBuiltins(),
                        new NoneBuiltins(),
                        new EllipsisBuiltins(),
                        new SentinelIteratorBuiltins(),
                        new ForeignIteratorBuiltins(),
                        new GeneratorBuiltins(),
                        new CoroutineBuiltins(),
                        new CoroutineWrapperBuiltins(),
                        new CommonGeneratorBuiltins(),
                        new AbstractFunctionBuiltins(),
                        new FunctionBuiltins(),
                        new BuiltinFunctionBuiltins(),
                        new MethodDescriptorBuiltins(),
                        new WrapperDescriptorBuiltins(),
                        new AbstractMethodBuiltins(),
                        new MethodBuiltins(),
                        new AbstractBuiltinMethodBuiltins(),
                        new BuiltinFunctionOrMethodBuiltins(),
                        new MethodWrapperBuiltins(),
                        new BuiltinClassmethodBuiltins(),
                        new CodeBuiltins(),
                        new FrameBuiltins(),
                        new MappingproxyBuiltins(),
                        new DescriptorBuiltins(),
                        new GetSetDescriptorTypeBuiltins(),
                        new MemberDescriptorBuiltins(),
                        new PropertyBuiltins(),
                        new BaseExceptionBuiltins(),
                        new PosixModuleBuiltins(),
                        new NtModuleBuiltins(),
                        new WinregModuleBuiltins(),
                        new CryptModuleBuiltins(),
                        new ScandirIteratorBuiltins(),
                        new DirEntryBuiltins(),
                        new ImpModuleBuiltins(),
                        new ArrayModuleBuiltins(),
                        new ArrayBuiltins(),
                        new TermiosModuleBuiltins(),
                        new TimeModuleBuiltins(),
                        new ModuleBuiltins(),
                        new MathModuleBuiltins(),
                        new CmathModuleBuiltins(),
                        new MarshalModuleBuiltins(),
                        new RandomModuleBuiltins(),
                        new RandomBuiltins(),
                        new WeakRefModuleBuiltins(),
                        new ReferenceTypeBuiltins(),
                        new TracemallocModuleBuiltins(),
                        // contextvars
                        new ContextVarBuiltins(),
                        new ContextBuiltins(),
                        new TokenBuiltins(),
                        new ContextIteratorBuiltins(),

                        new GenericAliasBuiltins(),
                        new com.oracle.graal.python.builtins.objects.types.UnionTypeBuiltins(),
                        // exceptions
                        new SystemExitBuiltins(),
                        new ImportErrorBuiltins(),
                        new StopIterationBuiltins(),
                        new KeyErrorBuiltins(),
                        new SyntaxErrorBuiltins(),
                        new OsErrorBuiltins(),
                        new UnicodeErrorBuiltins(),
                        new UnicodeEncodeErrorBuiltins(),
                        new UnicodeDecodeErrorBuiltins(),
                        new UnicodeTranslateErrorBuiltins(),

                        // io
                        new IOModuleBuiltins(),
                        new IOBaseBuiltins(),
                        new BufferedIOBaseBuiltins(),
                        new RawIOBaseBuiltins(),
                        new TextIOBaseBuiltins(),
                        new BufferedReaderBuiltins(),
                        new BufferedWriterBuiltins(),
                        new BufferedRandomBuiltins(),
                        new BufferedReaderMixinBuiltins(),
                        new BufferedWriterMixinBuiltins(),
                        new BufferedIOMixinBuiltins(),
                        new FileIOBuiltins(),
                        new TextIOWrapperBuiltins(),
                        new IncrementalNewlineDecoderBuiltins(),
                        new BufferedRWPairBuiltins(),
                        new BytesIOBuiltins(),
                        new StringIOBuiltins(),
                        new IOBaseDictBuiltins(),

                        // _multibytecodec
                        new MultibyteCodecBuiltins(),
                        new MultibytecodecModuleBuiltins(),
                        new MultibyteIncrementalDecoderBuiltins(),
                        new MultibyteIncrementalEncoderBuiltins(),
                        new MultibyteStreamReaderBuiltins(),
                        new MultibyteStreamWriterBuiltins(),
                        new CodecCtxBuiltins(),
                        new CodecsCNModuleBuiltins(),
                        new CodecsHKModuleBuiltins(),
                        new CodecsISO2022ModuleBuiltins(),
                        new CodecsJPModuleBuiltins(),
                        new CodecsKRModuleBuiltins(),
                        new CodecsTWModuleBuiltins(),

                        new StringModuleBuiltins(),
                        new ItertoolsModuleBuiltins(),

                        // _functools
                        new KeyWrapperBuiltins(),
                        new PartialBuiltins(),
                        new LruCacheWrapperBuiltins(),
                        new FunctoolsModuleBuiltins(),

                        new ErrnoModuleBuiltins(),
                        new CodecsModuleBuiltins(),
                        new CodecsTruffleModuleBuiltins(),
                        new DequeBuiltins(),
                        new DequeIterBuiltins(),
                        new OrderedDictBuiltins(),
                        new OrderedDictKeysBuiltins(),
                        new OrderedDictValuesBuiltins(),
                        new OrderedDictItemsBuiltins(),
                        new OrderedDictIteratorBuiltins(),
                        new CollectionsModuleBuiltins(),
                        new DefaultDictBuiltins(),
                        new TupleGetterBuiltins(),
                        new JavaModuleBuiltins(),
                        new JArrayModuleBuiltins(),
                        new CSVModuleBuiltins(),
                        new JSONModuleBuiltins(),
                        new SREModuleBuiltins(),
                        new AstModuleBuiltins(),
                        PythonOptions.WITHOUT_NATIVE_POSIX && (PythonOptions.WITHOUT_JAVA_INET || !socketIOAllowed) ? null : new SelectModuleBuiltins(),
                        PythonOptions.WITHOUT_NATIVE_POSIX && (PythonOptions.WITHOUT_JAVA_INET || !socketIOAllowed) ? null : new SocketModuleBuiltins(),
                        PythonOptions.WITHOUT_NATIVE_POSIX && (PythonOptions.WITHOUT_JAVA_INET || !socketIOAllowed) ? null : new SocketBuiltins(),
                        PythonOptions.WITHOUT_PLATFORM_ACCESS ? null : new SignalModuleBuiltins(),
                        new TracebackBuiltins(),
                        new GcModuleBuiltins(),
                        new AtexitModuleBuiltins(),
                        new FaulthandlerModuleBuiltins(),
                        new UnicodeDataModuleBuiltins(),
                        new LocaleModuleBuiltins(),
                        new SysModuleBuiltins(),
                        new MemoryViewBuiltins(),
                        new SuperBuiltins(),
                        PythonOptions.WITHOUT_SSL ? null : new SSLModuleBuiltins(),
                        PythonOptions.WITHOUT_SSL ? null : new SSLContextBuiltins(),
                        PythonOptions.WITHOUT_SSL ? null : new SSLErrorBuiltins(),
                        PythonOptions.WITHOUT_SSL ? null : new SSLSocketBuiltins(),
                        PythonOptions.WITHOUT_SSL ? null : new MemoryBIOBuiltins(),
                        new BinasciiModuleBuiltins(),
                        new PosixShMemModuleBuiltins(),
                        PythonOptions.WITHOUT_PLATFORM_ACCESS ? null : new PosixSubprocessModuleBuiltins(),
                        new ReadlineModuleBuiltins(),
                        new OperatorModuleBuiltins(),

                        // hashlib
                        PythonOptions.WITHOUT_DIGEST ? null : new Md5ModuleBuiltins(),
                        PythonOptions.WITHOUT_DIGEST ? null : new Sha1ModuleBuiltins(),
                        PythonOptions.WITHOUT_DIGEST ? null : new Sha256ModuleBuiltins(),
                        PythonOptions.WITHOUT_DIGEST ? null : new Sha512ModuleBuiltins(),
                        PythonOptions.WITHOUT_DIGEST ? null : new Sha3ModuleBuiltins(),
                        PythonOptions.WITHOUT_DIGEST ? null : new Blake2ModuleBuiltins(),
                        PythonOptions.WITHOUT_DIGEST ? null : new DigestObjectBuiltins(),
                        PythonOptions.WITHOUT_DIGEST ? null : new HashObjectBuiltins(),
                        PythonOptions.WITHOUT_DIGEST ? null : new ShakeDigestObjectBuiltins(),
                        PythonOptions.WITHOUT_DIGEST ? null : new Blake2bObjectBuiltins(),
                        PythonOptions.WITHOUT_DIGEST ? null : new Blake2sObjectBuiltins(),
                        PythonOptions.WITHOUT_DIGEST ? null : new HashlibModuleBuiltins(),

                        new PyExpatModuleBuiltins(),

                        // itertools
                        new AccumulateBuiltins(),
                        new CombinationsBuiltins(),
                        new CompressBuiltins(),
                        new DropwhileBuiltins(),
                        new ChainBuiltins(),
                        new CountBuiltins(),
                        new CycleBuiltins(),
                        new FilterfalseBuiltins(),
                        new GroupByBuiltins(),
                        new GrouperBuiltins(),
                        new IsliceBuiltins(),
                        new PairwiseBuiltins(),
                        new PermutationsBuiltins(),
                        new ProductBuiltins(),
                        new RepeatBuiltins(),
                        new StarmapBuiltins(),
                        new TakewhileBuiltins(),
                        new TeeBuiltins(),
                        new TeeDataObjectBuiltins(),
                        new ZipLongestBuiltins(),

                        // zlib
                        PythonOptions.WITHOUT_COMPRESSION_LIBRARIES ? null : new ZLibModuleBuiltins(),
                        PythonOptions.WITHOUT_COMPRESSION_LIBRARIES ? null : new ZlibCompressBuiltins(),
                        PythonOptions.WITHOUT_COMPRESSION_LIBRARIES ? null : new ZlibDecompressBuiltins(),

                        new MMapModuleBuiltins(),
                        new FcntlModuleBuiltins(),
                        new MMapBuiltins(),
                        new SimpleQueueBuiltins(),
                        new QueueModuleBuiltins(),
                        new ThreadModuleBuiltins(),
                        new ThreadBuiltins(),
                        new ThreadLocalBuiltins(),
                        new LockBuiltins(),
                        new RLockBuiltins(),
                        new PwdModuleBuiltins(),
                        new ResourceModuleBuiltins(),
                        new ContextvarsModuleBuiltins(),

                        // lzma
                        PythonOptions.WITHOUT_COMPRESSION_LIBRARIES ? null : new LZMAModuleBuiltins(),
                        PythonOptions.WITHOUT_COMPRESSION_LIBRARIES ? null : new LZMACompressorBuiltins(),
                        PythonOptions.WITHOUT_COMPRESSION_LIBRARIES ? null : new LZMADecompressorBuiltins(),

                        new MultiprocessingModuleBuiltins(),
                        new SemLockBuiltins(),
                        new MultiprocessingGraalPyModuleBuiltins(),
                        new GraalPySemLockBuiltins(),

                        new WarningsModuleBuiltins(),
                        new GraalPythonModuleBuiltins(),

                        // json
                        new JSONScannerBuiltins(),
                        new JSONEncoderBuiltins(),

                        // csv
                        new CSVDialectBuiltins(),
                        new CSVReaderBuiltins(),
                        new CSVWriterBuiltins(),

                        // _ast
                        new AstBuiltins(),

                        // ctypes
                        new CArgObjectBuiltins(),
                        new CDataTypeBuiltins(),
                        new CDataTypeSequenceBuiltins(),
                        new CFieldBuiltins(),
                        new CtypesModuleBuiltins(),
                        new PyCArrayTypeBuiltins(),
                        new PyCFuncPtrBuiltins(),
                        new PyCFuncPtrTypeBuiltins(),
                        new PyCPointerTypeBuiltins(),
                        new PyCSimpleTypeBuiltins(),
                        new PyCStructTypeBuiltins(),
                        new StgDictBuiltins(),
                        new StructUnionTypeBuiltins(),
                        new StructureBuiltins(),
                        new UnionTypeBuiltins(),
                        new SimpleCDataBuiltins(),
                        new PyCArrayBuiltins(),
                        new PyCPointerBuiltins(),
                        new CDataBuiltins(),

                        // _hpy_universal, _hpy_debug, and _hpy_trace
                        new GraalHPyUniversalModuleBuiltins(),
                        new GraalHPyDebugModuleBuiltins(),
                        new GraalHPyTraceModuleBuiltins(),

                        // _asyncio
                        new AsyncioModuleBuiltins(),
                        new AsyncGeneratorBuiltins(),
                        new AsyncGenSendBuiltins(),
                        new AsyncGenThrowBuiltins()));
        if (hasProfilerTool) {
            builtins.add(new LsprofModuleBuiltins());
            builtins.add(LsprofModuleBuiltins.newProfilerBuiltins());
        }
        if (!PythonOptions.WITHOUT_COMPRESSION_LIBRARIES && (nativeAccessAllowed || ImageInfo.inImageBuildtimeCode())) {
            builtins.add(new BZ2CompressorBuiltins());
            builtins.add(new BZ2DecompressorBuiltins());
            builtins.add(new BZ2ModuleBuiltins());
        }
        ServiceLoader<PythonBuiltins> providers = ServiceLoader.load(PythonBuiltins.class, Python3Core.class.getClassLoader());
        for (PythonBuiltins builtin : providers) {
            builtins.add(builtin);
        }
        filterBuiltins(builtins);
        return builtins.toArray(new PythonBuiltins[builtins.size()]);
    }

    // not using EnumMap, HashMap, etc. to allow this to fold away during partial evaluation
    @CompilationFinal(dimensions = 1) private final PythonBuiltinClass[] builtinTypes = new PythonBuiltinClass[PythonBuiltinClassType.VALUES.length];

    private final Map<TruffleString, PythonModule> builtinModules = new HashMap<>();
    @CompilationFinal private PythonModule builtinsModule;
    @CompilationFinal private PythonModule sysModule;
    @CompilationFinal private PDict sysModules;
    @CompilationFinal private PFunction importFunc;
    @CompilationFinal private PythonModule importlib;

    @CompilationFinal private PInt pyTrue;
    @CompilationFinal private PInt pyFalse;
    @CompilationFinal private PFloat pyNaN;

    private final SysModuleState sysModuleState = new SysModuleState();

    @CompilationFinal private Object globalScopeObject;

    /*
     * This field cannot be made CompilationFinal since code might get compiled during context
     * initialization.
     */
    private volatile boolean initialized;

    private final PythonLanguage language;
    @CompilationFinal private PythonObjectSlowPathFactory objectFactory;

    public Python3Core(PythonLanguage language, boolean isNativeSupportAllowed, boolean socketIOAllowed) {
        this.language = language;
        this.builtins = initializeBuiltins(isNativeSupportAllowed, socketIOAllowed);
        this.coreFiles = initializeCoreFiles();
    }

    @CompilerDirectives.ValueType
    public static class SysModuleState {
        private int recursionLimit = ImageInfo.inImageCode() ? NATIVE_REC_LIM : REC_LIM;
        private int checkInterval = 100;
        private double switchInterval = 0.005;

        public int getRecursionLimit() {
            return recursionLimit;
        }

        public void setRecursionLimit(int recursionLimit) {
            this.recursionLimit = recursionLimit;
        }

        public int getCheckInterval() {
            return checkInterval;
        }

        public void setCheckInterval(int checkInterval) {
            this.checkInterval = checkInterval;
        }

        public double getSwitchInterval() {
            return switchInterval;
        }

        public void setSwitchInterval(double switchInterval) {
            this.switchInterval = switchInterval;
        }
    }

    public SysModuleState getSysModuleState() {
        return sysModuleState;
    }

    public final PythonContext getContext() {
        // Small hack: we know that this is the only implementation of Python3Core, this should be
        // removed once and if Python3Core is fully merged into PythonContext
        return (PythonContext) this;
    }

    public final PythonLanguage getLanguage() {
        return language;
    }

    /**
     * Checks whether the core is initialized.
     */
    public final boolean isCoreInitialized() {
        return initialized;
    }

    /**
     * Load the core library and prepare all builtin classes and modules.
     */
    public final void initialize(PythonContext context) {
        objectFactory = new PythonObjectSlowPathFactory(context.getAllocationReporter(), context.getLanguage());
        initializeJavaCore();
        initializeImportlib();
        initializePython3Core(context.getCoreHomeOrFail());
        assert SpecialMethodSlot.checkSlotOverrides(this);
        initialized = true;
    }

    private void initializeJavaCore() {
        initializeTypes();
        populateBuiltins();
        SpecialMethodSlot.initializeBuiltinsSpecialMethodSlots(this);
        publishBuiltinModules();
        builtinsModule = builtinModules.get(BuiltinNames.T_BUILTINS);
    }

    private void initializeImportlib() {
        PythonModule bootstrap = ImpModuleBuiltins.importFrozenModuleObject(this, T__FROZEN_IMPORTLIB, false);
        PythonModule bootstrapExternal;

        PyObjectCallMethodObjArgs callNode = PyObjectCallMethodObjArgs.getUncached();
        WriteAttributeToDynamicObjectNode writeNode = WriteAttributeToDynamicObjectNode.getUncached();
        ReadAttributeFromDynamicObjectNode readNode = ReadAttributeFromDynamicObjectNode.getUncached();
        PyDictSetItem setItem = PyDictSetItem.getUncached();

        // first, a workaround since postInitialize hasn't run yet for the _weakref module aliases
        writeNode.execute(lookupBuiltinModule(T__WEAKREF), T_REF, lookupType(PythonBuiltinClassType.PReferenceType));

        if (bootstrap == null) {
            // true when the frozen module is not available
            bootstrapExternal = createModule(T_IMPORTLIB_BOOTSTRAP_EXTERNAL);
            bootstrap = createModule(T_IMPORTLIB_BOOTSTRAP);
            loadFile(toTruffleStringUncached("importlib/_bootstrap_external"), getContext().getStdlibHome(), bootstrapExternal);
            loadFile(toTruffleStringUncached("importlib/_bootstrap"), getContext().getStdlibHome(), bootstrap);
        } else {
            bootstrapExternal = ImpModuleBuiltins.importFrozenModuleObject(this, T__FROZEN_IMPORTLIB_EXTERNAL, true);
            setItem.execute(null, null, sysModules, T_IMPORTLIB_BOOTSTRAP, bootstrap);
            setItem.execute(null, null, sysModules, T_IMPORTLIB_BOOTSTRAP_EXTERNAL, bootstrapExternal);
            LOGGER.log(Level.FINE, () -> "import '" + T__FROZEN_IMPORTLIB + "' # <frozen>");
            LOGGER.log(Level.FINE, () -> "import '" + T__FROZEN_IMPORTLIB_EXTERNAL + "' # <frozen>");
        }
        setItem.execute(null, null, sysModules, T__FROZEN_IMPORTLIB, bootstrap);
        setItem.execute(null, null, sysModules, T__FROZEN_IMPORTLIB_EXTERNAL, bootstrapExternal);

        // __package__ needs to be set and doesn't get set by _bootstrap setup
        writeNode.execute(bootstrap, T___PACKAGE__, T_IMPORTLIB);
        writeNode.execute(bootstrapExternal, T___PACKAGE__, T_IMPORTLIB);

        callNode.execute(null, null, bootstrap, toTruffleStringUncached("_install"), getSysModule(), lookupBuiltinModule(T__IMP));
        writeNode.execute(getBuiltins(), T___IMPORT__, readNode.execute(bootstrap, T___IMPORT__));
        // see CPython's init_importlib_external
        callNode.execute(null, null, bootstrap, toTruffleStringUncached("_install_external_importers"));
        if (!PythonOptions.WITHOUT_COMPRESSION_LIBRARIES) {
            // see CPython's _PyImportZip_Init
            Object pathHooks = readNode.execute(sysModule, toTruffleStringUncached("path_hooks"));
            if (!(pathHooks instanceof PList pathHooksList)) {
                LOGGER.log(Level.FINE, () -> "unable to get sys.path_hooks");
                LOGGER.log(Level.FINE, () -> "initializing zipimport failed");
            } else {
                LOGGER.log(Level.FINE, () -> "# installing zipimport hook");
                TruffleString t_zipimport = toTruffleStringUncached("zipimport");
                PythonModule zipimport = ImpModuleBuiltins.importFrozenModuleObject(this, t_zipimport, false);
                if (zipimport == null) {
                    // true when the frozen module is not available
                    zipimport = createModule(t_zipimport);
                    try {
                        loadFile(t_zipimport, getContext().getStdlibHome(), zipimport);
                    } catch (PException e) {
                        zipimport = null;
                        removeBuiltinModule(t_zipimport);
                    }
                } else {
                    setItem.execute(null, null, sysModules, t_zipimport, zipimport);
                    LOGGER.log(Level.FINE, () -> "import 'zipimport' # <frozen>");
                }
                if (zipimport == null) {
                    LOGGER.log(Level.FINE, () -> "# can't import zipimport");
                } else {
                    writeNode.execute(zipimport, T___BUILTINS__, getBuiltins());
                    Object zipimporter = readNode.execute(zipimport, toTruffleStringUncached("zipimporter"));
                    if (zipimporter == PNone.NO_VALUE) {
                        LOGGER.log(Level.FINE, () -> "# can't import zipimport.zipimporter");
                    } else {
                        SequenceStorage store = pathHooksList.getSequenceStorage();
                        pathHooksList.setSequenceStorage(SequenceStorageNodes.InsertItemNode.executeUncached(store, 0, zipimporter));
                        LOGGER.log(Level.FINE, () -> "# installed zipimport hook");
                    }
                }
            }
        }

        importFunc = (PFunction) readNode.execute(bootstrap, T___IMPORT__);
        importlib = bootstrap;

        PythonBuiltinClass moduleType = lookupType(PythonBuiltinClassType.PythonModule);
        writeNode.execute(moduleType, T___REPR__, readNode.execute(bootstrap, toTruffleStringUncached("_module_repr")));
        SpecialMethodSlot.reinitializeSpecialMethodSlots(moduleType, getLanguage());
    }

    private void initializePython3Core(TruffleString coreHome) {
        loadFile(BuiltinNames.T_BUILTINS, coreHome);
        for (TruffleString s : coreFiles) {
            loadFile(s, coreHome);
        }
        initialized = true;
    }

    /**
     * Run post-initialization code that needs a fully working Python environment. This will be run
     * eagerly when the context is initialized on the JVM or a new context is created on SVM, but is
     * omitted when the native image is generated.
     */
    public final void postInitialize() {
        if (!ImageInfo.inImageBuildtimeCode() || ImageInfo.inImageRuntimeCode()) {
            initialized = false;

            for (PythonBuiltins builtin : builtins) {
                CoreFunctions annotation = builtin.getClass().getAnnotation(CoreFunctions.class);
                if (annotation.isEager() || annotation.extendClasses().length != 0) {
                    builtin.postInitialize(this);
                }
            }

            /*
             * Special case for _bz2: If native access is not allowed, we cannot use the built-in
             * implementation that would call libbz2 via NFI. Therefore, we remove it from the
             * built-in modules map (and also from sys.modules if already loaded). This will cause a
             * fallback to another _bz2 implementation (e.g. LLVM or maybe some Java lib). This
             * needs to be done here and cannot be done in 'initializeBuiltins' because then we
             * would never include the intrinsified _bz2 module in the native image since native
             * access is never allowed during native image build time.
             */
            if (!PythonOptions.WITHOUT_COMPRESSION_LIBRARIES && ImageInfo.inImageCode() && !getContext().isNativeAccessAllowed()) {
                removeBuiltinModule(BuiltinNames.T_BZ2);
            }

            globalScopeObject = PythonMapScope.createTopScope(getContext());
            getContext().getSharedFinalizer().registerAsyncAction();

            if (!PythonOptions.AUTOMATIC_ASYNC_ACTIONS) {
                if (getContext().getEnv().isPolyglotBindingsAccessAllowed()) {
                    getContext().getEnv().exportSymbol("PollPythonAsyncActions", getContext().getEnv().asGuestValue(new Runnable() {
                        @Override
                        public void run() {
                            getContext().pollAsyncActions();
                        }
                    }));
                } else {
                    LOGGER.log(Level.SEVERE, "AutomaticAsyncActions are disabled, but PolyglotBindingsAccess is not allowed. " +
                                    "Cannot expose `PollPythonAsyncActions'. If this is not called regularly, Python will leak memory.");
                }
            }

            initialized = true;
        }
    }

    @TruffleBoundary
    public final void removeBuiltinModule(TruffleString name) {
        assert !initialized : "can only remove builtin modules before initialization is finished";
        builtinModules.remove(name);
        if (sysModules != null) {
            // may already be published
            sysModules.delItem(name);
        }
    }

    @TruffleBoundary
    public final PythonModule lookupBuiltinModule(TruffleString name) {
        return builtinModules.get(name);
    }

    public final PythonBuiltinClass lookupType(PythonBuiltinClassType type) {
        assert builtinTypes[type.ordinal()] != null;
        return builtinTypes[type.ordinal()];
    }

    /**
     * Returns an array whose runtime type is {@code Object[]}, but all elements are instances of
     * {@link TruffleString}.
     */
    @TruffleBoundary
    public final Object[] builtinModuleNames() {
        return builtinModules.keySet().toArray();
    }

    public final PythonModule getBuiltins() {
        return builtinsModule;
    }

    public final void registerTypeInBuiltins(TruffleString name, PythonBuiltinClassType type) {
        assert builtinsModule != null : "builtins module was not yet initialized: cannot register type";
        builtinsModule.setAttribute(name, lookupType(type));
    }

    public final PythonModule getSysModule() {
        return sysModule;
    }

    public final PDict getSysModules() {
        return sysModules;
    }

    public final PythonModule getImportlib() {
        return importlib;
    }

    public final PFunction getImportFunc() {
        return importFunc;
    }

    /**
     * Returns the stderr object or signals error when stderr is "lost".
     */
    public final Object getStderr() {
        try {
            return PyObjectLookupAttr.executeUncached(sysModule, T_STDERR);
        } catch (PException e) {
            try {
                getContext().getEnv().err().write("lost sys.stderr\n".getBytes());
            } catch (IOException ioe) {
                // nothing more we can do
            }
            throw e;
        }
    }

    private void publishBuiltinModules() {
        assert sysModules != null;
        for (Entry<TruffleString, PythonModule> entry : builtinModules.entrySet()) {
            final PythonModule pythonModule = entry.getValue();
            final PythonBuiltins moduleBuiltins = pythonModule.getBuiltins();
            if (moduleBuiltins != null) {
                CoreFunctions annotation = moduleBuiltins.getClass().getAnnotation(CoreFunctions.class);
                if (annotation.isEager()) {
                    sysModules.setItem(entry.getKey(), pythonModule);
                }
            }
        }
    }

    private PythonBuiltinClass initializeBuiltinClass(PythonBuiltinClassType type) {
        int index = type.ordinal();
        if (builtinTypes[index] == null) {
            if (type.getBase() == null) {
                // object case
                builtinTypes[index] = new PythonBuiltinClass(getLanguage(), type, null);
            } else {
                builtinTypes[index] = new PythonBuiltinClass(getLanguage(), type, initializeBuiltinClass(type.getBase()));
            }
        }
        return builtinTypes[index];
    }

    private void initializeTypes() {
        // create class objects for builtin types
        for (PythonBuiltinClassType builtinClass : PythonBuiltinClassType.VALUES) {
            initializeBuiltinClass(builtinClass);
        }
        // n.b.: the builtin modules and classes and their constructors are initialized first here,
        // so we have the mapping from java classes to python classes and builtin names to modules
        // available.
        for (PythonBuiltins builtin : builtins) {
            CoreFunctions annotation = builtin.getClass().getAnnotation(CoreFunctions.class);
            if (annotation.defineModule().length() > 0) {
                createModule(toTruffleStringUncached(annotation.defineModule()), builtin);
            }
        }
        // publish builtin types in the corresponding modules
        for (PythonBuiltinClassType builtinClass : PythonBuiltinClassType.VALUES) {
            TruffleString module = builtinClass.getPublishInModule();
            if (module != null) {
                PythonModule pythonModule = lookupBuiltinModule(module);
                if (pythonModule != null) {
                    pythonModule.setAttribute(builtinClass.getName(), lookupType(builtinClass));
                }
                if (module.toJavaStringUncached().equals(J_POSIX)) {
                    // special case of aliased posix==nt
                    pythonModule = lookupBuiltinModule(T_NT);
                    if (pythonModule != null) {
                        pythonModule.setAttribute(builtinClass.getName(), lookupType(builtinClass));
                    }
                }
            }
        }
        // now initialize well-known objects
        pyTrue = factory().createInt(PythonBuiltinClassType.Boolean, BigInteger.ONE);
        pyFalse = factory().createInt(PythonBuiltinClassType.Boolean, BigInteger.ZERO);
        pyNaN = factory().createFloat(Double.NaN);
    }

    private void populateBuiltins() {
        for (PythonBuiltins builtin : builtins) {
            builtin.initialize(this);
            CoreFunctions annotation = builtin.getClass().getAnnotation(CoreFunctions.class);
            if (annotation.defineModule().length() > 0) {
                PythonModule module = builtinModules.get(toTruffleStringUncached(annotation.defineModule()));
                if (module != null) {
                    addBuiltinsTo(module, builtin);
                }
            }
            if (annotation.extendsModule().length() > 0) {
                PythonModule module = builtinModules.get(toTruffleStringUncached(annotation.extendsModule()));
                if (module != null) {
                    addBuiltinsTo(module, builtin);
                }
            }
            for (PythonBuiltinClassType klass : annotation.extendClasses()) {
                addBuiltinsTo(lookupType(klass), builtin);
            }
        }

        // core machinery
        sysModule = builtinModules.get(T_SYS);
        sysModules = (PDict) sysModule.getAttribute(T_MODULES);
    }

    private PythonModule createModule(TruffleString name) {
        return createModule(name, null);
    }

    private void addBuiltinModule(TruffleString name, PythonModule module) {
        builtinModules.put(name, module);
        if (sysModules != null) {
            sysModules.setItem(name, module);
        }
    }

    private PythonModule createModule(TruffleString name, PythonBuiltins moduleBuiltins) {
        PythonModule mod = builtinModules.get(name);
        if (mod == null) {
            mod = factory().createPythonModule(name);
            if (moduleBuiltins != null) {
                mod.setBuiltins(moduleBuiltins);
            }
            addBuiltinModule(name, mod);
        }
        return mod;
    }

    private void addBuiltinsTo(PythonObject obj, PythonBuiltins builtinsForObj) {
        builtinsForObj.addConstantsToModuleObject(obj);
        builtinsForObj.addFunctionsToModuleObject(obj, objectFactory);
    }

    @TruffleBoundary
    private Source getInternalSource(TruffleString basename, TruffleString prefix) {
        PythonContext ctxt = getContext();
        Env env = ctxt.getEnv();
        String suffix = env.getFileNameSeparator() + basename + J_PY_EXTENSION;
        TruffleFile file = env.getInternalTruffleFile(prefix + suffix);
        String errorMessage;
        try {
            return PythonLanguage.newSource(ctxt, file, basename.toJavaStringUncached());
        } catch (IOException e) {
            errorMessage = "Startup failed, could not read core library from " + file + ". Maybe you need to set python.CoreHome and python.StdLibHome.";
        } catch (SecurityException e) {
            errorMessage = "Startup failed, a security exception occurred while reading from " + file + ". Maybe you need to set python.CoreHome and python.StdLibHome.";
        }
        LOGGER.log(Level.SEVERE, errorMessage);
        RuntimeException e = new RuntimeException(errorMessage);
        throw e;
    }

    private void loadFile(TruffleString s, TruffleString prefix) {
        PythonModule mod = lookupBuiltinModule(s);
        if (mod == null) {
            // use an anonymous module for the side-effects
            mod = factory().createPythonModule(T___ANONYMOUS__);
        }
        loadFile(s, prefix, mod);
    }

    private void loadFile(TruffleString s, TruffleString prefix, PythonModule mod) {
        if (ImpModuleBuiltins.importFrozenModuleObject(this, cat(T_GRAALPYTHON, T_DOT, s), false, mod) != null) {
            LOGGER.log(Level.FINE, () -> "import '" + s + "' # <frozen>");
            return;
        }
        Supplier<CallTarget> getCode = () -> {
            Source source = getInternalSource(s, prefix);
            return getLanguage().parse(getContext(), source, InputType.FILE, false, 0, false, null, EnumSet.noneOf(FutureFeature.class));
        };
        RootCallTarget callTarget = (RootCallTarget) getLanguage().cacheCode(s, getCode);
        GenericInvokeNode.getUncached().execute(callTarget, PArguments.withGlobals(mod));
    }

    public final PythonObjectSlowPathFactory factory() {
        return objectFactory;
    }

    public final PInt getTrue() {
        return pyTrue;
    }

    public final PInt getFalse() {
        return pyFalse;
    }

    public final PFloat getNaN() {
        return pyNaN;
    }

    public final Object getTopScopeObject() {
        return globalScopeObject;
    }

    public static void writeInfo(String message) {
        PythonLanguage.getLogger(Python3Core.class).fine(message);
    }

    public static void writeInfo(Supplier<String> messageSupplier) {
        PythonLanguage.getLogger(Python3Core.class).fine(messageSupplier);
    }
}
