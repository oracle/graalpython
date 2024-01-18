/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.removeNativeWeakRef;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_weaklistoffset;
import static com.oracle.graal.python.nodes.BuiltinNames.J__WEAKREF;
import static com.oracle.graal.python.nodes.BuiltinNames.T__WEAKREF;
import static com.oracle.graal.python.nodes.StringLiterals.T_REF;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType.WeakRefStorage;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__WEAKREF, isEager = true)
public final class WeakRefModuleBuiltins extends PythonBuiltins {
    private static final HiddenKey weakRefQueueKey = new HiddenKey("weakRefQueue");
    private final ReferenceQueue<Object> weakRefQueue = new ReferenceQueue<>();

    // This GraalPy specific as CPython is storing weakref list within a PyObject (obj +
    // tp_weaklistoffset)
    public static final HiddenKey __WEAKLIST__ = new HiddenKey("__weaklist__");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return WeakRefModuleBuiltinsFactory.getFactories();
    }

    public static int getBuiltinTypeWeaklistoffset(PythonBuiltinClassType cls) {
        // @formatter:off
        return switch (cls) {
            case PythonObject, // object
                    PInt, // int
                    Boolean, // bool
                    PByteArray, // bytearray
                    PBytes, // bytes
                    PList, // list
                    PNone, // NoneType
                    PNotImplemented, // NotImplementedType
                    PTraceback, // traceback
                    Super, // super
                    PRange, // range
                    PDict, // dict
                    PDictKeysView, // dict_keys
                    PDictValuesView, // dict_values
                    PDictItemsView, // dict_items
                    PDictReverseKeyIterator, // dict_reversekeyiterator
                    PDictReverseValueIterator, // dict_reversevalueiterator
                    PDictReverseItemIterator, // dict_reverseitemiterator
                    PString, // str
                    PSlice, // slice
                    PStaticmethod, // staticmethod
                    PComplex, // complex
                    PFloat, // float
                    PProperty, // property
                    PTuple, // tuple
                    PEnumerate, // enumerate
                    PReverseIterator, // reversed
                    PFrame, // frame
                    PMappingproxy, // mappingproxy
                    GetSetDescriptor, // getset_descriptor
                    WrapperDescriptor, // wrapper_descriptor
                    MethodWrapper, // method-wrapper
                    PEllipsis, // ellipsis
                    MemberDescriptor, // member_descriptor
                    PSimpleNamespace, // types.SimpleNamespace
                    Capsule, // PyCapsule
                    PCell, // cell
                    PInstancemethod, // instancemethod
                    PBuiltinClassMethod, // classmethod_descriptor
                    PBuiltinFunction, // method_descriptor
                    PSentinelIterator, // callable_iterator
                    PIterator, // iterator
                    PCoroutineWrapper, // coroutine_wrapper
                    PEncodingMap, // EncodingMap
                    PIntInfo, // sys.int_info
                    PBaseException, // BaseException
                    Exception, // Exception
                    TypeError, // TypeError
                    StopAsyncIteration, // StopAsyncIteration
                    StopIteration, // StopIteration
                    GeneratorExit, // GeneratorExit
                    SystemExit, // SystemExit
                    KeyboardInterrupt, // KeyboardInterrupt
                    ImportError, // ImportError
                    ModuleNotFoundError, // ModuleNotFoundError
                    OSError, // OSError
                    EOFError, // EOFError
                    RuntimeError, // RuntimeError
                    RecursionError, // RecursionError
                    NotImplementedError, // NotImplementedError
                    NameError, // NameError
                    UnboundLocalError, // UnboundLocalError
                    AttributeError, // AttributeError
                    SyntaxError, // SyntaxError
                    IndentationError, // IndentationError
                    TabError, // TabError
                    LookupError, // LookupError
                    IndexError, // IndexError
                    KeyError, // KeyError
                    ValueError, // ValueError
                    UnicodeError, // UnicodeError
                    UnicodeEncodeError, // UnicodeEncodeError
                    UnicodeDecodeError, // UnicodeDecodeError
                    UnicodeTranslateError, // UnicodeTranslateError
                    AssertionError, // AssertionError
                    ArithmeticError, // ArithmeticError
                    FloatingPointError, // FloatingPointError
                    OverflowError, // OverflowError
                    ZeroDivisionError, // ZeroDivisionError
                    SystemError, // SystemError
                    ReferenceError, // ReferenceError
                    MemoryError, // MemoryError
                    BufferError, // BufferError
                    Warning, // Warning
                    UserWarning, // UserWarning
                    DeprecationWarning, // DeprecationWarning
                    PendingDeprecationWarning, // PendingDeprecationWarning
                    SyntaxWarning, // SyntaxWarning
                    RuntimeWarning, // RuntimeWarning
                    FutureWarning, // FutureWarning
                    ImportWarning, // ImportWarning
                    UnicodeWarning, // UnicodeWarning
                    BytesWarning, // BytesWarning
                    ResourceWarning, // ResourceWarning
                    ConnectionError, // ConnectionError
                    BlockingIOError, // BlockingIOError
                    BrokenPipeError, // BrokenPipeError
                    ChildProcessError, // ChildProcessError
                    ConnectionAbortedError, // ConnectionAbortedError
                    ConnectionRefusedError, // ConnectionRefusedError
                    ConnectionResetError, // ConnectionResetError
                    FileExistsError, // FileExistsError
                    FileNotFoundError, // FileNotFoundError
                    IsADirectoryError, // IsADirectoryError
                    NotADirectoryError, // NotADirectoryError
                    InterruptedError, // InterruptedError
                    PermissionError, // PermissionError
                    ProcessLookupError, // ProcessLookupError
                    TimeoutError, // TimeoutError
                    PFloatInfo, // sys.float_info
                    PythonModuleDef, // moduledef
                    PHashInfo, // sys.hash_info
                    PVersionInfo, // sys.version_info
                    PFlags, // sys.flags
                    PThreadInfo, // sys.thread_info
                    PMap, // map
                    PZip, // zip
                    PClassmethod, // classmethod
                    PBytesIOBuf, // _io._BytesIOBuffer
                    PIncrementalNewlineDecoder, // _io.IncrementalNewlineDecoder
                    PStatResult, // os.stat_result
                    PStatvfsResult, // os.statvfs_result
                    PTerminalSize, // os.terminal_size
                    PScandirIterator, // posix.ScandirIterator
                    PDirEntry, // posix.DirEntry
                    PUnameResult, // posix.uname_result
                    PStructTime, // time.struct_time
                    PDictItemIterator, // dict_itemiterator
                    PDictKeyIterator, // dict_keyiterator
                    PDictValueIterator, // dict_valueiterator
                    PAccumulate, // itertools.accumulate
                    PCombinations, // itertools.combinations
                    PCombinationsWithReplacement, // itertools.combinations_with_replacement
                    PCycle, // itertools.cycle
                    PDropwhile, // itertools.dropwhile
                    PTakewhile, // itertools.takewhile
                    PIslice, // itertools.islice
                    PStarmap, // itertools.starmap
                    PChain, // itertools.chain
                    PCompress, // itertools.compress
                    PFilterfalse, // itertools.filterfalse
                    PCount, // itertools.count
                    PZipLongest, // itertools.zip_longest
                    PPermutations, // itertools.permutations
                    PProduct, // itertools.product
                    PRepeat, // itertools.repeat
                    PGroupBy, // itertools.groupby
                    PTeeDataObject, // itertools._tee_dataobject
                    PDefaultDict, // collections.defaultdict
                    PDequeIter, // _collections._deque_iterator
                    PDequeRevIter, // _collections._deque_reverse_iterator
                    PTupleGetter // _collections._tuplegetter
                    -> 0;
            case PythonClass -> 368; // type
            case PSet, // set
                    PFrozenSet // frozenset
                    -> 192;
            case PMemoryView, // memoryview
                    PCode // code
                    -> 136;
            case PBuiltinFunctionOrMethod, // builtin_function_or_method
                    PGenerator, // generator
                    PCoroutine, // coroutine
                    PythonModule, // module
                    PThreadLocal, // _thread._local
                    PRLock, // _thread.RLock
                    PBufferedRWPair, // _io.BufferedRWPair
                    PAsyncGenerator // async_generator
                    -> 40;
            case PMethod, // method
                    PFileIO, // _io.FileIO
                    PTee // itertools._tee
                    -> 32;
            case PFunction -> 80; // function
            case PIOBase, // _io._IOBase
                    PRawIOBase, // _io._RawIOBase
                    PBufferedIOBase, // _io._BufferedIOBase
                    PTextIOBase // _io._TextIOBase
                    -> 24;
            case PBytesIO, // _io.BytesIO
                    PPartial // functools.partial
                    -> 48;
            case PStringIO -> 112; // _io.StringIO
            case PBufferedReader, // _io.BufferedReader
                    PBufferedWriter, // _io.BufferedWriter
                    PBufferedRandom, // _io.BufferedRandom
                    PLruCacheWrapper
                    -> 144;
            case PTextIOWrapper -> 176; // _io.TextIOWrapper

            default -> -1; // unknown or not implemented
            // @formatter:on
        };
    }

    private static class WeakrefCallbackAction extends AsyncHandler.AsyncPythonAction {
        private final WeakRefStorage[] references;
        private int index;

        public WeakrefCallbackAction(WeakRefStorage[] weakRefStorages) {
            this.references = weakRefStorages;
            this.index = 0;
        }

        @Override
        public Object callable() {
            return references[index].getCallback();
        }

        @Override
        public Object[] arguments() {
            return new Object[]{references[index].getRef()};
        }

        @Override
        public boolean proceed() {
            index++;
            return index < references.length;
        }

        @Override
        protected void handleException(PException e) {
            /*
             * see 'weakrefobject.c:handle_callback'
             */
            WriteUnraisableNode.getUncached().execute(e.getEscapedException(), null, callable());
        }
    }

    private static final TruffleString T_PROXY_TYPE = tsLiteral("ProxyType");
    private static final TruffleString T_CALLABLE_PROXY_TYPE = tsLiteral("CallableProxyType");

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule weakrefModule = core.lookupBuiltinModule(T__WEAKREF);
        weakrefModule.setAttribute(weakRefQueueKey, weakRefQueue);
        PythonBuiltinClass refType = core.lookupType(PythonBuiltinClassType.PReferenceType);
        weakrefModule.setAttribute(T_REF, refType);
        refType.setAttribute(weakRefQueueKey, weakRefQueue);
        // FIXME we should intrinsify those types
        TypeNodes.SetTypeFlagsNode.executeUncached(weakrefModule.getAttribute(T_PROXY_TYPE), TypeFlags.DEFAULT | TypeFlags.HAVE_GC);
        TypeNodes.SetTypeFlagsNode.executeUncached(weakrefModule.getAttribute(T_CALLABLE_PROXY_TYPE), TypeFlags.DEFAULT | TypeFlags.HAVE_GC);
        final PythonContext ctx = core.getContext();
        core.getContext().registerAsyncAction(() -> {
            if (!ctx.isGcEnabled()) {
                return null;
            }
            Reference<? extends Object> reference = null;
            if (PythonOptions.AUTOMATIC_ASYNC_ACTIONS) {
                try {
                    reference = weakRefQueue.remove();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                reference = weakRefQueue.poll();
            }
            ArrayList<PReferenceType.WeakRefStorage> refs = new ArrayList<>();
            do {
                if (reference instanceof PReferenceType.WeakRefStorage ref) {
                    long ptr = ref.getPointer();
                    if (ptr > 0) {
                        if (!ctx.isFinalizing()) {
                            /* avoid race condition of deallocating an object at exit */
                            refs.add(ref);
                            removeNativeWeakRef(ctx, ptr);
                        }
                    } else {
                        refs.add(ref);
                    }
                }
                reference = weakRefQueue.poll();
            } while (reference != null);
            if (!refs.isEmpty()) {
                return new WeakrefCallbackAction(refs.toArray(new PReferenceType.WeakRefStorage[0]));
            }
            return null;
        });
    }

    // ReferenceType constructor
    @Builtin(name = "ReferenceType", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PReferenceType)
    @GenerateNodeFactory
    public abstract static class ReferenceTypeNode extends PythonTernaryBuiltinNode {
        @Child private ReadAttributeFromObjectNode readQueue = ReadAttributeFromObjectNode.create();
        @Child private CStructAccess.ReadI64Node getTpWeaklistoffsetNode;

        @Specialization(guards = "!isNativeObject(object)")
        @SuppressWarnings("truffle-static-method")
        PReferenceType refType(Object cls, Object object, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached GetClassNode getClassNode,
                        @Cached ReadAttributeFromObjectNode getAttrNode,
                        @Cached WriteAttributeToDynamicObjectNode setAttrNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            Object obj = object;
            if (object instanceof PythonBuiltinClassType tobj) {
                obj = getContext().getCore().lookupType(tobj);
            }

            Object clazz = getClassNode.execute(inliningTarget, obj);
            boolean allowed = true;
            if (clazz instanceof PythonBuiltinClassType type) {
                allowed = type.getWeaklistoffset() != 0;
            }
            if (!allowed) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CANNOT_CREATE_WEAK_REFERENCE_TO, obj);
            }
            Object wr = getAttrNode.execute(obj, __WEAKLIST__);
            if (wr != PNone.NO_VALUE) {
                return (PReferenceType) wr; // is must be a PReferenceType instance.
            }

            PReferenceType ref = factory.createReferenceType(cls, obj, null, getWeakReferenceQueue());
            setAttrNode.execute(obj, __WEAKLIST__, ref);
            return ref;
        }

        @Specialization(guards = {"!isNativeObject(object)", "!isPNone(callback)"})
        PReferenceType refTypeWithCallback(Object cls, Object object, Object callback,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createReferenceType(cls, object, callback, getWeakReferenceQueue());
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        PReferenceType refType(Object cls, PythonAbstractNativeObject pythonObject, Object callback,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached GetClassNode getClassNode,
                        @Cached IsBuiltinClassExactProfile profile,
                        @Cached GetMroNode getMroNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            Object actualCallback = callback instanceof PNone ? null : callback;
            Object clazz = getClassNode.execute(inliningTarget, pythonObject);

            // if the object is a type, a weak ref is allowed
            boolean allowed = false;
            if (profile.profileClass(inliningTarget, clazz, PythonBuiltinClassType.PythonClass)) {
                allowed = true;
            } else {
                // if the object's type is a native type, we need to consider 'tp_weaklistoffset'
                if (PGuards.isNativeClass(clazz) || clazz instanceof PythonClass && ((PythonClass) clazz).needsNativeAllocation()) {
                    for (Object base : getMroNode.execute(inliningTarget, clazz)) {
                        if (PGuards.isNativeClass(base)) {
                            if (getTpWeaklistoffsetNode == null) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                getTpWeaklistoffsetNode = insert(CStructAccess.ReadI64Node.create());
                            }
                            long tpWeaklistoffset = getTpWeaklistoffsetNode.readFromObj((PythonNativeClass) base, PyTypeObject__tp_weaklistoffset);
                            if (tpWeaklistoffset != 0) {
                                allowed = true;
                                break;
                            }
                        } else if (base instanceof PythonClass /* not PythonBuiltinClass */) {
                            // any subclass of a normal (non-builtin) class supports weakrefs
                            allowed = true;
                            break;
                        }
                    }
                }
            }
            if (allowed) {
                CApiTransitions.addNativeWeakRef(getContext(), pythonObject);
                return factory.createReferenceType(cls, pythonObject, actualCallback, getWeakReferenceQueue());
            } else {
                return refType(cls, pythonObject, actualCallback, raiseNode.get(inliningTarget));
            }
        }

        @Fallback
        static PReferenceType refType(@SuppressWarnings("unused") Object cls, Object object, @SuppressWarnings("unused") Object callback,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.CANNOT_CREATE_WEAK_REFERENCE_TO, object);
        }

        @SuppressWarnings("unchecked")
        private ReferenceQueue<Object> getWeakReferenceQueue() {
            Object queueObject = readQueue.execute(getContext().lookupType(PythonBuiltinClassType.PReferenceType), weakRefQueueKey);
            if (queueObject instanceof ReferenceQueue) {
                ReferenceQueue<Object> queue = (ReferenceQueue<Object>) queueObject;
                return queue;
            } else {
                if (getContext().isCoreInitialized()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("the weak reference queue was modified!");
                } else {
                    // returning a null reference queue is fine, it just means
                    // that the finalizer won't run
                    return null;
                }
            }
        }
    }

    // getweakrefcount(obj)
    @Builtin(name = "getweakrefcount", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetWeakRefCountNode extends PythonBuiltinNode {
        @Specialization
        public Object getCount(PReferenceType pReferenceType) {
            return pReferenceType.getWeakRefCount();
        }

        @Fallback
        public Object getCount(@SuppressWarnings("unused") Object none) {
            return 0;
        }
    }

    // getweakrefs()
    @Builtin(name = "getweakrefs", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetWeakRefsNode extends PythonBuiltinNode {
        @Specialization
        public Object getRefs(@SuppressWarnings("unused") Object object) {
            return PNone.NONE;
        }
    }

    // _remove_dead_weakref()
    @Builtin(name = "_remove_dead_weakref", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RemoveDeadWeakRefsNode extends PythonBuiltinNode {
        @Specialization
        public Object removeDeadRefs(@SuppressWarnings("unused") PDict dict, @SuppressWarnings("unused") Object key) {
            // TODO: do the removal from the dict ... (_weakref.c:60)
            return PNone.NONE;
        }
    }
}
