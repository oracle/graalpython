/* Copyright (c) 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2024 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#if KeccakOpt == 64
  #include "KeccakP-1600-SnP-opt64.h"
#elif KeccakOpt == 32
  #include "KeccakP-1600-SnP-opt32.h"
#else
  #error "No KeccakOpt"
#endif
