package com.example.twincompanion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper

const val FRAME_WIDTH = 64
const val FRAME_HEIGHT = 64
const val WALK_FRAMES_PER_ROW = 9
const val IDLE_FRAMES_PER_ROW = 2
const val SIT_FRAMES_PER_ROW = 3
const val EMOTE_FRAMES_PER_ROW = 3
const val JUMP_FRAMES_PER_ROW = 5
const val FRAME_DURATION_MS = 180L
const val SPRITE_SIZE_DP = 64

enum class Direction {
    DOWN, LEFT, RIGHT, UP
}

class SpriteEngine(context: Context) {

    private val walkSheet = loadSheet(context, "antonio_walk")
    private val idleSheet = loadSheet(context, "antonio_idle")
    private val jumpSheet = loadSheet(context, "antonio_jump")
    private val sitSheet = loadSheet(context, "antonio_sit")
    private val emoteSheet = loadSheet(context, "antonio_emote")

    private var currentFrame = 0
    private var currentDirection = Direction.RIGHT
    private var currentAnimation = Animation.WALK
    private var loopStartFrame = 0
    private var loopFrameCount = WALK_FRAMES_PER_ROW
    private var shouldLoop = true
    private var onSequenceFinished: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    var onFrameChanged: (() -> Unit)? = null

    var currentBitmap: Bitmap = getFrame(Direction.DOWN, 1)
        private set

    fun startWalking(direction: Direction) {
        if (currentAnimation == Animation.WALK && direction == currentDirection && running) return
        startLoop(Animation.WALK, direction, startFrame = 0, frameCount = WALK_FRAMES_PER_ROW)
    }

    fun startIdle(direction: Direction = Direction.DOWN) {
        running = false
        handler.removeCallbacksAndMessages(null)
        onSequenceFinished = null
        currentAnimation = Animation.IDLE
        currentDirection = direction
        currentFrame = 0
        currentBitmap = getFrame(direction, currentFrame)
        onFrameChanged?.invoke()
    }

    fun startSitting() {
        showFrame(Animation.SIT, Direction.DOWN, frame = 1)
    }

    fun playSitDown(onFinished: () -> Unit) {
        startSequence(
            animation = Animation.SIT,
            direction = Direction.DOWN,
            startFrame = 0,
            frameCount = 2,
            loop = false,
            onFinished = onFinished
        )
    }

    fun playStandUp(onFinished: () -> Unit) {
        startSequence(
            animation = Animation.SIT,
            direction = Direction.DOWN,
            startFrame = 2,
            frameCount = 1,
            loop = false,
            onFinished = onFinished
        )
    }

    fun startWaving() {
        startLoop(Animation.EMOTE, Direction.DOWN, startFrame = 0, frameCount = EMOTE_FRAMES_PER_ROW)
    }

    fun startJumping() {
        startLoop(Animation.JUMP, Direction.DOWN, startFrame = 0, frameCount = JUMP_FRAMES_PER_ROW)
    }

    fun stopWalking() {
        startIdle()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        onSequenceFinished = null
    }

    private fun startLoop(animation: Animation, direction: Direction, startFrame: Int, frameCount: Int) {
        startSequence(animation, direction, startFrame, frameCount, loop = true, onFinished = null)
    }

    private fun startSequence(
        animation: Animation,
        direction: Direction,
        startFrame: Int,
        frameCount: Int,
        loop: Boolean,
        onFinished: (() -> Unit)?
    ) {
        currentAnimation = animation
        currentDirection = direction
        loopStartFrame = startFrame
        loopFrameCount = frameCount.coerceAtLeast(1)
        shouldLoop = loop
        onSequenceFinished = onFinished
        currentFrame = startFrame
        currentBitmap = getFrame(currentDirection, currentFrame)
        onFrameChanged?.invoke()

        if (!running) {
            running = true
            scheduleNextFrame()
        }
    }

    private fun scheduleNextFrame() {
        handler.postDelayed({
            if (running) {
                val nextRelativeFrame = currentFrame - loopStartFrame + 1
                if (!shouldLoop && nextRelativeFrame >= loopFrameCount) {
                    running = false
                    currentFrame = loopStartFrame + loopFrameCount - 1
                    currentBitmap = getFrame(currentDirection, currentFrame)
                    onFrameChanged?.invoke()
                    onSequenceFinished?.invoke()
                    onSequenceFinished = null
                    return@postDelayed
                }

                val relativeFrame = nextRelativeFrame % loopFrameCount
                currentFrame = loopStartFrame + relativeFrame
                currentBitmap = getFrame(currentDirection, currentFrame)
                onFrameChanged?.invoke()
                scheduleNextFrame()
            }
        }, FRAME_DURATION_MS)
    }

    private fun showFrame(animation: Animation, direction: Direction, frame: Int) {
        running = false
        handler.removeCallbacksAndMessages(null)
        onSequenceFinished = null
        currentAnimation = animation
        currentDirection = direction
        currentFrame = frame
        currentBitmap = getFrame(direction, frame)
        onFrameChanged?.invoke()
    }

    private fun getFrame(direction: Direction, frame: Int): Bitmap {
        val sheet = sheetFor(currentAnimation) ?: walkSheet ?: return makePlaceholder()
        val maxRow = sheet.height / FRAME_HEIGHT - 1
        val maxFrame = sheet.width / FRAME_WIDTH - 1
        val safeRow = rowFor(sheet, currentAnimation, direction).coerceIn(0, maxRow)
        val safeFrame = frame.coerceIn(0, maxFrame)
        return Bitmap.createBitmap(
            sheet,
            safeFrame * FRAME_WIDTH,
            safeRow * FRAME_HEIGHT,
            FRAME_WIDTH,
            FRAME_HEIGHT
        )
    }

    private fun sheetFor(animation: Animation): Bitmap? {
        return when (animation) {
            Animation.WALK -> walkSheet
            Animation.IDLE -> idleSheet ?: walkSheet
            Animation.JUMP -> jumpSheet ?: walkSheet
            Animation.SIT -> sitSheet ?: idleSheet ?: walkSheet
            Animation.EMOTE -> emoteSheet ?: walkSheet
        }
    }

    private fun rowFor(sheet: Bitmap, animation: Animation, direction: Direction): Int {
        if (sheet.isFullLpcSheet()) {
            return when (animation) {
                Animation.WALK, Animation.IDLE -> when (direction) {
                    Direction.UP -> 8
                    Direction.LEFT -> 9
                    Direction.DOWN -> 10
                    Direction.RIGHT -> 11
                }
                Animation.JUMP -> 20
                Animation.SIT -> 17
                Animation.EMOTE -> 0
            }
        }

        return when (direction) {
            Direction.UP -> 0
            Direction.LEFT -> 1
            Direction.DOWN -> 2
            Direction.RIGHT -> 3
        }
    }

    private fun loadSheet(context: Context, name: String): Bitmap? {
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (resId != 0) {
            BitmapFactory.decodeResource(
                context.resources,
                resId,
                BitmapFactory.Options().apply { inScaled = false }
            )
        } else {
            null
        }
    }

    private fun Bitmap.isFullLpcSheet(): Boolean {
        return width >= FRAME_WIDTH * 13 && height >= FRAME_HEIGHT * 20
    }

    private fun makePlaceholder(): Bitmap {
        val bmp = Bitmap.createBitmap(FRAME_WIDTH, FRAME_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bodyPaint = Paint().apply { color = Color.parseColor("#FF6B35") }
        val eyePaint = Paint().apply { color = Color.BLACK }
        canvas.drawRect(16f, 8f, 48f, 56f, bodyPaint)
        canvas.drawRect(22f, 16f, 30f, 24f, eyePaint)
        canvas.drawRect(34f, 16f, 42f, 24f, eyePaint)
        return bmp
    }

    private enum class Animation { WALK, IDLE, JUMP, SIT, EMOTE }
}
