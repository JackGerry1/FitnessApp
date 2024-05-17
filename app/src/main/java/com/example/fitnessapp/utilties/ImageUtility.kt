package com.example.fitnessapp.utilties

import android.util.Log

/*
References:

  Wetherell, J. (2013). android-heart-rate-monitor. [online]
  GitHub. Available at: https://github.com/phishman3579/android-heart-rate-monitor [Accessed 15 May 2024].

*/
object ImageUtility {

    // Function to calculate the average red component value from a YUV420SP image
    fun decodeYUV420SPtoRedAvg(yuv420sp: ByteArray?, width: Int, height: Int): Int {
        // If the input byte array is null, return 0
        if (yuv420sp == null) return 0

        // Calculate the total number of pixels in the image
        val frameSize = width * height

        // Calculate the sum of the red component values for all pixels
        val sum: Int = decodeYUV420SPtoRedSum(yuv420sp, width, height)

        // Calculate and return the average red component value
        return sum / frameSize
    }

    // function find all of the red in the img frame
    // This private function decodes a YUV420 semi-planar image byte array and calculates the sum of the red values.
    private fun decodeYUV420SPtoRedSum(yuv420sp: ByteArray?, width: Int, height: Int): Int {

        // Check if the input byte array is null, if so, return 0.
        if (yuv420sp == null) return 0

        // Calculate the total number of pixels in the image frame.
        val frameSize = width * height

        var sum = 0

        // Initialize the Y-plane pointer.
        var yp = 0

        // Iterate over each row of the image.
        for (j in 0 until height) {
            // Initialize the U and V plane pointer for the current row.
            var uvp = frameSize + (j shr 1) * width
            var u = 0
            var v = 0

            // Iterate over each column of the image.
            for (i in 0 until width) {
                // Get the Y value for the current pixel.
                var y = (0xff and yuv420sp[yp].toInt()) - 16
                if (y < 0) y = 0

                // Get the U and V values for the current pixel.
                if (i and 1 == 0) {
                    v = (0xff and yuv420sp[uvp++].toInt()) - 128
                    u = (0xff and yuv420sp[uvp++].toInt()) - 128
                }

                // Convert YUV to RGB.
                val y1192 = 1192 * y
                var r = y1192 + 1634 * v
                var g = y1192 - 833 * v - 400 * u
                var b = y1192 + 2066 * u

                // Clamp the R, G, B values to be within the 0-262143 range.
                if (r < 0) r = 0 else if (r > 262143) r = 262143
                if (g < 0) g = 0 else if (g > 262143) g = 262143
                if (b < 0) b = 0 else if (b > 262143) b = 262143

                // Convert the R, G, B values to a pixel value.
                val pixel = -0x1000000 or (r shl 6 and 0xff0000) or (g shr 2 and 0xff00) or (b shr 10 and 0xff)

                // Extract the red value from the pixel and add it to the sum.
                val red = pixel shr 16 and 0xff
                sum += red

                // Move to the next Y value.
                yp++
            }
        }

        return sum
    }

}