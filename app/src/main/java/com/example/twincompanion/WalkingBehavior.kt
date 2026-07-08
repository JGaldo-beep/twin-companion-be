package com.example.twincompanion

import android.os.Handler
import android.os.Looper
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class WalkingBehavior(
    private val service: OverlayService,
    private val spriteEngine: SpriteEngine,
    private val minX: Int,
    private val maxX: Int,
    private val minY: Int,
    private val maxY: Int
) {
    private val speedPx = 2
    private val walkTickMs = 16L
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random(System.currentTimeMillis())

    private var x = minX
    private var y = maxY
    private var targetX = minX
    private var targetY = maxY
    private var mode = Mode.WALKING
    private var modeEndsAt = 0L
    private var jumpStartedAt = 0L
    private var jumpBaseY = maxY
    private var walkingDirection = Direction.RIGHT
    private var running = false

    private enum class Mode { WALKING, IDLE, SITTING_DOWN, RESTING, STANDING_UP, WAVE, JUMP, REAPPEAR }

    fun start() {
        running = true
        x = minX
        y = maxY
        service.updatePosition(x, y)
        startWave(durationMs = 1600L)
        scheduleWalkTick()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    /** Interrumpe lo que este haciendo y saluda (llega contexto nuevo de Antonio). */
    fun greet(durationMs: Long = 2200L) {
        if (!running) return
        startWave(durationMs)
    }

    private fun scheduleWalkTick() {
        handler.postDelayed({
            if (running) {
                tick()
                scheduleWalkTick()
            }
        }, walkTickMs)
    }

    @Volatile private var isDragging = false

    fun startDrag() { isDragging = true }

    fun endDrag(newX: Int, newY: Int) {
        x = newX
        y = newY
        targetX = newX
        targetY = newY
        isDragging = false
        chooseNextAction()
    }

    private fun tick() {
        if (isDragging) return
        when (mode) {
            Mode.WALKING -> walkTowardTarget()
            Mode.IDLE, Mode.WAVE -> {
                if (now() >= modeEndsAt) chooseNextAction()
            }
            Mode.RESTING -> {
                if (now() >= modeEndsAt) startStandUp()
            }
            Mode.SITTING_DOWN, Mode.STANDING_UP -> Unit
            Mode.JUMP -> updateJump()
            Mode.REAPPEAR -> reappear()
        }
    }

    private fun walkTowardTarget() {
        val dx = targetX - x
        val dy = targetY - y

        if (abs(dx) <= speedPx && abs(dy) <= speedPx) {
            x = targetX
            y = targetY
            service.updatePosition(x, y)
            chooseNextAction()
            return
        }

        if (isCurrentLegDone(dx, dy)) {
            walkingDirection = directionTo(dx, dy)
        }

        when (walkingDirection) {
            Direction.RIGHT -> x += dx.coerceIn(0, speedPx)
            Direction.LEFT -> x += dx.coerceIn(-speedPx, 0)
            Direction.DOWN -> y += dy.coerceIn(0, speedPx)
            Direction.UP -> y += dy.coerceIn(-speedPx, 0)
        }

        spriteEngine.startWalking(walkingDirection)

        service.updatePosition(x, y)
    }

    private fun chooseNextAction() {
        when (random.nextInt(100)) {
            in 0..34 -> chooseNewWalkTarget()
            in 35..49 -> startIdle()
            in 50..74 -> startRest()
            in 75..88 -> startWave()
            in 89..96 -> startJump()
            else -> mode = Mode.REAPPEAR
        }
    }

    private fun chooseNewWalkTarget() {
        mode = Mode.WALKING
        targetX = random.nextInt(minX, maxX + 1)
        targetY = when (random.nextInt(100)) {
            in 0..34 -> minY
            in 35..69 -> maxY
            else -> random.nextInt(minY, maxY + 1)
        }
        walkingDirection = directionTo(targetX - x, targetY - y)
        spriteEngine.startWalking(walkingDirection)
    }

    private fun startIdle(durationMs: Long = random.nextLong(1400L, 4200L)) {
        mode = Mode.IDLE
        modeEndsAt = now() + durationMs
        spriteEngine.startIdle()
    }

    private fun startWave(durationMs: Long = random.nextLong(1200L, 2600L)) {
        mode = Mode.WAVE
        modeEndsAt = now() + durationMs
        spriteEngine.startWaving()
    }

    private fun startRest(durationMs: Long = random.nextLong(5200L, 11000L)) {
        mode = Mode.SITTING_DOWN
        spriteEngine.playSitDown {
            if (!running || mode != Mode.SITTING_DOWN) return@playSitDown
            mode = Mode.RESTING
            modeEndsAt = now() + durationMs
            spriteEngine.startSitting()
        }
    }

    private fun startStandUp() {
        mode = Mode.STANDING_UP
        spriteEngine.playStandUp {
            if (!running || mode != Mode.STANDING_UP) return@playStandUp
            startIdle(durationMs = random.nextLong(900L, 1800L))
        }
    }

    private fun startJump() {
        mode = Mode.JUMP
        jumpStartedAt = now()
        jumpBaseY = y
        spriteEngine.startJumping()
    }

    private fun updateJump() {
        val elapsed = now() - jumpStartedAt
        val duration = 620L
        if (elapsed >= duration) {
            y = jumpBaseY
            service.updatePosition(x, y)
            chooseNextAction()
            return
        }

        val progress = elapsed.toFloat() / duration.toFloat()
        val lift = sin(progress * Math.PI).toFloat() * 34f
        y = (jumpBaseY - lift).roundToInt()
        service.updatePosition(x, y)
    }

    private fun reappear() {
        x = random.nextInt(minX, maxX + 1)
        y = if (random.nextBoolean()) minY else maxY
        targetX = x
        targetY = y
        service.updatePosition(x, y)
        startWave(durationMs = 1700L)
    }

    private fun directionTo(dx: Int, dy: Int): Direction {
        return if (abs(dx) >= abs(dy)) {
            if (dx >= 0) Direction.RIGHT else Direction.LEFT
        } else {
            if (dy >= 0) Direction.DOWN else Direction.UP
        }
    }

    private fun isCurrentLegDone(dx: Int, dy: Int): Boolean {
        return when (walkingDirection) {
            Direction.RIGHT, Direction.LEFT -> abs(dx) <= speedPx && abs(dy) > speedPx
            Direction.DOWN, Direction.UP -> abs(dy) <= speedPx && abs(dx) > speedPx
        }
    }

    private fun now(): Long = System.currentTimeMillis()
}
