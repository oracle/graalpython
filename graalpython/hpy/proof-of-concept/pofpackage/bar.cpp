#include "hpy.h"

class Bar
{
    int foo;
    public:
        Bar(int f)
        {
            foo = f;
        }

        int boo(HPyContext *ctx, HPy obj)
        {
            return foo + HPyLong_AsLong(ctx, obj);
        }
};

HPyDef_METH(hello, "hello", HPyFunc_O)
static HPy hello_impl(HPyContext *ctx, HPy self, HPy obj)
{
    Bar b(21);
    return HPyLong_FromLong(ctx, b.boo(ctx, obj));
}


static HPyDef *module_defines[] = {
    &hello,
    NULL
};
static HPyModuleDef moduledef = {
    .doc = "HPy C++ Proof of Concept",
    .size = 0,
    .defines = module_defines
};

#ifdef __cplusplus
extern "C" {
#endif


HPy_MODINIT(bar, moduledef)

#ifdef __cplusplus
}
#endif
