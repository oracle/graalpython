/* MIT License
 *
 * Copyright (c) 2020, Oracle and/or its affiliates.
 * Copyright (c) 2019 pyhandle
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef HPY_UNIVERSAL_HPYFUNC_H
#define HPY_UNIVERSAL_HPYFUNC_H

typedef enum {
    HPyFunc_VARARGS  = 1,  // METH_VARARGS
    HPyFunc_KEYWORDS = 2,  // METH_VARARGS | METH_KEYWORDS
    HPyFunc_NOARGS   = 3,  // METH_NOARGS
    HPyFunc_O        = 4,  // METH_O

    HPyFunc_DESTROYFUNC,

    HPyFunc_UNARYFUNC,
    HPyFunc_BINARYFUNC,
    HPyFunc_TERNARYFUNC,
    HPyFunc_INQUIRY,
    HPyFunc_LENFUNC,
    HPyFunc_SSIZEARGFUNC,
    HPyFunc_SSIZESSIZEARGFUNC,
    HPyFunc_SSIZEOBJARGPROC,
    HPyFunc_SSIZESSIZEOBJARGPROC,
    HPyFunc_OBJOBJARGPROC,
    HPyFunc_FREEFUNC,
    HPyFunc_GETATTRFUNC,
    HPyFunc_GETATTROFUNC,
    HPyFunc_SETATTRFUNC,
    HPyFunc_SETATTROFUNC,
    HPyFunc_REPRFUNC,
    HPyFunc_HASHFUNC,
    HPyFunc_RICHCMPFUNC,
    HPyFunc_GETITERFUNC,
    HPyFunc_ITERNEXTFUNC,
    HPyFunc_DESCRGETFUNC,
    HPyFunc_DESCRSETFUNC,
    HPyFunc_INITPROC,
    HPyFunc_GETTER,
    HPyFunc_SETTER,

} HPyFunc_Signature;

/* The typedefs corresponding to the various HPyFunc_Signature members
   are produced inside autogen_hpyfunc_declare.h. */


/* ~~~ HPyFunc_DECLARE ~~~

   Emit a forward declaration for a function SYM having a signature SIG, where
   SIG is one of HPyFunc_Signature members.

   Strictly speaking, the anonymous enum is not needed, since it just defines
   a constant like Foo_sig which is never used anyway. However, since we try
   to use "SIG" in the enum definition, we get a very nice error message in
   case we use a SIG value which does not exists.  If we didn't use this
   trick, we would get a VERY obscure error message, since gcc would see a
   function call to something like _HPyFunc_DECLARE_HPyFunc_XXX.
*/
#define HPyFunc_DECLARE(SYM, SIG) \
    enum { SYM##_sig = SIG };     \
    _HPyFunc_DECLARE_##SIG(SYM)


/* ~~~ HPyFunc_TRAMPOLINE ~~~

   Emit a CPython-compatible trampoline which calls IMPL, where IMPL has the
   signature SIG. See above for why we need the anonymous enum. The actual
   implementation of trampolines are in hpyfunc_trampolines.h, which is
   different for the CPython and Universal cases
*/
#define HPyFunc_TRAMPOLINE(SYM, IMPL, SIG) \
    enum { SYM##_sig = SIG };              \
    _HPyFunc_TRAMPOLINE_##SIG(SYM, IMPL)


#include "autogen_hpyfunc_declare.h"

#ifdef HPY_UNIVERSAL_ABI
#  include "universal/hpyfunc_trampolines.h"
#  include "universal/autogen_hpyfunc_trampolines.h"
#else
#  include "cpython/hpyfunc_trampolines.h"
#  include "cpython/autogen_hpyfunc_trampolines.h"
#endif // HPY_UNIVERSAL_ABI

#endif /* HPY_UNIVERSAL_HPYFUNC_H */
