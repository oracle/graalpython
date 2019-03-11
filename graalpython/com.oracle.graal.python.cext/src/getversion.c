/* Copyright (c) 2018, 2019, Oracle and/or its affiliates.
 * Copyright (C) 1996-2017 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */

/* Return the full version string. */

#include "Python.h"

#include "patchlevel.h"

const char *
Py_GetVersion(void)
{
    static char version[250];
    PyOS_snprintf(version, sizeof(version), "%.80s (%.80s) %.80s",
                  PY_VERSION, Py_GetBuildInfo(), Py_GetCompiler());
    return version;
}
