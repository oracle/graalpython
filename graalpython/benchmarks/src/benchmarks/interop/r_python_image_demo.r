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
