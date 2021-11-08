package dev.eternalbox.common.audio

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log
import kotlin.math.sin
import kotlin.math.sqrt

object PCMPitchShifter {
    private const val MAX_FRAME_LENGTH = 16000
    private val gInFIFO = FloatArray(MAX_FRAME_LENGTH)
    private val gOutFIFO = FloatArray(MAX_FRAME_LENGTH)
    private val gFFTworksp = FloatArray(2 * MAX_FRAME_LENGTH)
    private val gLastPhase = FloatArray(MAX_FRAME_LENGTH / 2 + 1)
    private val gSumPhase = FloatArray(MAX_FRAME_LENGTH / 2 + 1)
    private val gOutputAccum = FloatArray(2 * MAX_FRAME_LENGTH)
    private val gAnaFreq = FloatArray(MAX_FRAME_LENGTH)
    private val gAnaMagn = FloatArray(MAX_FRAME_LENGTH)
    private val gSynFreq = FloatArray(MAX_FRAME_LENGTH)
    private val gSynMagn = FloatArray(MAX_FRAME_LENGTH)
    private var gRover: Long = 0
    private var gInit: Long = 0

    public fun pitchShift(pitchShift: Float, numSamplesToProcess: Long, sampleRate: Float, inData: FloatArray) =
        pitchShift(pitchShift, numSamplesToProcess, 2048, 10, sampleRate, inData)

    public fun pitchShift(pitchShift: Float, numSamplesToProcess: Long, fftFrameSize: Long, osamp: Long, sampleRate: Float, inData: FloatArray) {
        var magn = 0.0f
        var phase = 0f
        var tmp = 0f
        var window = 0f
        var real = 0f
        var imag = 0f

        var freqPerBin = 0f
        var expct = 0f

        var qpd = 0L
        var index = 0
        var inFifoLatency = 0L
        var stepSize = 0L
        var fftFrameSize2 = 0L

        var outData: FloatArray = inData

        /* set up some handy variables */
        fftFrameSize2 = fftFrameSize / 2
        stepSize = fftFrameSize / osamp
        freqPerBin = sampleRate / fftFrameSize.toFloat()
        expct = (2.0 * PI * stepSize.toFloat() / fftFrameSize.toFloat()).toFloat()
        inFifoLatency = fftFrameSize - stepSize
        if (gRover == 0L) gRover = inFifoLatency

        /* main processing loop */
        for (i in 0 until numSamplesToProcess) {
            /* As long as we have not yet collected enough data just read in */
            gInFIFO[gRover.toInt()] = inData[i.toInt()]
            outData[i.toInt()] = gOutFIFO[(gRover - inFifoLatency).toInt()]
            gRover++

            /* now we have enough data for processing */
            if (gRover >= fftFrameSize) {
                gRover = inFifoLatency

                /* do windowing and re,im interleave */
                for (k in 0 until fftFrameSize.toInt()) {
                    window = -.5f * cos((2.0 * PI * k.toFloat() / fftFrameSize.toFloat()).toFloat()) + .5f
                    gFFTworksp[2 * k] = gInFIFO[k] * window
                    gFFTworksp[2 * k + 1] = 0f
                }

                /* ***************** ANALYSIS ******************* */
                /* do transform */
                shortTimeFourierTransform(gFFTworksp, fftFrameSize, -1)

                /* this is the analysis step */
                for (k in 0..fftFrameSize2.toInt()) {
                    /* de-interlace FFT buffer */
                    real = gFFTworksp[2 * k]
                    imag = gFFTworksp[2 * k + 1]

                    /* compute magnitude and phase */
                    magn = 2f * sqrt(real * real + imag * imag)
                    phase = atan2(imag, real)

                    /* compute phase difference */
                    tmp = phase - gLastPhase[k]
                    gLastPhase[k] = phase

                    /* subtract expected phase difference */
                    tmp -= (k * expct).toFloat()

                    /* map delta phase into +/- Pi interval */
                    qpd = (tmp / PI).toLong()
                    if (qpd >= 0) qpd += qpd and 1
                    else qpd -= qpd and 1
                    tmp -= (PI * qpd).toFloat()

                    /* get deviation from bin frequency from the +/- Pi interval */
                    tmp = (osamp * tmp / (2.0 * PI)).toFloat()

                    /* compute the k-th partials' true frequency */
                    tmp = k * freqPerBin + tmp * freqPerBin

                    /* store magnitude and true frequency in analysis arrays */
                    gAnaMagn[k] = magn
                    gAnaFreq[k] = tmp
                }

                /* ***************** PROCESSING ******************* */
                /* this does the actual pitch shifting */
                for (zero in 0 until fftFrameSize.toInt()) {
                    gSynMagn[zero] = 0f
                    gSynFreq[zero] = 0f
                }

                for (k in 0..fftFrameSize2.toInt()) {
                    index = (k * pitchShift).toInt()
                    if (index <= fftFrameSize2) {
                        gSynMagn[index] += gAnaMagn[k]
                        gSynFreq[index] = gAnaFreq[k] * pitchShift
                    }
                }

                /* ***************** SYNTHESIS ******************* */
                /* this is the synthesis step */
                for (k in 0..fftFrameSize2.toInt()) {
                    /* get magnitude and true frequency from synthesis arrays */
                    magn = gSynMagn[k]
                    tmp = gSynFreq[k]

                    /* subtract bin mid frequency */
                    tmp -= k * freqPerBin

                    /* get bin deviation from freq deviation */
                    tmp /= freqPerBin

                    /* take osamp into account */
                    tmp = (2f * PI * tmp / osamp).toFloat()

                    /* add the overlap phase advance back in */
                    tmp += k * expct

                    /* accumulate delta phase to get bin phase */
                    gSumPhase[k] += tmp
                    phase = gSumPhase[k]

                    /* get real and imag part and re-interleave */
                    gFFTworksp[2 * k] = magn * cos(phase)
                    gFFTworksp[2 * k + 1] = magn * sin(phase)
                }

                /* zero negative frequencies */
                for (k in (fftFrameSize + 2).toInt() until (2 * fftFrameSize).toInt()) gFFTworksp[k] = 0f

                /* do inverse transform */
                shortTimeFourierTransform(gFFTworksp, fftFrameSize, 1)

                /* do windowing and add to output accumulator */
                for (k in 0 until fftFrameSize.toInt()) {
                    window = (-.5f * cos(2f * PI * k.toFloat() / fftFrameSize.toFloat()) + .5f).toFloat()
                    gOutputAccum[k] += 2f * window * gFFTworksp[2 * k] / (fftFrameSize * osamp)
                }
                for (k in 0 until stepSize.toInt()) gOutFIFO[k] = gOutputAccum[k]

                /* shift accumulator */
                //memmove(gOutputAccum, gOutputAccum + stepSize, fftFrameSize * sizeof(float));
                for (k in 0 until fftFrameSize.toInt()) gOutputAccum[k] = gOutputAccum[(k + stepSize).toInt()]

                /* move input FIFO */
                for (k in 0 until inFifoLatency.toInt()) gInFIFO[k] = gInFIFO[(k + stepSize).toInt()]
            }
        }
    }

    public fun shortTimeFourierTransform(fftBuffer: FloatArray, fftFrameSize: Long, sign: Long) {
        var wr = 0f
        var wi = 0f
        var arg = 0f
        var temp = 0f

        var tr = 0f
        var ti = 0f
        var ur = 0f
        var ui = 0f

        var bitm = 0
        var le = 0
        var le2 = 0
        var j = 0

        for (i in 2 until (2 * fftFrameSize - 2).toInt() step 2) {
            bitm = 2
            j = 0

            while (bitm < 2 * fftFrameSize) {
                try {
                    if (i and bitm != 0) j++
                    j = j shl 1
                } finally {
                    bitm = bitm shl 1
                }
            }

            if (i < j) {
                temp = fftBuffer[i]
                fftBuffer[i] = fftBuffer[j]
                fftBuffer[j] = temp
                temp = fftBuffer[i + 1]
                fftBuffer[i + 1] = fftBuffer[j + 1]
                fftBuffer[j + 1] = temp
            }
        }

        val max = (log(fftFrameSize.toDouble(), E) / log(2.0, E) + .5)
        le = 2
        for (k in 0 until max.toInt()) {
            le = le shl 1
            le2 = le shr 1
            ur = 1f
            ui = 0f
            arg = (PI / (le2 shr 1)).toFloat()
            wr = cos(arg)
            wi = sign * sin(arg)
            j = 0
            while (j in 0 until le2) {
                try {
                    for (i in j until (2 * fftFrameSize).toInt() step le) {
                        tr = fftBuffer[i + le2] * ur - fftBuffer[i + le2 + 1] * ui
                        ti = fftBuffer[i + le2] * ui + fftBuffer[i + le2 + 1] * ur
                        fftBuffer[i + le2] = fftBuffer[i] - tr
                        fftBuffer[i + le2 + 1] = fftBuffer[i + 1] - ti
                        fftBuffer[i] += tr
                        fftBuffer[i + 1] += ti
                    }

                    tr = ur * wr - ui * wi
                    ui = ur * wi + ui * wr
                    ur = tr
                } finally {
                    j += 2
                }
            }
        }
    }
}