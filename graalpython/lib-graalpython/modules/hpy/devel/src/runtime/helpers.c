/* MIT License
 *
 * Copyright (c) 2021, Oracle and/or its affiliates.
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

/**
 * Runtime helper functions.
 *
 * These are not part of the HPy context or ABI, but are available for
 * HPy extensions to incorporate at compile time.
 *
 * Runtime Helpers API
 * -------------------
 *
 */

#include "hpy.h"

/**
 * Create a type and add it as an attribute on the given object. The type is
 * created using `HPyType_FromSpec`. The object is often a module that the type
 * is being added to.
 *
 * :param ctx:
 *     The execution context.
 * :param obj:
 *     A handle to the object the type is being added to (often a module).
 * :param name:
 *     The name of the attribute on the object to assign the type to.
 * :param hpyspec:
 *     The type spec to use to create the type.
 * :param params:
 *     The type spec parameters to use to create the type.
 *
 * :returns: 0 on failure, 1 on success.
 *
 * Examples:
 *
 * Using `HPyHelpers_AddType` without any `HPyType_SpecParam` parameters:
 *
 * .. code-block:: c
 *
 *     if (!HPyHelpers_AddType(ctx, module, "MyType", hpyspec, NULL))
 *         return HPy_NULL;
 *     ...
 *
 * Using `HPyHelpers_AddType` with `HPyType_SpecParam` parameters:
 *
 * .. code-block:: c
 *
 *     HPyType_SpecParam params[] = {
 *         { HPyType_SpecParam_Base, ctx->h_LongType },
 *         { 0 }
 *     };
 *
 *     if (!HPyHelpers_AddType(ctx, module, "MyType", hpyspec, params))
 *         return HPy_NULL;
 *     ...
 */
HPyAPI_HELPER int
HPyHelpers_AddType(HPyContext *ctx, HPy obj, const char *name,
                  HPyType_Spec *hpyspec, HPyType_SpecParam *params)
{
    HPy h_type = HPyType_FromSpec(ctx, hpyspec, params);
    if (HPy_IsNull(h_type)) {
        return 0;
    }
    if (HPy_SetAttr_s(ctx, obj, name, h_type) != 0) {
        HPy_Close(ctx, h_type);
        return 0;
    }
    HPy_Close(ctx, h_type);
    return 1;
}
