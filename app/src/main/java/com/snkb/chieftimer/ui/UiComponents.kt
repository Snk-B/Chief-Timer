@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.snkb.chieftimer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.snkb.chieftimer.ui.theme.TavernGold

/** Increments whenever the host lifecycle enters ON_RESUME, for one-shot permission checks without 1s polling. */
@Composable
internal fun rememberResumeTick(): Int {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return tick
}

@Composable
internal fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    TavernStoneCard(modifier = modifier) {
        ClashOutlinedText(
            text = title,
            fontSize = 18.sp,
            color = TavernGold,
            strokeWidth = 4.8f,
        )
        content()
    }
}

/** A card that is closed by default: the whole header row toggles it, "+" when closed and "−" when open. */
@Composable
internal fun CollapsibleCard(
    title: String,
    stateKey: String,
    modifier: Modifier = Modifier,
    alwaysOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var openState by rememberSaveable(stateKey) { mutableStateOf(false) }
    val open = alwaysOpen || openState
    TavernStoneCard(modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).then(if (alwaysOpen) Modifier else Modifier.clickable { openState = !openState }),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClashOutlinedText(text = title, fontSize = 18.sp, color = TavernGold, strokeWidth = 4.8f)
            if (!alwaysOpen) ClashOutlinedText(text = if (open) "−" else "+", fontSize = 24.sp, color = TavernGold, strokeWidth = 5f)
        }
        AnimatedVisibility(visible = open) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** A titled block inside a card, so several related blocks can share one card. */
@Composable
internal fun SubSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ClashOutlinedText(text = title, fontSize = 14.5.sp, color = TavernGold, strokeWidth = 4f)
        content()
    }
}
