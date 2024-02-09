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

public enum HiddenAttr {

    OBJECT_ID("_id"),                   // ObjectNodes
    CLASS("ob_type"),
    DICT("ob_dict"),
    ENCODER_OBJECT("encoder_object"),   // cjkcodecs
    DECODER_OBJECT("decoder_object"),   // cjkcodecs
    KWD_MARK("kwd_mark"),               // functools
    ORIGINAL_CONSTRUCTORS(HashlibModuleBuiltins.J_CONSTRUCTORS),    // hashlib
    PICKLE_STATE("state"),              // pickle
    NEXT_ELEMENT("next_element"),       // PythonAbstractObject
    INTERNED("_interned"),              // PString
    AST_STATE("ast_state"),             // _ast
    HOST_INTEROP_BEHAVIOR(J___GRAALPYTHON_INTEROP_BEHAVIOR__),      // polyglot
    DATA("__data__"),                   // readline
    SIGNAL_MODULE_DATA("signalModuleData"), // _signal
    CURRENT_ALARM("current_alarm"),     // _signal
    DEFAULT_TIMEOUT("default_timeout"), // _socket

    ;

    private final HiddenKey key;

    HiddenAttr(String keyName) {
        key = new HiddenKey(keyName);
    }

    public HiddenKey getKeyTodoRemoveThis() {
        return key;
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
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic(HiddenAttr.class)
    public static abstract class WriteNode extends Node {
        public abstract void execute(Node inliningTarget, PythonAbstractObject self, HiddenAttr attr, Object value);

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

    }
}
