package com.myfamily.meow.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myfamily.meow.ai.ChatTurn
import com.myfamily.meow.gemmaEngine
import com.myfamily.meow.repository
import com.myfamily.meow.ui.common.BrandLockup
import com.myfamily.meow.ui.common.CatFace
import com.myfamily.meow.ui.common.designCard
import com.myfamily.meow.ui.report.GoalSettings
import com.myfamily.meow.ui.theme.Background
import com.myfamily.meow.ui.theme.Outline
import com.myfamily.meow.ui.theme.Pink
import com.myfamily.meow.ui.theme.PinkLight
import com.myfamily.meow.ui.theme.PinkSoft
import com.myfamily.meow.ui.theme.TextMuted
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.dz
import com.myfamily.meow.ui.theme.sz

/** Questions from the team's Figma notes ("고양이와 채팅") plus a few everyday ones. */
private val SUGGESTIONS = listOf(
    "이번 달 내 소비 어때?",
    "10만원 줄이려면 어디서 제일 많이 줄여야 해?",
    "쇼핑을 절반으로 줄였다면?",
    "나 무슨 요일에 제일 많이 써?",
    "이번 주 절약 챌린지 추천해줘",
)

/** "고양이 상담소": spending-habit chat with the on-device cat. */
@Composable
fun ChatScreen(onHome: () -> Unit) {
    val context = LocalContext.current
    val vm: ChatViewModel = viewModel(factory = viewModelFactory {
        initializer {
            val goals = GoalSettings(context.applicationContext)
            ChatViewModel(context.repository, context.gemmaEngine) { goals.monthly to goals.categoryGoals() }
        }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val ready = state.status == CatStatus.READY

    LaunchedEffect(state.turns.size, state.turns.lastOrNull()?.text?.length) {
        if (state.turns.isNotEmpty()) listState.animateScrollToItem(state.turns.lastIndex)
    }

    fun submit(text: String) {
        if (!ready || text.isBlank()) return
        vm.send(text)
        input = ""
    }

    Column(Modifier.fillMaxSize().background(Background).imePadding()) {
        BrandLockup(Modifier.padding(start = 27.dz, top = 13.dz), onClick = onHome)
        Text(
            "고양이 상담소",
            color = TextPrimary,
            fontSize = 34.sz,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dz),
        )
        Text(
            when (state.status) {
                CatStatus.NO_MODEL -> "고양이 두뇌(모델 파일)가 폰에 없어요"
                CatStatus.WAKING -> "고양이 깨우는 중… (처음엔 몇 초 걸려요)"
                CatStatus.READY -> "내 소비 기록을 보고 폰 안에서만 답해요"
                CatStatus.THINKING -> "생각 중이다냥…"
                CatStatus.ERROR -> "고양이가 아파요: ${state.error ?: "알 수 없는 오류"}"
            },
            color = TextMuted,
            fontSize = 15.sz,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dz, bottom = 8.dz),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dz),
            contentPadding = PaddingValues(horizontal = 20.dz, vertical = 12.dz),
        ) {
            itemsIndexed(state.turns) { i, turn ->
                val typing = state.status == CatStatus.THINKING && i == state.turns.lastIndex
                if (turn.fromCat) CatBubble(turn, typing) else UserBubble(turn)
            }
        }

        if (ready) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dz, vertical = 6.dz),
                horizontalArrangement = Arrangement.spacedBy(8.dz),
            ) {
                SUGGESTIONS.forEach { q ->
                    Text(
                        q,
                        color = TextPrimary,
                        fontSize = 15.sz,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dz))
                            .background(PinkSoft)
                            .clickable { submit(q) }
                            .padding(horizontal = 14.dz, vertical = 8.dz),
                    )
                }
            }
        }

        Row(
            Modifier.padding(start = 20.dz, end = 20.dz, top = 6.dz, bottom = 14.dz),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                enabled = ready,
                textStyle = TextStyle(fontSize = 17.sz, color = TextPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit(input) }),
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dz))
                    .border(1.dz, Outline, RoundedCornerShape(22.dz))
                    .background(Color.White)
                    .padding(horizontal = 18.dz, vertical = 12.dz),
                decorationBox = { inner ->
                    if (input.isEmpty()) Text("고양이에게 물어보기…", color = TextMuted, fontSize = 17.sz)
                    inner()
                },
            )
            Spacer(Modifier.width(10.dz))
            Box(
                Modifier
                    .size(48.dz)
                    .clip(CircleShape)
                    .background(if (ready && input.isNotBlank()) Pink else Pink.copy(alpha = 0.35f))
                    .clickable(enabled = ready && input.isNotBlank()) { submit(input) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "보내기", tint = Color.White, modifier = Modifier.size(22.dz))
            }
        }
    }
}

@Composable
private fun CatBubble(turn: ChatTurn, typing: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        CatFace(46.dz)
        Spacer(Modifier.width(8.dz))
        Text(
            turn.text.ifEmpty { if (typing) "…" else "" },
            color = TextPrimary,
            fontSize = 17.sz,
            lineHeight = 25.sz,
            modifier = Modifier
                .widthIn(max = 300.dz)
                .designCard(RoundedCornerShape(topStart = 4.dz, topEnd = 18.dz, bottomEnd = 18.dz, bottomStart = 18.dz), 2.dz)
                .padding(horizontal = 16.dz, vertical = 12.dz),
        )
    }
}

@Composable
private fun UserBubble(turn: ChatTurn) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            turn.text,
            color = TextPrimary,
            fontSize = 17.sz,
            lineHeight = 25.sz,
            modifier = Modifier
                .widthIn(max = 290.dz)
                .clip(RoundedCornerShape(topStart = 18.dz, topEnd = 4.dz, bottomEnd = 18.dz, bottomStart = 18.dz))
                .background(PinkLight)
                .padding(horizontal = 16.dz, vertical = 12.dz),
        )
    }
}
