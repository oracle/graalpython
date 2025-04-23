/* Simple C module that defines simple functions "myabs" and "add_ints".
 *
 * This module represents an incremental change over the "simple" package
 * and shows how to add a method with VARARGS calling convention.
 *
 * We need to have a separate standalone C module for those snippets, because we
 * want to show the source code including the HPyDef array initialization, so
 * there is no room left for adding other entry points for other code snippets.
 */

#include "hpy.h"

// This is here to make the module look like an incremental change to simple-example
HPyDef_METH(myabs, "myabs", HPyFunc_O)
static HPy myabs_impl(HPyContext *ctx, HPy self, HPy arg)
{
    return HPy_Absolute(ctx, arg);
}

// BEGIN: add_ints
HPyDef_METH(add_ints, "add_ints", HPyFunc_VARARGS)
static HPy add_ints_impl(HPyContext *ctx, HPy self, const HPy *args, size_t nargs)
{
    long a, b;
    if (!HPyArg_Parse(ctx, NULL, args, nargs, "ll", &a, &b))
        return HPy_NULL;
    return HPyLong_FromLong(ctx, a+b);
}
// END: add_ints

// BEGIN: methodsdef
static HPyDef *SimpleMethods[] = {
        &myabs,
        &add_ints,
        NULL,
};
// END: methodsdef

static HPyModuleDef def = {
        .doc = "HPy Example of varargs calling convention",
        .size = 0,
        .defines = SimpleMethods
};

HPy_MODINIT(hpyvarargs, def)
