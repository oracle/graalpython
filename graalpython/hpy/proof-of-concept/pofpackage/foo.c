#include "hpy.h"

HPyDef_METH(hello, "hello", HPyFunc_NOARGS)
static HPy hello_impl(HPyContext *ctx, HPy self)
{
    return HPyUnicode_FromString(ctx, "hello from pofpackage.foo");
}


static HPyDef *module_defines[] = {
    &hello,
    NULL
};
static HPyModuleDef moduledef = {
    .doc = "HPy Proof of Concept",
    .size = 0,
    .defines = module_defines
};

HPy_MODINIT(foo, moduledef)
