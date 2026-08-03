/* Copyright (c) 2026, Oracle and/or its affiliates.
 * Copyright (C) 1996-2026 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_MONITORING_H
#define Py_MONITORING_H
#ifdef __cplusplus
extern "C" {
#endif

// There is currently no limited API for monitoring

#ifndef Py_LIMITED_API
#  define Py_CPYTHON_MONITORING_H
#  include "cpython/monitoring.h"
#  undef Py_CPYTHON_MONITORING_H
#endif

#ifdef __cplusplus
}
#endif
#endif /* !Py_MONITORING_H */
