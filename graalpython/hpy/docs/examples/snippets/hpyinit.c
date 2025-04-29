#include "hpy.h"

// BEGIN
HPyDef_SLOT(my_exec, HPy_mod_exec)
int my_exec_impl(HPyContext *ctx, HPy mod) {

    // Some initialization: add types, constants, ...

    return 0; // success
}

static HPyDef *Methods[] = {
    &my_exec, // HPyDef_SLOT macro generated `my_exec` for us
    NULL,
};

static HPyModuleDef mod_def = {
    .defines = Methods
};

HPy_MODINIT(hpyinit, mod_def)
// END