# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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


import math
import time

"""
 * @file ex_particle_OPENMP_seq.c
 * @author Michael Trotter & Matt Goodrum
 * @brief Particle filter implementation in C/OpenMP
 """

PI = 3.1415926535897932

"""
@var M value for Linear Congruential Generator (LCG) use GCC's value
"""
M = 2147483647
"""
@var A value for LCG
"""
A = 1103515245
"""
@var C value for LCG
"""
C = 12345

"""
* Set values of the 3D array to a newValue if that value is equal to the testValue
* @param testValue The value to be replaced
* @param newValue The value to replace testValue with
* @param array3D The image vector
* @param dimX The x dimension of the frame
* @param dimY The y dimension of the frame
* @param dimZ The number of frames
"""


def setIf(testValue, newValue, array3D, dimX, dimY, dimZ):
    for x in range(dimX):
        for y in range(dimY):
            for z in range(dimZ):
                if (array3D[x*dimZ*dimY + y*dimZ + z] == testValue):
                    array3D[x*dimZ*dimY + y*dimZ + z] = newValue


# emulate c++ int overflow
def cppNum(num):
    return (num + 2**31) % 2**32 - 2**31


def cppMod(num, x):
    return num % x if num > 0 else -1 * ((-1 * num) % x)


"""
* Generates a uniformly distributed random number using the provided seed and GCC's settings for the Linear Congruential Generator (LCG)
* @see http:#en.wikipedia.org/wiki/Linear_congruential_generator
* @note This function is thread-safe
* @param seed The seed array
* @param index The specific index of the seed to be advanced
* @return a uniformly distributed number [0, 1)
"""


def randu(seed, index):
    """uniformly distributed random number"""
    num = cppNum(A * seed[index] + C)
    seed[index] = cppMod(num, M)
    # seed[index] = num % M
    return math.fabs(seed[index] / (float(M)))


def randn(seed, index):
    """Box-Muller algorithm"""
    u = randu(seed, index)
    v = randu(seed, index)
    cosine = math.cos(2 * PI * v)
    rt = -2 * math.log(u)

    return math.sqrt(rt) * cosine

# def randu(seed, index):
#     num = A * seed[index] + C
#     seed[index] = num % M
#     return math.fabs(seed[index] / (float(M)))
#
# """
# * Generates a normally distributed random number using the Box-Muller transformation
# * @note This function is thread-safe
# * @param seed The seed array
# * @param index The specific index of the seed to be advanced
# * @return a double representing random number generated using the Box-Muller algorithm
# * @see http:#en.wikipedia.org/wiki/Normal_distribution, section computing value for normal random distribution
# """
#
#
# def randn(seed, index):
#     """Box-Muller algorithm"""
#     u = randu(seed, index)
#     v = randu(seed, index)
#     cosine = math.cos(2 * PI * v)
#     rt = -2 * math.log(u)
#     return math.sqrt(rt) * cosine
#
# """
# * Sets values of 3D matrix using randomly generated numbers from a normal distribution
# * @param array3D The video to be modified
# * @param dimX The x dimension of the frame
# * @param dimY The y dimension of the frame
# * @param dimZ The number of frames
# * @param seed The seed array
# """


def addNoise(array3D, dimX, dimY, dimZ, randn_noise):
    for x in range(dimX):
        for y in range(dimY):
            for z in range(dimZ):
                array3D[x*dimZ*dimY + y*dimZ + z] = array3D[x*dimZ*dimY + y *
                                                            dimZ + z] + int((5 * randn_noise[x*dimZ*dimY + y*dimZ + z]))


"""
* Dilates the provided video
* @param matrix The video to be dilated
* @param posX The x location of the pixel to be dilated
* @param posY The y location of the pixel to be dilated
* @param poxZ The z location of the pixel to be dilated
* @param dimX The x dimension of the frame
* @param dimY The y dimension of the frame
* @param dimZ The number of frames
* @param error The error radius
"""


def dilate_matrix(newMatrix, posX, posY, posZ, dimX, dimY, dimZ, error):
    startX = posX - error
    while (startX < 0):
        startX += 1
    startY = posY - error
    while (startY < 0):
        startY += 1
    endX = posX + error
    while (endX > dimX):
        endX -= 1
    endY = posY + error
    while (endY > dimY):
        endY -= 1
    for x in range(startX, endX):
        for y in range(startY, endY):
            distance = math.sqrt(float(x - posX) ** 2 +
                                 float(y - posY) ** 2)
            if(distance < error):
                newMatrix[x*dimZ*dimY + y*dimZ + posZ] = 1


"""
* Dilates the target matrix using the radius as a guide
* @param matrix The reference matrix
* @param dimX The x dimension of the video
* @param dimY The y dimension of the video
* @param dimZ The z dimension of the video
* @param error The error radius to be dilated
* @param newMatrix The target matrix
"""


def imdilate_disk(matrix, dimX, dimY, dimZ, error, newMatrix):
    for posX in range(dimX):
        for posY in range(dimY):
            for posZ in range(dimZ):
                if(matrix[posX*dimZ*dimY + posY*dimZ + posZ] == 1):
                    # dilate_matrix(newMatrix, posX, posY, posZ, dimX, dimY, dimZ, error)
                    startX = posX - error
                    while (startX < 0):
                        startX += 1
                    startY = posY - error
                    while (startY < 0):
                        startY += 1
                    endX = posX + error
                    while (endX > dimX):
                        endX -= 1
                    endY = posY + error
                    while (endY > dimY):
                        endY -= 1
                    for x in range(startX, endX):
                        for y in range(startY, endY):
                            distance = math.sqrt(float(x - posX) ** 2 +
                                                 float(y - posY) ** 2)
                            if(distance < error):
                                newMatrix[x*dimZ*dimY + y*dimZ + posZ] = 1


"""
* The synthetic video sequence we will work with here is composed of a
* single moving object, circular in shape (fixed radius)
* The motion here is a linear motion
* the foreground intensity and the backgrounf intensity is known
* the image is corrupted with zero mean Gaussian noise
* @param I The video itself
* @param IszX The x dimension of the video
* @param IszY The y dimension of the video
* @param Nfr The number of frames of the video
* @param seed The seed array used for number generation
"""


def copyMatrix(I, newMatrix, IszX, IszY, Nfr):
    for x in range(IszX):
        for y in range(IszY):
            for z in range(Nfr):
                I[x*Nfr*IszY + y*Nfr + z] = newMatrix[x*Nfr*IszY + y*Nfr + z]


def videoSequence(I, IszX, IszY, Nfr, newMatrix, randn_noise):
    max_size = IszX * IszY * Nfr
    """get object centers"""
    x0 = int(round(IszY / 2.0))
    y0 = int(round(IszX / 2.0))
    I[x0*Nfr*IszY + y0*Nfr + 0] = 1

    """move point"""
    for k in range(1, Nfr):
        xk = int(abs(x0 + (k - 1)))
        yk = int(abs(y0 - 2 * (k - 1)))
        pos = yk * IszY * Nfr + xk * Nfr + k
        if(pos >= max_size):
            pos = 0
        I[yk*Nfr*IszY + xk*Nfr + k] = 1

    """dilate matrix"""
    imdilate_disk(I, IszX, IszY, Nfr, 5, newMatrix)
    # copyMatrix(I, newMatrix, IszX, IszY, Nfr)

    """define background, add noise"""
    # setIf(0, 100, I, IszX, IszY, Nfr)
    # setIf(1, 228, I, IszX, IszY, Nfr)

    """add noise"""
    # addNoise(I, IszX, IszY, Nfr, randn_noise)
    for x in range(IszX):
        for y in range(IszY):
            for z in range(Nfr):
                I[x*Nfr*IszY + y*Nfr + z] = newMatrix[x*Nfr*IszY + y*Nfr + z]
                if (I[x*Nfr*IszY + y*Nfr + z] == 0):
                    I[x*Nfr*IszY + y*Nfr + z] = 100
                if (I[x*Nfr*IszY + y*Nfr + z] == 1):
                    I[x*Nfr*IszY + y*Nfr + z] = 228

                I[x*Nfr*IszY + y*Nfr + z] = \
                    I[x*Nfr*IszY + y*Nfr + z] +\
                    int((5 * randn_noise[x*Nfr*IszY + y*Nfr + z]))


"""
* Finds the first element in the CDF that is greater than or equal to the provided value and returns that index
* @note This function uses sequential search
* @param CDF The CDF
* @param lengthCDF The length of CDF
* @param value The value to be found
* @return The index of value in the CDF if value is never found, returns the last index
"""


def findIndex(CDF, lengthCDF, value):
    index = -1
    for x in range(lengthCDF):
        if CDF[x] >= value:
            index = x
            break

    if index == -1:
        return lengthCDF - 1

    return index


"""
* Finds the first element in the CDF that is greater than or equal to the provided value and returns that index
* @note This function uses binary search before switching to sequential search
* @param CDF The CDF
* @param beginIndex The index to start searching from
* @param endIndex The index to stop searching
* @param value The value to find
* @return The index of value in the CDF if value is never found, returns the last index
* @warning Use at your own risk not fully tested
"""


def findIndexBin(CDF, beginIndex, endIndex, value):
    if(endIndex < beginIndex):
        return -1
    middleIndex = beginIndex + ((endIndex - beginIndex) / 2)
    """check the value"""
    if CDF[middleIndex] >= value:
        """check that it's good"""
        if middleIndex == 0:
            return middleIndex
        elif CDF[middleIndex - 1] < value:
            return middleIndex
        elif CDF[middleIndex - 1] == value:
            while middleIndex > 0 and CDF[middleIndex - 1] == value:
                middleIndex -= 1
            return middleIndex

    if CDF[middleIndex] > value:
        return findIndexBin(CDF, beginIndex, middleIndex + 1, value)

    return findIndexBin(CDF, middleIndex - 1, endIndex, value)


"""
* @note This function is designed to work with a video of several frames. 
        In addition, it references a provided MATLAB function which takes 
        the video, the objxy matrix and the x and y arrays as arguments and 
        returns the likelihoods
* @param I The video to be run
* @param IszX The x dimension of the video
* @param IszY The y dimension of the video
* @param Nfr The number of frames
* @param seed The seed array used for random number generation
* @param Nparticles The number of particles to be used
"""


def particleFilter(I, IszX, IszY, Nfr, Nparticles, arrayX, arrayY, randn_X, randn_Y, weights, CDF, frames_randu, xj, yj):
    max_size = IszX * IszY * Nfr
    # original particle centroid
    xe1 = round(IszY / 2.0)
    ye1 = round(IszX / 2.0)

    # expected object locations, compared to center
    radius = 5
    diameter = radius * 2 - 1
    center = radius - 1

    for x in range(Nparticles):
        weights[x] = 1 / (float(Nparticles))
        arrayX[x] = xe1 * 1.
        arrayY[x] = ye1 * 1.

    for k in range(1, Nfr):
        # apply motion model
        # draws sample from motion model (random walk). The only prior information
        # is that the object moves 2x as fast as in the y direction

        for x in range(Nparticles):
            arrayX[x] += 1 + 5 * randn_X[x]
            arrayY[x] += -2 + 2 * randn_Y[x]

            likelihood = 0.
            countOnes = 0
            www = 0

            for xx in range(diameter):
                for yy in range(diameter):
                    distance = math.sqrt(
                        (float((xx - radius + 1)) ** 2) + (float(yy - radius + 1) ** 2))
                    if (distance < radius):
                        countOnes += 1
                        indXXX = round(arrayX[x]) + (xx - center)
                        indXX = abs(indXXX)
                        indYYY = round(arrayY[x]) + (yy - center)
                        indYY = abs(indYYY)
                        indX = int(indXX)
                        indY = int(indYY)
                        indZ = k
                        if(indX >= IszX or indY >= IszY):
                            indX = 0
                            indY = 0
                            indZ = 0

                        likelihood += ((I[indX*Nfr*IszY + indY*Nfr + indZ] - 100) ** 2 -
                                       (I[indX*Nfr*IszY + indY*Nfr + indZ] - 228) ** 2) / 50.0

            likelihood = likelihood / (float(countOnes))
            # update & normalize weights
            # using equation (63) of Arulampalam Tutorial
            weights[x] = weights[x] * math.exp(likelihood)

        sumWeights = 0.
        for x in range(Nparticles):
            sumWeights += weights[x]

        for x in range(Nparticles):
            weights[x] = weights[x] / sumWeights

        xe = 0.
        ye = 0.
        # estimate the object location by expected values
        for x in range(Nparticles):
            xe += arrayX[x] * weights[x]
            ye += arrayY[x] * weights[x]

        distance = math.sqrt(float(xe - int(round(IszY / 2.0)))
                             ** 2 + float(ye - int(round(IszX / 2.0))) ** 2)

        # resampling
        CDF[0] = weights[0]
        for x in range(1, Nparticles):
            CDF[x] = weights[x] + CDF[x - 1]
        u1 = (1 / (float(Nparticles))) * frames_randu[k]

        for x in range(Nparticles):
            v = u1 + x / (float(Nparticles))
            i = findIndex(CDF, Nparticles, v)

            xj[x] = arrayX[i]
            yj[x] = arrayY[i]

        for x in range(Nparticles):
            # reassign arrayX and arrayY
            arrayX[x] = xj[x]
            arrayY[x] = yj[x]
            weights[x] = 1 / (float(Nparticles))


class Data:
    def __init__(self):
        self.I = None
        self.newMatrix = None
        self.weights = None
        self.likelihood = None
        self.arrayX = None
        self.arrayY = None
        self.xj = None
        self.yj = None
        self.CDF = None
        self.u = None

        self.randn_X = None
        self.randn_Y = None
        self.randn_noise = None
        self.frames_randu = None


data = Data()

default_size1 = 1024
default_size2 = 96
default_size3 = 16


def measure(Nparticles=default_size1, IszX=default_size2, IszY=default_size2, Nfr=default_size3):
    # call video sequence
    videoSequence(data.I, IszX, IszY, Nfr, data.newMatrix, data.randn_noise)
    # call particle filter
    particleFilter(data.I, IszX, IszY, Nfr, Nparticles, data.arrayX, data.arrayY,
                   data.randn_X, data.randn_Y, data.weights, data.CDF, data.frames_randu, data.xj, data.yj)


def __benchmark__(Nparticles=10):
    measure(Nparticles)


def __setup__(Nparticles=default_size1, IszX=default_size2, IszY=default_size2, Nfr=default_size3):
    data.I = [0 for i in range(Nfr*IszY*IszX)]
    data.newMatrix = [0 for i in range(Nfr*IszY*IszX)]
    # initial weights are all equal (1/Nparticles)
    data.weights = [0. for i in range(Nparticles)]
    # initial likelihood to 0.0
    data.likelihood = [0. for i in range(Nparticles)]
    data.arrayX = [0. for i in range(Nparticles)]
    data.arrayY = [0. for i in range(Nparticles)]
    data.xj = [0. for i in range(Nparticles)]
    data.yj = [0. for i in range(Nparticles)]
    data.CDF = [0. for i in range(Nparticles)]
    data.u = [0. for i in range(Nparticles)]
    # establish seed
    seed = [int(time.time() % M + 1) * i for i in range(Nparticles)]

    data.randn_X = [randn(seed, i) for i in range(Nparticles)]
    data.randn_Y = [randn(seed, i) for i in range(Nparticles)]
    data.randn_noise = [randn(seed, 0) for i in range(Nfr*IszY*IszX)]
    data.frames_randu = [randn(seed, 0) for i in range(Nfr)]


def __cleanup__(Nparticles=default_size1, IszX=default_size2, IszY=default_size2, Nfr=default_size3):
    # clean up written data
    for i in range(Nfr*IszY*IszX):
        data.I[i] = 0
        data.newMatrix[i] = 0
    for i in range(Nparticles):
        data.weights[i] = 0.
        data.likelihood[i] = 0.
        data.arrayX[i] = 0.
        data.arrayY[i] = 0.
        data.xj[i] = 0.
        data.yj[i] = 0.
        data.CDF[i] = 0.
        data.u[i] = 0.
