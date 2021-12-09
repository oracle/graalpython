# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

# Simple smoke test for R<->Python interop demo

FILE <- (function() {
   args <- commandArgs()
   filearg <- grep("^--file=", args, value=TRUE)
   if (length(filearg))
     sub("^--file=", "", filearg)
   else
     invisible(NULL)
})()
DIR <- dirname(dirname(FILE))

library(grid)

# Load Python package and retrieve Python class 'Image'
pcode <- paste0('import sys\n',
           'sys.path.insert(0, "', DIR, '")\n',
           'from image_magix import Image\n',
           'Image\n')
res <- eval.polyglot("python", pcode)

# Load JPEG image
# install.packages("https://www.rforge.net/src/contrib/jpeg_0.1-8.tar.gz", repos=NULL)
# library(jpeg)
# jimg <- readJPEG(paste0(FILE, "input.jpg"))
# jimg <- jimg*255
jimg <- matrix(sample(0:255, 100*100, replace=T), 100, 100)

# Create object of Python class 'Image' with loaded JPEG data
pImgObj <- new(res, dim(jimg)[[2]], dim(jimg)[[1]], jimg)

# Run Sobel filter (in Python)
system.time(processedImgObj <- pImgObj$`@sobel`(T, T))

# Run fisheye filter (in Python)
#processedImgObj <- pImgObj$`@fisheye`(2, T)

mx <- matrix(processedImgObj$`@data`/255, nrow=processedImgObj$`@height`, ncol=processedImgObj$`@width`)
{ grid.newpage(); grid.raster(mx, height=unit(nrow(mx),"points")) }
