package top.yogiczy.mytv.ui.screens.leanback.main.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回看快进/快退偏移量计算的单元测试
 *
 * 核心公式：calculateSeekOffset(currentOffset, deltaMs, playerPosition, isSeekActive, maxOffset)
 *
 * 关键行为：
 * - 第一次按键（isSeekActive=false）：先同步 playerPosition 到 offset，再加 delta
 * - 连续按键（isSeekActive=true）：不同步 playerPosition，直接加 delta（因为 position 未更新）
 * - offset 始终限制在 [0, maxOffset] 范围内
 */
class CatchupSeekOffsetTest {

    private val oneHour = 3600_000L  // 1小时节目
    private val maxOffset = oneHour - 1000L  // 节目结束前1秒

    private fun calc(
        currentOffset: Long,
        deltaMs: Long,
        playerPosition: Long,
        isSeekActive: Boolean,
        maxOffset: Long = this.maxOffset,
    ) = LeanbackMainContentState.calculateSeekOffset(
        currentOffset = currentOffset,
        deltaMs = deltaMs,
        playerPosition = playerPosition,
        isSeekActive = isSeekActive,
        maxOffset = maxOffset,
    )

    // ==================== 场景1：首次快进，同步播放位置 ====================

    @Test
    fun `首次快进 - 从头开始未播放直接快进10秒`() {
        // offset=0, 没播放过(pos=0), 快进10s
        val result = calc(
            currentOffset = 0,
            deltaMs = 10_000,
            playerPosition = 0,
            isSeekActive = false,
        )
        assertEquals(10_000L, result)
    }

    @Test
    fun `首次快进 - 已播放120秒后快进10秒应到130秒`() {
        // 这是修复前的核心 bug：offset=0 + playerPos=120s + delta=10s = 130s
        // 修复前结果是 10s（丢失了已播放的 120 秒）
        val result = calc(
            currentOffset = 0,
            deltaMs = 10_000,
            playerPosition = 120_000,
            isSeekActive = false,
        )
        assertEquals(130_000L, result)
    }

    @Test
    fun `首次快进 - 之前seek过再播放一段时间后快进`() {
        // 之前 seek 到 offset=60s，又播放了 30s (playerPos=30s)，快进 10s
        // 应到 60 + 30 + 10 = 100s
        val result = calc(
            currentOffset = 60_000,
            deltaMs = 10_000,
            playerPosition = 30_000,
            isSeekActive = false,
        )
        assertEquals(100_000L, result)
    }

    // ==================== 场景2：首次快退 ====================

    @Test
    fun `首次快退 - 已播放60秒后快退10秒应到50秒`() {
        val result = calc(
            currentOffset = 0,
            deltaMs = -10_000,
            playerPosition = 60_000,
            isSeekActive = false,
        )
        assertEquals(50_000L, result)
    }

    @Test
    fun `首次快退 - 快退量大于已播放量应归零`() {
        // offset=0, 播放了 5s, 快退 10s → 应该是 0（不能为负）
        val result = calc(
            currentOffset = 0,
            deltaMs = -10_000,
            playerPosition = 5_000,
            isSeekActive = false,
        )
        assertEquals(0L, result)
    }

    // ==================== 场景3：连续快进（模拟防抖期间的多次按键） ====================

    @Test
    fun `连续快进 - 第二次按键不重复同步playerPosition`() {
        // 第一次按键后 offset 变成了 130s (如上面的测试)
        // 第二次快速按键（isSeekActive=true），不应再加 playerPosition
        val result = calc(
            currentOffset = 130_000,
            deltaMs = 20_000,
            playerPosition = 120_000, // 这个值不应该被使用
            isSeekActive = true,
        )
        assertEquals(150_000L, result)
    }

    @Test
    fun `连续快进 - 模拟3次连续按键（10s,20s,30s档位）`() {
        // 模拟用户从 offset=0, playerPos=60s 开始连续按3次右键
        // 第1次：isSeekActive=false, delta=10s → 0 + 60 + 10 = 70s
        val after1 = calc(0, 10_000, 60_000, isSeekActive = false)
        assertEquals(70_000L, after1)

        // 第2次：isSeekActive=true, delta=20s → 70 + 20 = 90s
        val after2 = calc(after1, 20_000, 60_000, isSeekActive = true)
        assertEquals(90_000L, after2)

        // 第3次：isSeekActive=true, delta=30s → 90 + 30 = 120s
        val after3 = calc(after2, 30_000, 60_000, isSeekActive = true)
        assertEquals(120_000L, after3)
    }

    @Test
    fun `连续快退 - 模拟3次连续快退`() {
        // 从 offset=200s 开始连续快退, playerPos=30s
        // 第1次：isSeekActive=false, delta=-10s → 200 + 30 - 10 = 220s
        val after1 = calc(200_000, -10_000, 30_000, isSeekActive = false)
        assertEquals(220_000L, after1)

        // 第2次：isSeekActive=true, delta=-20s → 220 - 20 = 200s
        val after2 = calc(after1, -20_000, 30_000, isSeekActive = true)
        assertEquals(200_000L, after2)

        // 第3次：isSeekActive=true, delta=-30s → 200 - 30 = 170s
        val after3 = calc(after2, -30_000, 30_000, isSeekActive = true)
        assertEquals(170_000L, after3)
    }

    // ==================== 场景4：边界条件 ====================

    @Test
    fun `快进不超过最大偏移量`() {
        val result = calc(
            currentOffset = maxOffset - 5_000,
            deltaMs = 10_000,
            playerPosition = 0,
            isSeekActive = true,
        )
        assertEquals(maxOffset, result)
    }

    @Test
    fun `快退不低于零`() {
        val result = calc(
            currentOffset = 5_000,
            deltaMs = -10_000,
            playerPosition = 0,
            isSeekActive = true,
        )
        assertEquals(0L, result)
    }

    @Test
    fun `连续快进到节目末尾再快进应保持在最大值`() {
        val result = calc(
            currentOffset = maxOffset,
            deltaMs = 30_000,
            playerPosition = 0,
            isSeekActive = true,
        )
        assertEquals(maxOffset, result)
    }

    @Test
    fun `playerPosition为负数时应视为0`() {
        // playerPosition=-1 表示未知，coerceAtLeast(0L) 应处理
        val result = calc(
            currentOffset = 0,
            deltaMs = 10_000,
            playerPosition = -1,
            isSeekActive = false,
        )
        assertEquals(10_000L, result)
    }

    @Test
    fun `短节目 - maxOffset极小时的处理`() {
        // 5秒的短节目，maxOffset=4s
        val shortMax = 4_000L
        val result = calc(
            currentOffset = 0,
            deltaMs = 10_000,
            playerPosition = 0,
            isSeekActive = false,
            maxOffset = shortMax,
        )
        assertEquals(shortMax, result)
    }

    // ==================== 场景5：复杂交互流程 ====================

    @Test
    fun `完整使用流程 - 播放、快进、再播放、再快进`() {
        // 1. 开始回看，offset=0
        // 2. 播放 60s (playerPos=60s)
        // 3. 首次快进 10s → 70s
        val step1 = calc(0, 10_000, 60_000, isSeekActive = false)
        assertEquals(70_000L, step1)

        // 4. prepare 完成，开始播放新位置，播放了 45s (playerPos=45s)
        // 5. 首次快退 10s → 70 + 45 - 10 = 105s
        val step2 = calc(step1, -10_000, 45_000, isSeekActive = false)
        assertEquals(105_000L, step2)

        // 6. prepare 完成，播放了 20s (playerPos=20s)
        // 7. 连续快进3次：10s + 20s + 30s
        val step3a = calc(step2, 10_000, 20_000, isSeekActive = false)
        assertEquals(135_000L, step3a) // 105 + 20 + 10

        val step3b = calc(step3a, 20_000, 20_000, isSeekActive = true)
        assertEquals(155_000L, step3b) // 135 + 20

        val step3c = calc(step3b, 30_000, 20_000, isSeekActive = true)
        assertEquals(185_000L, step3c) // 155 + 30
    }

    @Test
    fun `快进到末尾附近后快退应正常工作`() {
        // 快进到接近末尾
        val nearEnd = calc(maxOffset - 5_000, 10_000, 0, isSeekActive = true)
        assertEquals(maxOffset, nearEnd)

        // 然后快退 10s（新一轮按键，isSeekActive=false，playerPos=2s）
        val backOff = calc(nearEnd, -10_000, 2_000, isSeekActive = false)
        assertEquals(maxOffset + 2_000 - 10_000, backOff)
    }
}
