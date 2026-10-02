package com.myfamily.meow.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.myfamily.meow.ai.GemmaClassifier
import com.myfamily.meow.classification.Category
import com.myfamily.meow.classification.ClassificationInput
import kotlinx.coroutines.launch

/** Temporary screen for checking on-device Gemma classification on a real device. */
@Composable
fun AiTestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val classifier = remember { GemmaClassifier(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { classifier.close() } }
    val scope = rememberCoroutineScope()

    val modelFile = classifier.modelFile
    val modelInfo = if (modelFile.exists()) {
        "있음 (${modelFile.length() / 1_000_000}MB)"
    } else {
        "없음 — ${modelFile.path} 에 넣어주세요"
    }

    var engineStatus by remember { mutableStateOf("로드 전") }
    var loaded by remember { mutableStateOf(false) }
    var merchant by remember { mutableStateOf("성균문구") }
    var amount by remember { mutableStateOf("8500") }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Gemma 온디바이스 테스트", style = MaterialTheme.typography.headlineSmall)
        Text("모델 파일: $modelInfo")
        Text("엔진: $engineStatus")

        Button(
            enabled = !busy && !loaded && modelFile.exists(),
            onClick = {
                scope.launch {
                    busy = true
                    engineStatus = "로드 중… (최대 십수 초)"
                    val started = System.currentTimeMillis()
                    engineStatus = try {
                        classifier.initialize()
                        loaded = true
                        "${classifier.backendName} 로드 완료 (${System.currentTimeMillis() - started}ms)"
                    } catch (e: Exception) {
                        "실패: ${e.describe()}"
                    }
                    busy = false
                }
            },
        ) { Text("모델 로드") }

        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text("가맹점") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter(Char::isDigit) },
            label = { Text("금액") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            enabled = !busy && loaded && merchant.isNotBlank(),
            onClick = {
                scope.launch {
                    busy = true
                    result = "분류 중…"
                    val started = System.currentTimeMillis()
                    result = try {
                        val r = classifier.classify(
                            ClassificationInput(
                                merchant = merchant,
                                amount = amount.toLongOrNull() ?: 0,
                                transactionTime = System.currentTimeMillis(),
                                history = SAMPLE_HISTORY,
                            )
                        )
                        val elapsed = System.currentTimeMillis() - started
                        "결과: ${r.category.label}\n모델 원문: \"${r.rawOutput}\"\n소요: ${elapsed}ms"
                    } catch (e: Exception) {
                        "실패: ${e.describe()}"
                    }
                    busy = false
                }
            },
        ) { Text("분류하기") }

        if (result.isNotEmpty()) Text(result)
    }
}

private val SAMPLE_HISTORY = listOf(
    "교보문고" to Category.EDUCATION,
    "알파문구" to Category.EDUCATION,
)

private fun Exception.describe(): String = buildString {
    append("${this@describe.javaClass.simpleName}: ${message.orEmpty()}")
    cause?.let { append("\n원인: ${it.javaClass.simpleName}: ${it.message.orEmpty()}") }
}
