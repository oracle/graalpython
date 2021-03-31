/* MIT License
 *
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2019 pyhandle
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/* We define HPy_New as a macro around _HPy_New to suppress a
   warning. Usually, we expected it to be called this way:
       PointObject *p;
       HPy h = HPy_New(ctx, cls, &p);

   If we call _HPy_New directly, we get a warning because we are implicitly
   casting a PointObject** into a void**.  The following macro uses a trick to
   implicitly cast to void** in a safer way: the 3rd argument is passed as:

       (void)sizeof((*data)->_ob_head._reserved0), (void**)data

   The left operand of the comma operator is ignored, but it ensures that
   p is really a pointer to a pointer to a structure with a HPyObject_HEAD.
   The sizeof() ensures that the expression is not actually evaluated at all.
   The cast to (void) is needed to convince GCC not to emit a warning about
   the unused result.

   The goal of the comment inside the macro is to be displayed in case of
   error. If you pass a non compatible type (e.g. a PointObject* instead of a
   PointObject**), GCC will complain with an error like this:

     ...macros.h:28:19: error: invalid type argument of unary ‘*’ (...)
     (void)sizeof((*data)->_ob_head._reserved0), / * ERROR: blah blah * /

*/

#define HPy_New(ctx, cls, data) (_HPy_New(                                    \
    (ctx),                                                                    \
    (cls),                                                                    \
    ((void)sizeof((*data)->_ob_head._reserved0), /* ERROR: expected a variable of type T** with T a structure starting with HPyObject_HEAD */ \
     (void**)data)                                                            \
  ))


/* ~~~ HPyTuple_Pack ~~~

   this is just syntactic sugar around HPyTuple_FromArray, to help porting the
   exising code which uses PyTuple_Pack
*/

#define HPyTuple_Pack(ctx, n, ...) (HPyTuple_FromArray(ctx, (HPy[]){ __VA_ARGS__ }, n))

/* Rich comparison opcodes */
typedef enum {
    HPy_LT = 0,
    HPy_LE = 1,
    HPy_EQ = 2,
    HPy_NE = 3,
    HPy_GT = 4,
    HPy_GE = 5,
} HPy_RichCmpOp;

// this needs to be a macro because val1 and val2 can be of arbitrary types
#define HPy_RETURN_RICHCOMPARE(ctx, val1, val2, op)                     \
    do {                                                                \
        bool result;                                                    \
        switch (op) {                                                   \
        case HPy_EQ: result = ((val1) == (val2)); break;                \
        case HPy_NE: result = ((val1) != (val2)); break;                \
        case HPy_LT: result = ((val1) <  (val2)); break;                \
        case HPy_GT: result = ((val1) >  (val2)); break;                \
        case HPy_LE: result = ((val1) <= (val2)); break;                \
        case HPy_GE: result = ((val1) >= (val2)); break;                \
        default:                                                        \
            HPy_FatalError(ctx, "Invalid value for HPy_RichCmpOp");     \
        }                                                               \
        if (result)                                                     \
            return HPy_Dup(ctx, ctx->h_True);                           \
        return HPy_Dup(ctx, ctx->h_False);                              \
    } while (0)
