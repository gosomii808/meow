package com.myfamily.meow.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myfamily.meow.BuildConfig
import com.myfamily.meow.R
import com.myfamily.meow.repository
import com.myfamily.meow.ui.common.BrandLockup
import com.myfamily.meow.ui.common.CatFace
import com.myfamily.meow.ui.theme.HomeBackground
import com.myfamily.meow.ui.theme.MintChip
import com.myfamily.meow.ui.theme.MintIconBg
import com.myfamily.meow.ui.theme.MintSoft
import com.myfamily.meow.ui.theme.MintText
import com.myfamily.meow.ui.theme.Pink
import com.myfamily.meow.ui.theme.PinkCount
import com.myfamily.meow.ui.theme.PinkIconBg
import com.myfamily.meow.ui.theme.PinkMuted
import com.myfamily.meow.ui.theme.PinkSoft
import com.myfamily.meow.ui.theme.ReportGreen
import com.myfamily.meow.ui.theme.SwipeExclude
import com.myfamily.meow.ui.theme.TextMuted
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.TextSecondary
import com.myfamily.meow.ui.theme.dz
import com.myfamily.meow.ui.theme.sz

private val TitleColor = Color(0xFF1E1E28)

/** Figma 메인 화면 (a raster mock in the file; layout measured from it). */
@Composable
fun HomeScreen(
    listenerOn: Boolean,
    onStartSwipe: () -> Unit,
    onHistory: () -> Unit,
    onReport: () -> Unit,
    onChat: () -> Unit,
    onSettings: () -> Unit,
    onDebug: () -> Unit,
    onFixListener: () -> Unit,
) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(context.repository) } })
    val s by vm.summary.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(HomeBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dz),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (BuildConfig.DEBUG) {
                TextButton(onClick = onDebug) { Text("DEV", color = TextMuted, fontSize = 13.sz) }
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "설정", tint = TextMuted) }
        }

        if (!listenerOn) {
            Text(
                "알림 접근이 꺼져 있어 결제를 수집하지 못해요. 눌러서 켜기",
                color = SwipeExclude,
                fontSize = 15.sz,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dz))
                    .background(PinkSoft)
                    .clickable(onClick = onFixListener)
                    .padding(12.dz),
            )
        }

        // Greeting with the cat-in-wallet illustration overlapping the summary card.
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(top = 50.dz)) {
                Text("안녕하세요!", color = PinkMuted, fontSize = 19.sz)
                Spacer(Modifier.height(4.dz))
                Text(
                    "오늘의 소비,\n정리할 시간이에요",
                    color = TitleColor,
                    fontSize = 31.sz,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 42.sz,
                )
                Spacer(Modifier.height(10.dz))
                Text("직접 확인할 내역 ${s.pending}건", color = TextSecondary, fontSize = 17.sz)
                Spacer(Modifier.height(30.dz))
            }
            Image(
                painterResource(R.drawable.home_cat_wallet),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dz, y = 4.dz)
                    .size(width = 147.dz, height = 148.dz),
            )
        }

        SummaryCard(s)
        Spacer(Modifier.height(24.dz))

        Row(
            Modifier
                .fillMaxWidth()
                .height(85.dz)
                .clip(RoundedCornerShape(22.dz))
                .background(Pink)
                .clickable(onClick = onStartSwipe),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("소비 스와이프 시작하기", color = Color.White, fontSize = 23.sz, fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dz))
        }
        Spacer(Modifier.height(16.dz))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dz)) {
            SmallAction("내역 확인", PinkSoft, Modifier.weight(1f), onHistory) {
                IconBadge(PinkIconBg) { Icon(Icons.AutoMirrored.Filled.List, null, tint = Pink, modifier = Modifier.size(20.dz)) }
            }
            SmallAction("분석 리포트", MintSoft, Modifier.weight(1f), onReport) { TargetIcon() }
        }
        Spacer(Modifier.height(16.dz))
        AskCatCard(onChat)

        Spacer(Modifier.height(48.dz))
        BrandLockup(Modifier.align(Alignment.CenterHorizontally), catPx = 48, fontPx = 24)
        Spacer(Modifier.height(32.dz))
    }
}

@Composable
private fun SummaryCard(s: HomeSummary) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dz))
            .background(PinkSoft)
            .padding(horizontal = 24.dz, vertical = 20.dz),
        verticalArrangement = Arrangement.spacedBy(14.dz),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("오늘의 소비 요약", color = TitleColor, fontSize = 19.sz, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "✦ AI 정리",
                color = MintText,
                fontSize = 14.sz,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dz))
                    .background(MintChip)
                    .padding(horizontal = 12.dz, vertical = 6.dz),
            )
        }
        SummaryRow(
            icon = { IconBadge(PinkIconBg) { Icon(Icons.AutoMirrored.Filled.List, null, tint = Pink, modifier = Modifier.size(20.dz)) } },
            label = buildAnnotatedString { append("오늘 들어온 소비") },
            count = "${s.todayIncoming}건",
            countColor = PinkCount,
        )
        SummaryRow(
            icon = { IconBadge(MintIconBg) { Text("🤖", fontSize = 18.sz) } },
            label = buildAnnotatedString {
                append("AI가 ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${s.autoSorted}건") }
                append("을 먼저 정리했어요")
            },
            count = "${s.autoSorted}건",
            countColor = TitleColor,
        )
        SummaryRow(
            icon = {
                IconBadge(PinkIconBg) {
                    Box(Modifier.size(22.dz).clip(CircleShape).background(PinkCount), contentAlignment = Alignment.Center) {
                        Text("!", color = Color.White, fontSize = 15.sz, fontWeight = FontWeight.Bold)
                    }
                }
            },
            label = buildAnnotatedString {
                append("의심 내역 ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${s.suspicious}건") }
                append(" 남음")
            },
            count = "${s.suspicious}건",
            countColor = PinkCount,
        )
    }
}

@Composable
private fun SummaryRow(
    icon: @Composable () -> Unit,
    label: androidx.compose.ui.text.AnnotatedString,
    count: String,
    countColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(14.dz))
        Text(label, color = TitleColor, fontSize = 16.sz, modifier = Modifier.weight(1f))
        Text(count, color = countColor, fontSize = 19.sz, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IconBadge(bg: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(42.dz).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SmallAction(label: String, bg: Color, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(
        modifier
            .height(78.dz)
            .clip(RoundedCornerShape(20.dz))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dz),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.width(10.dz))
        Text(label, color = TitleColor, fontSize = 17.sz, fontWeight = FontWeight.Bold)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TitleColor, modifier = Modifier.size(22.dz))
    }
}

/** Entry to the on-device cat chatbot (not in the Figma file; same tone as the summary card). */
@Composable
private fun AskCatCard(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dz))
            .background(Color.White)
            .border(1.5.dz, PinkSoft, RoundedCornerShape(20.dz))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dz, vertical = 14.dz),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatFace(52.dz)
        Spacer(Modifier.width(12.dz))
        Column(Modifier.weight(1f)) {
            Text("고양이 상담소", color = TitleColor, fontSize = 18.sz, fontWeight = FontWeight.Bold)
            Text("내 소비 습관, 고양이에게 물어봐요", color = TextSecondary, fontSize = 15.sz)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TitleColor, modifier = Modifier.size(24.dz))
    }
}

/** Green target mark from the mock's 분석 리포트 button. */
@Composable
private fun TargetIcon() {
    val stroke = 2.4f.dz
    Canvas(Modifier.size(30.dz)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - stroke.toPx()
        drawCircle(ReportGreen, r, c, style = Stroke(stroke.toPx()))
        drawCircle(ReportGreen, r * 0.58f, c, style = Stroke(stroke.toPx()))
        drawCircle(ReportGreen, r * 0.2f, c)
    }
}
