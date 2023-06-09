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

#ifndef HPY_HPYTYPES_H_
#define HPY_HPYTYPES_H_

#include <graalvm/llvm/polyglot.h>
#include "hpy.h"

//#define DECLARE_TYPE_PUBLIC(typename) POLYGLOT_DECLARE_TYPE(typename) \
//    polyglot_typeid graal_hpy_get_##typename##_typeid() {      \
//        return polyglot_##typename##_typeid();                        \
//    }
//
//typedef HPy (*HPyModule_init)(HPyContext ctx);
//
//DECLARE_TYPE_PUBLIC(HPyModule_init);
//DECLARE_TYPE_PUBLIC(HPyFunc_noargs);
//DECLARE_TYPE_PUBLIC(HPyFunc_o);
//DECLARE_TYPE_PUBLIC(HPyFunc_varargs);
//DECLARE_TYPE_PUBLIC(HPyFunc_keywords);
//DECLARE_TYPE_PUBLIC(HPyFunc_unaryfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_binaryfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_ternaryfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_inquiry);
//DECLARE_TYPE_PUBLIC(HPyFunc_lenfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_ssizeargfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_ssizessizeargfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_ssizeobjargproc);
//DECLARE_TYPE_PUBLIC(HPyFunc_ssizessizeobjargproc);
//DECLARE_TYPE_PUBLIC(HPyFunc_objobjargproc);
//DECLARE_TYPE_PUBLIC(HPyFunc_freefunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_getattrfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_getattrofunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_setattrfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_setattrofunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_reprfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_hashfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_richcmpfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_getiterfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_iternextfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_descrgetfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_descrsetfunc);
//DECLARE_TYPE_PUBLIC(HPyFunc_initproc);
//DECLARE_TYPE_PUBLIC(HPyFunc_getter);
//DECLARE_TYPE_PUBLIC(HPyFunc_setter);
//DECLARE_TYPE_PUBLIC(HPyFunc_objobjproc);
//DECLARE_TYPE_PUBLIC(HPyFunc_getbufferproc);
//DECLARE_TYPE_PUBLIC(HPyFunc_releasebufferproc);
//DECLARE_TYPE_PUBLIC(HPyFunc_destroyfunc);

#endif /* HPY_HPYTYPES_H_ */
