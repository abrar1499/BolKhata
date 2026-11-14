package com.bolkhata.app

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch

/**
 * Every tap in the app gives a light tick under the finger: the usual ripple of [inner], plus a short haptic when a
 * tap lands (not when a finger only starts a scroll). Set once for the whole app in BolTheme.
 */
class HapticIndication(private val inner: IndicationNodeFactory) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = HapticNode(interactionSource, inner.create(interactionSource))
    override fun equals(other: Any?) = other is HapticIndication && other.inner == inner
    override fun hashCode() = inner.hashCode()
}

private class HapticNode(private val source: InteractionSource, ripple: DelegatableNode) : DelegatingNode(), CompositionLocalConsumerModifierNode {
    init {
        delegate(ripple)
    }

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { if (it is PressInteraction.Release) currentValueOf(LocalView).performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
        }
    }
}
