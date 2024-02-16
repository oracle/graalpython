/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import static com.oracle.graal.python.builtins.objects.object.PythonObject.CLASS_CHANGED_FLAG;
import static com.oracle.graal.python.builtins.objects.object.PythonObject.HAS_MATERIALIZED_DICT;
import static com.oracle.graal.python.nodes.BuiltinNames.J___GRAALPYTHON_INTEROP_BEHAVIOR__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___BASICSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___FLAGS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ITEMSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___VECTORCALLOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___WEAKLISTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ALLOC__;

import com.oracle.graal.python.builtins.modules.hashlib.HashlibModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.HiddenAttrFactory.ReadNodeGen;
import com.oracle.graal.python.nodes.HiddenAttrFactory.WriteNodeGen;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;

public final class HiddenAttr {

    public static final HiddenAttr OBJECT_ID = new HiddenAttr("_id");                   // ObjectNodes
    public static final HiddenAttr CLASS = new HiddenAttr("ob_type");
    public static final HiddenAttr DICT = new HiddenAttr("ob_dict");
    public static final HiddenAttr DICTOFFSET = new HiddenAttr(J___DICTOFFSET__);
    public static final HiddenAttr WEAKLISTOFFSET = new HiddenAttr(J___WEAKLISTOFFSET__);
    public static final HiddenAttr ITEMSIZE = new HiddenAttr(J___ITEMSIZE__);
    public static final HiddenAttr BASICSIZE = new HiddenAttr(J___BASICSIZE__);
    public static final HiddenAttr ALLOC = new HiddenAttr(J___ALLOC__);
    public static final HiddenAttr DEALLOC = new HiddenAttr("__dealloc__");
    public static final HiddenAttr DEL = new HiddenAttr("__del__");
    public static final HiddenAttr FREE = new HiddenAttr("__free__");
    public static final HiddenAttr CLEAR = new HiddenAttr("__clear__");
    public static final HiddenAttr AS_BUFFER = new HiddenAttr("__tp_as_buffer__");
    public static final HiddenAttr FLAGS = new HiddenAttr(J___FLAGS__);
    public static final HiddenAttr VECTORCALL_OFFSET = new HiddenAttr(J___VECTORCALLOFFSET__);
    public static final HiddenAttr GETBUFFER = new HiddenAttr("__getbuffer__");
    public static final HiddenAttr RELEASEBUFFER = new HiddenAttr("__releasebuffer__");
    public static final HiddenAttr DOC = new HiddenAttr(J___DOC__);

    public static final HiddenAttr WEAKLIST = new HiddenAttr("__weaklist__");           // _weakref
    public static final HiddenAttr WEAK_REF_QUEUE = new HiddenAttr("weakRefQueue");     // _weakref
    public static final HiddenAttr ENCODER_OBJECT = new HiddenAttr("encoder_object");   // cjkcodecs
    public static final HiddenAttr DECODER_OBJECT = new HiddenAttr("decoder_object");   // cjkcodecs
    public static final HiddenAttr KWD_MARK = new HiddenAttr("kwd_mark");               // functools
    public static final HiddenAttr ORIGINAL_CONSTRUCTORS = new HiddenAttr(HashlibModuleBuiltins.J_CONSTRUCTORS);    // hashlib
    public static final HiddenAttr PICKLE_STATE = new HiddenAttr("state");              // pickle
    public static final HiddenAttr NEXT_ELEMENT = new HiddenAttr("next_element");       // PythonAbstractObject
    public static final HiddenAttr INTERNED = new HiddenAttr("_interned");              // PString
    public static final HiddenAttr AST_STATE = new HiddenAttr("ast_state");             // _ast
    public static final HiddenAttr HOST_INTEROP_BEHAVIOR = new HiddenAttr(J___GRAALPYTHON_INTEROP_BEHAVIOR__);      // polyglot
    public static final HiddenAttr DATA = new HiddenAttr("__data__");                   // readline
    public static final HiddenAttr SIGNAL_MODULE_DATA = new HiddenAttr("signalModuleData"); // _signal
    public static final HiddenAttr CURRENT_ALARM = new HiddenAttr("current_alarm");     // _signal
    public static final HiddenAttr DEFAULT_TIMEOUT = new HiddenAttr("default_timeout"); // _socket
    public static final HiddenAttr TREGEX_CACHE = new HiddenAttr("tregex_cache");       // _sre
    public static final HiddenAttr THREAD_COUNT = new HiddenAttr("thread_count");       // _thread
    public static final HiddenAttr CURRENT_ZONE_ID = new HiddenAttr("currentZoneID");   // time
    public static final HiddenAttr TIME_SLEPT = new HiddenAttr("timeSlept");            // time
    public static final HiddenAttr FILTERS_VERSION = new HiddenAttr("filters_version"); // _warnings
    public static final HiddenAttr FILTERS = new HiddenAttr("filters");                 // _warnings
    public static final HiddenAttr DEFAULTACTION = new HiddenAttr("_defaultaction");    // _warnings
    public static final HiddenAttr ONCEREGISTRY = new HiddenAttr("_onceregistry");      // _warnings
    public static final HiddenAttr METHOD_DEF_PTR = new HiddenAttr("method_def_ptr");   // PythonCextMethodBuiltins
    public static final HiddenAttr PROMOTED_START = new HiddenAttr("promoted_start");   // PythonCextSlotBuiltins
    public static final HiddenAttr PROMOTED_STEP = new HiddenAttr("promoted_step");     // PythonCextSlotBuiltins
    public static final HiddenAttr PROMOTED_STOP = new HiddenAttr("promoted_stop");     // PythonCextSlotBuiltins
    public static final HiddenAttr METHODS_FLAGS = new HiddenAttr("__methods_flags__"); // GetMethodsFlagsNode
    public static final HiddenAttr NATIVE_STORAGE = new HiddenAttr("native_storage");
    public static final HiddenAttr NATIVE_SLOTS = new HiddenAttr("__native_slots__");
    public static final HiddenAttr INSTANCESHAPE = new HiddenAttr("instanceshape");

    private final HiddenKey key;

    private HiddenAttr(String keyName) {
        key = new HiddenKey(keyName);
    }

    public HiddenKey getKeyTodoRemoveThis() {
        return key;
    }

    public String getName() {
        return key.getName();
    }

    @Override
    public String toString() {
        return getName();
    }

    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @GenerateUncached
    public static abstract class ReadNode extends Node {
        public abstract Object execute(Node inliningTarget, PythonAbstractObject self, HiddenAttr attr, Object defaultValue);

        public final Object executeCached(PythonAbstractObject self, HiddenAttr attr, Object defaultValue) {
            return execute(this, self, attr, defaultValue);
        }

        public static Object executeUncached(PythonAbstractObject self, HiddenAttr attr, Object defaultValue) {
            return ReadNodeGen.getUncached().execute(null, self, attr, defaultValue);
        }

        @Specialization
        static Object doGeneric(PythonAbstractObject self, HiddenAttr attr, Object defaultValue,
                        @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            return dylib.getOrDefault(self, attr.key, defaultValue);
        }

        @NeverDefault
        public static ReadNode create() {
            return ReadNodeGen.create();
        }

        @NeverDefault
        public static ReadNode getUncached() {
            return ReadNodeGen.getUncached();
        }
    }

    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @GenerateUncached
    @ImportStatic(HiddenAttr.class)
    public static abstract class WriteNode extends Node {
        public abstract void execute(Node inliningTarget, PythonAbstractObject self, HiddenAttr attr, Object value);

        public final void executeCached(PythonAbstractObject self, HiddenAttr attr, Object value) {
            execute(this, self, attr, value);
        }

        public static void executeUncached(PythonAbstractObject self, HiddenAttr attr, Object value) {
            WriteNodeGen.getUncached().execute(null, self, attr, value);
        }

        @Specialization(guards = "attr == DICT")
        static void doPythonObjectDict(PythonObject self, HiddenAttr attr, Object value,
                        @Shared @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            dylib.setShapeFlags(self, dylib.getShapeFlags(self) | HAS_MATERIALIZED_DICT);
            dylib.put(self, DICT.key, value);
        }

        @Specialization(guards = "attr == CLASS")
        static void doPythonObjectClass(PythonObject self, HiddenAttr attr, Object value,
                        @Shared @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            // n.b.: the CLASS property is usually a constant property that is stored in the shape
            // in
            // single-context-mode. If we change it for the first time, there's an implicit shape
            // transition
            dylib.setShapeFlags(self, dylib.getShapeFlags(self) | CLASS_CHANGED_FLAG);
            dylib.put(self, CLASS.key, value);
        }

        @Specialization(guards = "!isSpecialCaseAttr(attr) || !isPythonObject(self)")
        static void doGeneric(PythonAbstractObject self, HiddenAttr attr, Object value,
                        @Shared @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            dylib.put(self, attr.key, value);
        }

        protected static boolean isPythonObject(Object object) {
            return object instanceof PythonObject;
        }

        protected static boolean isSpecialCaseAttr(HiddenAttr attr) {
            return attr == DICT || attr == CLASS;
        }

        @NeverDefault
        public static WriteNode create() {
            return WriteNodeGen.create();
        }

        @NeverDefault
        public static WriteNode getUncached() {
            return WriteNodeGen.getUncached();
        }
    }
}
