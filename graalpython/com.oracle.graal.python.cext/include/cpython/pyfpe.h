/* Copyright (c) 2022, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_PYFPE_H
#define Py_PYFPE_H
/* Header excluded from the stable API */
#ifndef Py_LIMITED_API

/* These macros used to do something when Python was built with --with-fpectl,
 * but support for that was dropped in 3.7. We continue to define them though,
 * to avoid breaking API users.
 */

#define PyFPE_START_PROTECT(err_string, leave_stmt)
#define PyFPE_END_PROTECT(v)

#endif /* !defined(Py_LIMITED_API) */
#endif /* !Py_PYFPE_H */
