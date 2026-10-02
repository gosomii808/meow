package com.myfamily.meow.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myfamily.meow.classification.Category
import com.myfamily.meow.ui.common.BrandLockup
import com.myfamily.meow.ui.common.CategoryPill
import com.myfamily.meow.ui.common.DesignButton
import com.myfamily.meow.ui.common.designCard
import com.myfamily.meow.ui.common.formatWon
import com.myfamily.meow.ui.theme.Background
import com.myfamily.meow.ui.theme.Outline
import com.myfamily.meow.ui.theme.Pink
import com.myfamily.meow.ui.theme.PinkLight
import com.myfamily.meow.ui.theme.PinkSoft
import com.myfamily.meow.ui.theme.TextMuted
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.TextSecondary
import com.myfamily.meow.ui.theme.dz
import com.myfamily.meow.ui.theme.sz
import java.text.NumberFormat
import java.util.Locale

private val number = NumberFormat.getNumberInstance(Locale.KOREA)

/** "목표 설정 및 수정하기" — not in the Figma file; designed to match the report's tone. */
@Composable
fun GoalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val goals = remember { GoalSettings(context) }
    val spending by reportViewModel().spending.collectAsStateWithLifecycle()
    var monthly by remember { mutableStateOf(goals.monthly) }
    val perCategory = remember { mutableStateMapOf<Category, Long>().apply { putAll(goals.categoryGoals()) } }
    val days = spending.month.lengthOfMonth()

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 27.dz),
    ) {
        Spacer(Modifier.height(13.dz))
        BrandLockup(onClick = onBack)
        Spacer(Modifier.height(30.dz))
        Text("목표 설정", color = TextPrimary, fontSize = 40.sz, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(30.dz))

        // Monthly goal
        Column(
            Modifier
                .fillMaxWidth()
                .designCard(RoundedCornerShape(20.dz), 3.dz)
                .padding(22.dz),
        ) {
            Text("${spending.month.monthValue}월 소비 목표", color = TextPrimary, fontSize = 21.sz, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dz))
            AmountField(monthly, onChange = { monthly = it }, fontPx = 36)
            Spacer(Modifier.height(12.dz))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dz)) {
                listOf(10_000L to "+1만", 50_000L to "+5만", 100_000L to "+10만").forEach { (add, label) ->
                    QuickChip(label) { monthly += add }
                }
                QuickChip("지우기", bg = PinkSoft) { monthly = 0 }
            }
            Spacer(Modifier.height(14.dz))
            Text(
                if (monthly > 0) "하루 평균 ${formatWon(monthly / days)}까지 쓸 수 있어요 · 지금까지 ${formatWon(spending.total)} 사용"
                else "이번 달 지금까지 ${formatWon(spending.total)} 사용했어요",
                color = TextSecondary,
                fontSize = 15.sz,
            )
        }
        Spacer(Modifier.height(26.dz))

        Text("카테고리별 목표", color = TextPrimary, fontSize = 21.sz, fontWeight = FontWeight.Bold)
        Text("선택 사항이에요. 비워두면 리포트에 사용 금액만 보여드려요.", color = TextMuted, fontSize = 14.sz)
        Spacer(Modifier.height(14.dz))
        Column(
            Modifier
                .fillMaxWidth()
                .designCard(RoundedCornerShape(20.dz), 3.dz)
                .padding(horizontal = 18.dz, vertical = 8.dz),
        ) {
            Category.entries.filter { it != Category.ETC }.forEach { c ->
                Row(Modifier.padding(vertical = 8.dz), verticalAlignment = Alignment.CenterVertically) {
                    CategoryPill(c, fontPx = 16)
                    Spacer(Modifier.width(10.dz))
                    Text(
                        "사용 ${formatWon(spending.byCategory[c] ?: 0)}",
                        color = TextMuted,
                        fontSize = 13.sz,
                        modifier = Modifier.weight(1f),
                    )
                    AmountField(
                        perCategory[c] ?: 0,
                        onChange = { v -> if (v > 0) perCategory[c] = v else perCategory.remove(c) },
                        fontPx = 18,
                        modifier = Modifier.width(150.dz),
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dz))
        DesignButton(
            "저장하기",
            onClick = {
                goals.monthly = monthly
                goals.setCategoryGoals(perCategory.toMap())
                onBack()
            },
            container = Pink,
            content = Color.White,
        )
        Spacer(Modifier.height(40.dz))
    }
}

/** Right-aligned number input with thousands separators and a "원" suffix. */
@Composable
private fun AmountField(value: Long, onChange: (Long) -> Unit, fontPx: Int, modifier: Modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dz))
            .border(1.dz, Outline, RoundedCornerShape(10.dz))
            .background(Color.White)
            .padding(horizontal = 12.dz, vertical = 8.dz),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = if (value > 0) number.format(value) else "",
            onValueChange = { text -> onChange(text.filter(Char::isDigit).take(10).toLongOrNull() ?: 0) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(fontSize = fontPx.sz, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.End),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value == 0L) Text("0", color = TextMuted, fontSize = fontPx.sz, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                inner()
            },
        )
        Text("원", color = TextSecondary, fontSize = fontPx.sz, modifier = Modifier.padding(start = 4.dz))
    }
}

@Composable
private fun QuickChip(label: String, bg: Color = PinkLight, onClick: () -> Unit) {
    Text(
        label,
        color = TextPrimary,
        fontSize = 15.sz,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dz))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dz, vertical = 7.dz),
    )
}
