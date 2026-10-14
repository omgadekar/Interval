package com.om.interval.workout

/** Points at a step: a top-level step ([inner] = -1) or a step inside a repeat block. */
data class StepPath(val block: Int, val inner: Int = -1)

/** Pure edits on a [Workout]; the editor screens call these and save the result straight away. */
object Edit {
    fun innerSteps(block: Block): List<Step>? = when (block) {
        is Step -> null
        is Repeat -> block.steps
        is RepeatUntilDistance -> block.steps
    }

    private fun withInner(block: Block, steps: List<Step>): Block = when (block) {
        is Step -> block
        is Repeat -> block.copy(steps = steps, skipLastOnFinal = block.skipLastOnFinal.coerceAtMost(steps.size - 1))
        is RepeatUntilDistance -> block.copy(steps = steps)
    }

    private fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
        val to = index + delta
        if (index !in indices || to !in indices) return this
        return toMutableList().apply { add(to, removeAt(index)) }
    }

    fun stepAt(w: Workout, path: StepPath): Step? {
        val block = w.blocks.getOrNull(path.block) ?: return null
        return if (path.inner < 0) block as? Step else innerSteps(block)?.getOrNull(path.inner)
    }

    fun add(w: Workout, block: Block): Workout = w.copy(blocks = w.blocks + block)

    fun replace(w: Workout, index: Int, block: Block): Workout =
        w.copy(blocks = w.blocks.mapIndexed { i, b -> if (i == index) block else b })

    fun remove(w: Workout, index: Int): Workout = w.copy(blocks = w.blocks.filterIndexed { i, _ -> i != index })

    fun move(w: Workout, index: Int, delta: Int): Workout = w.copy(blocks = w.blocks.moved(index, delta))

    fun duplicate(w: Workout, index: Int): Workout {
        val block = w.blocks.getOrNull(index) ?: return w
        return w.copy(blocks = w.blocks.toMutableList().apply { add(index + 1, block) })
    }

    fun replaceStep(w: Workout, path: StepPath, step: Step): Workout {
        if (path.inner < 0) return replace(w, path.block, step)
        val block = w.blocks.getOrNull(path.block) ?: return w
        val steps = innerSteps(block) ?: return w
        return replace(w, path.block, withInner(block, steps.mapIndexed { i, s -> if (i == path.inner) step else s }))
    }

    fun addInner(w: Workout, blockIndex: Int, step: Step): Workout {
        val block = w.blocks.getOrNull(blockIndex) ?: return w
        val steps = innerSteps(block) ?: return w
        return replace(w, blockIndex, withInner(block, steps + step))
    }

    /** Removes a step; a repeat keeps at least one step (delete the whole block instead). */
    fun removeStep(w: Workout, path: StepPath): Workout {
        if (path.inner < 0) return remove(w, path.block)
        val block = w.blocks.getOrNull(path.block) ?: return w
        val steps = innerSteps(block) ?: return w
        if (steps.size <= 1) return w
        return replace(w, path.block, withInner(block, steps.filterIndexed { i, _ -> i != path.inner }))
    }

    fun moveStep(w: Workout, path: StepPath, delta: Int): Workout {
        if (path.inner < 0) return move(w, path.block, delta)
        val block = w.blocks.getOrNull(path.block) ?: return w
        val steps = innerSteps(block) ?: return w
        return replace(w, path.block, withInner(block, steps.moved(path.inner, delta)))
    }

    fun duplicateStep(w: Workout, path: StepPath): Workout {
        if (path.inner < 0) return duplicate(w, path.block)
        val block = w.blocks.getOrNull(path.block) ?: return w
        val steps = innerSteps(block) ?: return w
        val step = steps.getOrNull(path.inner) ?: return w
        return replace(w, path.block, withInner(block, steps.toMutableList().apply { add(path.inner + 1, step) }))
    }

    // Sensible defaults for new pieces.
    fun newStep(type: StepType = StepType.RUN) = Step(type, End.Time(5 * 60))
    fun newRepeat() = Repeat(4, listOf(Step(StepType.RUN, End.Time(60)), Step(StepType.WALK, End.Time(60))), skipLastOnFinal = 1)
    fun newRepeatUntil() = RepeatUntilDistance(3000.0, listOf(Step(StepType.RUN, End.Time(10 * 60)), Step(StepType.WALK, End.Time(60))))
}