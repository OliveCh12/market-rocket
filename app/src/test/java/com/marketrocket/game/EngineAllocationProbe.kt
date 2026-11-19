package com.marketrocket.game

import org.junit.Assume.assumeTrue
import org.junit.Test

class EngineAllocationProbe {
    @Volatile private var renderSink: GameState? = null

    @Test
    fun measureAllocationsOnTheJVM() {
        assumeTrue(System.getenv("MARKET_ROCKET_PROFILE") == "1")
        // Android's compile-time API stubs omit the JVM-only management classes.
        val factory = Class.forName("java.lang.management.ManagementFactory")
        val type = Class.forName("com.sun.management.ThreadMXBean")
        val bean = factory.getMethod("getThreadMXBean").invoke(null)
        assumeTrue(type.isInstance(bean))
        assumeTrue(type.getMethod("isThreadAllocatedMemorySupported").invoke(bean) == true)
        type.getMethod("setThreadAllocatedMemoryEnabled", Boolean::class.javaPrimitiveType).invoke(bean, true)
        val counter = type.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        val threadId = Thread.currentThread().id
        fun allocated() = counter.invoke(bean, threadId) as Long
        val frames = 24_000

        fun run(withSnapshots: Boolean): Long {
            val engine = GameEngine(PlayerProfile(), seed = 12)
            engine.resize(380, 720)
            val before = allocated()
            repeat(frames) { frame ->
                if (frame % 960 == 0) {
                    engine.returnToReady()
                    engine.startRound()
                }
                engine.setThrust(frame % 20 < 9)
                engine.advance(1.0 / 120)
                if (withSnapshots) renderSink = engine.snapshot()
            }
            return (allocated() - before) / frames
        }

        fun runBursts(): Long {
            val engine = GameEngine(PlayerProfile(), seed = 12)
            var bytes = 0L
            repeat(120) {
                engine.returnToReady()
                engine.startRound()
                repeat(24) {
                    if (engine.snapshot().phase == Phase.Playing) engine.advance(0.08)
                }
                check(engine.snapshot().phase == Phase.Crashed)
                val before = allocated()
                repeat(30) {
                    engine.advance(1.0 / 120)
                    renderSink = engine.snapshot()
                }
                bytes += allocated() - before
            }
            return bytes / (120 * 30)
        }

        repeat(3) {
            run(false)
            run(true)
            runBursts()
        }
        val simulation = List(5) { run(false) }.sorted()[2]
        val rendering = List(5) { run(true) }.sorted()[2]
        val bursts = List(5) { runBursts() }.sorted()[2]
        println("MARKET_ROCKET_PROFILE crash-effects+snapshot=$bursts B/frame")
        println("MARKET_ROCKET_PROFILE simulation=$simulation B/frame, simulation+snapshot=$rendering B/frame")
    }
}
