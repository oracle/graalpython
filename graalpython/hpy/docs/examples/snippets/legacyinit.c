#include <Python.h>

// BEGIN
static struct PyModuleDef mod_def = {
    PyModuleDef_HEAD_INIT,
    .m_name = "legacyinit",
    .m_size = -1
};

PyMODINIT_FUNC
PyInit_legacyinit(void)
{
    PyObject *mod = PyModule_Create(&mod_def);
    if (mod == NULL) return NULL;

    // Some initialization: add types, constants, ...

    return mod;
}
// END