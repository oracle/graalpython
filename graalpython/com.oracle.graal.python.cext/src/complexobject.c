/* Copyright (c) 2018, 2019, Oracle and/or its affiliates.
 * Copyright (C) 1996-2017 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "capi.h"

PyTypeObject PyComplex_Type = PY_TRUFFLE_TYPE("complex", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyComplexObject));

static Py_complex c_error = {-1., 0.};

UPCALL_ID(PyComplex_AsCComplex);
Py_complex PyComplex_AsCComplex(PyObject *op) {
	PyObject* parts = UPCALL_CEXT_O(_jls_PyComplex_AsCComplex, native_to_java(op));
	Py_complex result;
	if(parts != NULL) {
		result.real = PyFloat_AsDouble(PyTuple_GetItem(parts, 0));
		result.imag = PyFloat_AsDouble(PyTuple_GetItem(parts, 1));
		return result;
	}
	return c_error;
}

// Below is taken from CPython

/* Complex object implementation */

/* Borrows heavily from floatobject.c */

/* Submitted by Jim Hugunin */

#include "Python.h"
#include "structmember.h"

/* elementary operations on complex numbers */

static Py_complex c_1 = {1., 0.};

Py_complex
_Py_c_sum(Py_complex a, Py_complex b)
{
    Py_complex r;
    r.real = a.real + b.real;
    r.imag = a.imag + b.imag;
    return r;
}

Py_complex
_Py_c_diff(Py_complex a, Py_complex b)
{
    Py_complex r;
    r.real = a.real - b.real;
    r.imag = a.imag - b.imag;
    return r;
}

Py_complex
_Py_c_neg(Py_complex a)
{
    Py_complex r;
    r.real = -a.real;
    r.imag = -a.imag;
    return r;
}

Py_complex
_Py_c_prod(Py_complex a, Py_complex b)
{
    Py_complex r;
    r.real = a.real*b.real - a.imag*b.imag;
    r.imag = a.real*b.imag + a.imag*b.real;
    return r;
}

/* Avoid bad optimization on Windows ARM64 until the compiler is fixed */
#ifdef _M_ARM64
#pragma optimize("", off)
#endif
Py_complex
_Py_c_quot(Py_complex a, Py_complex b)
{
    /******************************************************************
    This was the original algorithm.  It's grossly prone to spurious
    overflow and underflow errors.  It also merrily divides by 0 despite
    checking for that(!).  The code still serves a doc purpose here, as
    the algorithm following is a simple by-cases transformation of this
    one:

    Py_complex r;
    double d = b.real*b.real + b.imag*b.imag;
    if (d == 0.)
        errno = EDOM;
    r.real = (a.real*b.real + a.imag*b.imag)/d;
    r.imag = (a.imag*b.real - a.real*b.imag)/d;
    return r;
    ******************************************************************/

    /* This algorithm is better, and is pretty obvious:  first divide the
     * numerators and denominator by whichever of {b.real, b.imag} has
     * larger magnitude.  The earliest reference I found was to CACM
     * Algorithm 116 (Complex Division, Robert L. Smith, Stanford
     * University).  As usual, though, we're still ignoring all IEEE
     * endcases.
     */
     Py_complex r;      /* the result */
     const double abs_breal = b.real < 0 ? -b.real : b.real;
     const double abs_bimag = b.imag < 0 ? -b.imag : b.imag;

    if (abs_breal >= abs_bimag) {
        /* divide tops and bottom by b.real */
        if (abs_breal == 0.0) {
            errno = EDOM;
            r.real = r.imag = 0.0;
        }
        else {
            const double ratio = b.imag / b.real;
            const double denom = b.real + b.imag * ratio;
            r.real = (a.real + a.imag * ratio) / denom;
            r.imag = (a.imag - a.real * ratio) / denom;
        }
    }
    else if (abs_bimag >= abs_breal) {
        /* divide tops and bottom by b.imag */
        const double ratio = b.real / b.imag;
        const double denom = b.real * ratio + b.imag;
        assert(b.imag != 0.0);
        r.real = (a.real * ratio + a.imag) / denom;
        r.imag = (a.imag * ratio - a.real) / denom;
    }
    else {
        /* At least one of b.real or b.imag is a NaN */
        r.real = r.imag = Py_NAN;
    }
    return r;
}
#ifdef _M_ARM64
#pragma optimize("", on)
#endif

Py_complex
_Py_c_pow(Py_complex a, Py_complex b)
{
    Py_complex r;
    double vabs,len,at,phase;
    if (b.real == 0. && b.imag == 0.) {
        r.real = 1.;
        r.imag = 0.;
    }
    else if (a.real == 0. && a.imag == 0.) {
        if (b.imag != 0. || b.real < 0.)
            errno = EDOM;
        r.real = 0.;
        r.imag = 0.;
    }
    else {
        vabs = hypot(a.real,a.imag);
        len = pow(vabs,b.real);
        at = atan2(a.imag, a.real);
        phase = at*b.real;
        if (b.imag != 0.0) {
            len /= exp(at*b.imag);
            phase += b.imag*log(vabs);
        }
        r.real = len*cos(phase);
        r.imag = len*sin(phase);
    }
    return r;
}

static Py_complex
c_powu(Py_complex x, long n)
{
    Py_complex r, p;
    long mask = 1;
    r = c_1;
    p = x;
    while (mask > 0 && n >= mask) {
        if (n & mask)
            r = _Py_c_prod(r,p);
        mask <<= 1;
        p = _Py_c_prod(p,p);
    }
    return r;
}

static Py_complex
c_powi(Py_complex x, long n)
{
    Py_complex cn;

    if (n > 100 || n < -100) {
        cn.real = (double) n;
        cn.imag = 0.;
        return _Py_c_pow(x,cn);
    }
    else if (n > 0)
        return c_powu(x,n);
    else
        return _Py_c_quot(c_1, c_powu(x,-n));

}

double
_Py_c_abs(Py_complex z)
{
    /* sets errno = ERANGE on overflow;  otherwise errno = 0 */
    double result;

    if (!Py_IS_FINITE(z.real) || !Py_IS_FINITE(z.imag)) {
        /* C99 rules: if either the real or the imaginary part is an
           infinity, return infinity, even if the other part is a
           NaN. */
        if (Py_IS_INFINITY(z.real)) {
            result = fabs(z.real);
            errno = 0;
            return result;
        }
        if (Py_IS_INFINITY(z.imag)) {
            result = fabs(z.imag);
            errno = 0;
            return result;
        }
        /* either the real or imaginary part is a NaN,
           and neither is infinite. Result should be NaN. */
        return Py_NAN;
    }
    result = hypot(z.real, z.imag);
    if (!Py_IS_FINITE(result))
        errno = ERANGE;
    else
        errno = 0;
    return result;
}

UPCALL_ID(PyComplex_RealAsDouble);
double PyComplex_RealAsDouble(PyObject *op) {
	return UPCALL_CEXT_D(_jls_PyComplex_RealAsDouble, native_to_java(op));
}

UPCALL_ID(PyComplex_ImagAsDouble);
double PyComplex_ImagAsDouble(PyObject *op) {
	return UPCALL_CEXT_D(_jls_PyComplex_ImagAsDouble, native_to_java(op));
}

UPCALL_ID(PyComplex_FromDoubles);
PyObject * PyComplex_FromDoubles(double real, double imag) {
	return UPCALL_CEXT_O(_jls_PyComplex_FromDoubles, real, imag);
}

PyObject * PyComplex_FromCComplex(Py_complex cval) {
	return UPCALL_CEXT_O(_jls_PyComplex_FromDoubles, cval.real, cval.imag);
}
