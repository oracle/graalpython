/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.module;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__LOADER__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__PACKAGE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__SPEC__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.HiddenAttributes;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;

@ImportStatic(HiddenAttributes.class)
public final class PythonModule extends PythonObject {
    public PythonModule(Object clazz, Shape instanceShape) {
        super(clazz, instanceShape);
    }

    private PythonModule(PythonLanguage lang, String moduleName) {
        super(PythonBuiltinClassType.PythonModule, PythonBuiltinClassType.PythonModule.getInstanceShape(lang));
        setAttribute(__NAME__, moduleName);
        setAttribute(__DOC__, PNone.NONE);
        setAttribute(__PACKAGE__, PNone.NONE);
        setAttribute(__LOADER__, PNone.NONE);
        setAttribute(__SPEC__, PNone.NONE);
    }

    /**
     * Only to be used during context creation
     */
    public static PythonModule createInternal(String moduleName) {
        PythonModule pythonModule = new PythonModule(PythonLanguage.getCurrent(), moduleName);
        PDict dict = PythonObjectFactory.getUncached().createDictFixedStorage(pythonModule);
        try {
            PythonObjectLibrary.getUncached().setDict(pythonModule, dict);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere("BuiltinModule: could not set __dict__");
        }
        return pythonModule;
    }

    @Override
    public String toString() {
        return "<module '" + this.getAttribute(__NAME__) + "'>";
    }

    @ExportMessage
    static class GetDict {
        protected static boolean dictExists(Object dict) {
            return dict instanceof PDict;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self == cachedModule", "dictExists(dict)"}, assumptions = "singleContextAssumption()", limit = "1")
        static PDict getConstant(PythonModule self,
                        @Cached(value = "self", weak = true) PythonModule cachedModule,
                        @Cached(value = "self.getAttribute(DICT)", weak = true) Object dict) {
            // module.__dict__ is a read-only attribute
            return (PDict) dict;
        }

        @Specialization(replaces = "getConstant")
        static PDict getDict(PythonModule self,
                        @Shared("dylib") @CachedLibrary(limit = "4") DynamicObjectLibrary dylib) {
            return (PDict) dylib.getOrDefault(self, DICT, null);
        }
    }
}
