package com.personal.podpulse.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.podpulse.data.PodRepository

/** iPhone-style battery card shown over other apps when the case opens. */
@Composable
fun CasePopupCard(onDismiss: () -> Unit) {
    val status by PodRepository.status.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
    ) {
        val shape = RoundedCornerShape(36.dp)
        Column(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, bottom = 32.dp)
                .fillMaxWidth()
                .background(Bg1, shape)
                .border(1.dp, GlassBorder, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val s = status
            Text(
                s?.model?.displayName ?: "AirPods",
                color = TextHi,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))
            if (s != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    if (s.model.single) {
                        BatteryRing(s.left, "Battery", s.leftCharging, false, 110.dp, false)
                    } else {
                        BatteryRing(s.left, "Left", s.leftCharging, false, 96.dp, false)
                        BatteryRing(s.right, "Right", s.rightCharging, false, 96.dp, false)
                        BatteryRing(s.case, "Case", s.caseCharging, false, 96.dp, false)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Glass, contentColor = TextHi),
            ) {
                Text("Done", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
