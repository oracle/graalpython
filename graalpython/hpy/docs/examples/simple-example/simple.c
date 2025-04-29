/* Simple C module that defines single simple function "myabs".
 * We need to have a separate standalone package for those snippets, because we
 * want to show the source code in its entirety, including the HPyDef array
 * initialization, the module definition, and the setup.py script, so there is
 * no room left for mixing these code snippets with other code snippets.
 */

// BEGIN: myabs
#include "hpy.h"

HPyDef_METH(myabs, "myabs", HPyFunc_O)
static HPy myabs_impl(HPyContext *ctx, HPy self, HPy arg)
{
    return HPy_Absolute(ctx, arg);
}
// END: myabs

// BEGIN: double
HPyDef_METH_IMPL(double_num, "double", double_impl, HPyFunc_O)
static HPy double_impl(HPyContext *ctx, HPy self, HPy arg)
{
    return HPy_Add(ctx, arg, arg);
}
// END: double

// BEGIN: methodsdef
static HPyDef *SimpleMethods[] = {
        &myabs,
        &double_num,
        NULL,
};

static HPyModuleDef simple = {
        .doc = "HPy Example",
        .size = 0,
        .defines = SimpleMethods,
        .legacy_methods = NULL
};
// END: methodsdef

// BEGIN: moduledef
HPy_MODINIT(simple, simple)
// END: moduledef