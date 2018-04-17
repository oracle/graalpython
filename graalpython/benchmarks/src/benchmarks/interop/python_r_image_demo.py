import sys
import os

# avoid using 'os.path'
working_dir_parts = __file__.split(os.sep)[:-1]
sys.path.insert(0, os.sep.join(working_dir_parts[:-1]))

from image_magix import Image

MIME_R = "application/x-r"

load_jpeg = eval(compile("""function(file.name) {
    jimg <- read.csv(gzfile(file.name))
    return (jimg)
}""", "", MIME_R))

print("stage 1")

raw_data = load_jpeg(os.sep.join(working_dir_parts.append("img.csv.gz")))

# the dimensions are R attributes; define function to access them
getDim = eval(compile("function(v, pos) dim(v)[[pos]]", "", MIME_R))
getDataRowMajor = eval(compile("function(v) as.vector(t(v))", "", MIME_R))

print("stage 2")

# Create object of Python class 'Image' with loaded JPEG data
image = Image(getDim(raw_data, 2), getDim(raw_data, 1), getDataRowMajor(raw_data))

# Run Sobel filter
print("applying Sobel filter")
result = image.sobel()
print("-- finished")

# Run fisheye filter
print("applying fisheye filter")
result = image.fisheye()
print("-- finished")

print("stage 3")

draw = eval(compile("""function(processedImgObj) {
    require(grDevices)
    require(grid)
    mx <- matrix(processedImgObj$`@data`/255, nrow=processedImgObj$`@height`, ncol=processedImgObj$`@width`)
    cat("rows:", nrow(mx), "\n")
    grid.newpage()
    grid.raster(mx, height=unit(nrow(mx),"points"))
    cat("DONE\n")
}""", "", MIME_R))

draw(result)

eval(compile("dev.off()", "", MIME_R))

