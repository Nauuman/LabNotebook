package com.personal.podpulse.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.personal.podpulse.MainActivity
import com.personal.podpulse.R
import com.personal.podpulse.data.PodRepository
import com.personal.podpulse.data.PodStatus
import com.personal.podpulse.ui.levelColor
import java.util.Date

private val TextPrimary = Color(0xFFF4F6FB)
private val TextMuted = Color(0xFF8A93A8)
private val Track = Color(0x22FFFFFF)

class PodWidget : GlanceAppWidget() {
    companion object {
        private val COMPACT = DpSize(110.dp, 40.dp)
        private val FULL = DpSize(200.dp, 100.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, FULL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val status = PodRepository.snapshot(context)
        provideContent { WidgetBody(status) }
    }
}

@Composable
private fun WidgetBody(status: PodStatus?) {
    val size = LocalSize.current
    val compact = size.width < 200.dp || size.height < 100.dp
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(ImageProvider(R.drawable.widget_background))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        when {
            status == null -> Text(
                text = "Open your AirPods case nearby",
                style = TextStyle(color = ColorProvider(TextMuted), fontSize = 12.sp),
            )
            compact -> CompactLayout(status)
            else -> FullLayout(status)
        }
    }
}

@Composable
private fun CompactLayout(status: PodStatus) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (status.model.single) {
            CompactCell("Max", status.left, status.leftCharging)
        } else {
            CompactCell("L", status.left, status.leftCharging)
            CompactCell("R", status.right, status.rightCharging)
            CompactCell("C", status.case, status.caseCharging)
        }
    }
}

@Composable
private fun RowScope.CompactCell(label: String, level: Int?, charging: Boolean) {
    Row(
        modifier = GlanceModifier.defaultWeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp))
        Spacer(GlanceModifier.width(3.dp))
        if (charging) Bolt(level, 11)
        Text(
            text = level?.let { "$it%" } ?: "--",
            style = TextStyle(
                color = ColorProvider(levelColor(level)),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun FullLayout(status: PodStatus) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = status.model.displayName,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(TextPrimary),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Text(
                text = DateFormat.getTimeFormat(context).format(Date(status.timestamp)),
                style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp),
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (status.model.single) {
                FullCell("Battery", status.left, status.leftCharging)
            } else {
                FullCell("Left", status.left, status.leftCharging)
                FullCell("Right", status.right, status.rightCharging)
                FullCell("Case", status.case, status.caseCharging)
            }
        }
    }
}

@Composable
private fun RowScope.FullCell(label: String, level: Int?, charging: Boolean) {
    Column(
        modifier = GlanceModifier.defaultWeight().padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (charging) Bolt(level, 14)
            Text(
                text = level?.let { "$it%" } ?: "--",
                style = TextStyle(
                    color = ColorProvider(levelColor(level)),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Text(label, style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp))
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = (level ?: 0) / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(5.dp),
            color = ColorProvider(levelColor(level)),
            backgroundColor = ColorProvider(Track),
        )
    }
}

@Composable
private fun Bolt(level: Int?, sizeDp: Int) {
    Image(
        provider = ImageProvider(R.drawable.ic_bolt),
        contentDescription = "Charging",
        modifier = GlanceModifier.size(sizeDp.dp),
        colorFilter = ColorFilter.tint(ColorProvider(levelColor(level))),
    )
}
