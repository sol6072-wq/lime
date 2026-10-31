@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lime.translator

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LimeBg = Color(0xFFF4FBE8)
val LimeDeep = Color(0xFF4E8A1E)
val LimeMain = Color(0xFF8BC34A)
val LimeSoft = Color(0xFFDDF2BC)
val Sunny = Color(0xFFF2A93B)
val InkSoft = Color(0xFF5B6B4E)

@Composable
fun LimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = LimeDeep,
            onPrimary = Color.White,
            secondary = LimeMain,
            secondaryContainer = LimeSoft,
            background = LimeBg,
            surface = Color.White,
            surfaceVariant = Color(0xFFEAF5D8)
        ),
        content = content
    )
}

// ======================= 메인 화면 =======================
@Composable
fun MainScreen(c: LimeController, withMic: (() -> Unit) -> Unit) {
    var askClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(c.chat.size) {
        if (c.chat.isNotEmpty()) listState.animateScrollToItem(c.chat.size - 1)
    }

    Scaffold(
        containerColor = LimeBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("라임이 통역기", fontWeight = FontWeight.Bold, color = LimeDeep) },
                actions = {
                    IconButton(onClick = { c.cancelAll(); c.screen = Screen.SOUNDS }) {
                        Icon(Icons.Filled.List, contentDescription = "라임이 소리 모음", tint = LimeDeep)
                    }
                    IconButton(onClick = { askClear = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "기록 지우기", tint = InkSoft)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LimeBg)
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(LimeSoft)
                )
                Parrot(
                    talking = c.mode == Mode.LIME_TALKING,
                    listening = c.mode == Mode.HEARING_ME || c.mode == Mode.LISTENING_LIME,
                    level = c.level,
                    modifier = Modifier.size(185.dp)
                )
            }
            Text(
                c.status,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                textAlign = TextAlign.Center,
                color = InkSoft,
                fontSize = 14.sp
            )
            if (c.partial.isNotBlank()) {
                Text(
                    "“${c.partial}”",
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    textAlign = TextAlign.Center,
                    color = LimeDeep,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (c.mode == Mode.HEARING_ME || c.mode == Mode.LISTENING_LIME) {
                LinearProgressIndicator(
                    progress = { c.level },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 60.dp, vertical = 6.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = LimeMain,
                    trackColor = LimeSoft
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (c.chat.isEmpty()) {
                    item {
                        Text(
                            "🎤 라임이한테 말하기: 내가 한 말을 라임이 소리로 바꿔줘요\n" +
                                "👂 라임이 말 듣기: 라임이 소리를 한국어로 바꿔줘요\n" +
                                "📋 오른쪽 위 목록 버튼에서 라임이 진짜 소리를 녹음해 둘 수 있어요",
                            Modifier.padding(12.dp),
                            color = InkSoft,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
                items(c.chat, key = { it.id }) { m -> Bubble(m) }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BigButton(
                    emoji = "🎤",
                    label = if (c.mode == Mode.HEARING_ME) "말 다 했어요" else "라임이한테\n말하기",
                    color = LimeDeep,
                    active = c.mode == Mode.HEARING_ME,
                    modifier = Modifier.weight(1f)
                ) { withMic { c.onTalkButton() } }
                BigButton(
                    emoji = "👂",
                    label = if (c.mode == Mode.LISTENING_LIME) "다 들었어요" else "라임이 말\n듣기",
                    color = Sunny,
                    active = c.mode == Mode.LISTENING_LIME,
                    modifier = Modifier.weight(1f)
                ) { withMic { c.onListenButton() } }
            }
        }
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text("대화 기록 지우기") },
            text = { Text("라임이와 나눈 대화 기록을 모두 지울까요? (녹음한 라임이 소리는 그대로 남아요)") },
            confirmButton = { TextButton(onClick = { c.clearChat(); askClear = false }) { Text("지우기") } },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text("취소") } }
        )
    }
}

@Composable
private fun BigButton(
    emoji: String,
    label: String,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "btn")
    val pulse by inf.animateFloat(1f, 1.04f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "pulse")
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(104.dp)
            .scale(if (active) pulse else 1f),
        shape = RoundedCornerShape(30.dp),
        color = if (active) color.copy(alpha = 0.85f) else color,
        shadowElevation = 4.dp
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(if (active) "⏹" else emoji, fontSize = 30.sp)
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    when (m.sender) {
        Sender.SYSTEM -> Text(
            m.text,
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            textAlign = TextAlign.Center,
            color = InkSoft,
            fontSize = 13.sp
        )

        Sender.ME -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Surface(
                shape = RoundedCornerShape(20.dp, 6.dp, 20.dp, 20.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(m.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontSize = 16.sp)
            }
            if (m.sub != null) {
                Text(m.sub, fontSize = 11.sp, color = InkSoft, modifier = Modifier.padding(top = 3.dp, end = 6.dp))
            }
        }

        Sender.LIME -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LimeSoft),
                contentAlignment = Alignment.Center
            ) { Text("🦜", fontSize = 20.sp) }
            Spacer(Modifier.size(8.dp))
            Column {
                Text("라임이", fontSize = 12.sp, color = LimeDeep, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
                    color = Color(0xFFD7F0B0),
                    modifier = Modifier.widthIn(max = 270.dp)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(m.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        if (m.sub != null) {
                            Text(m.sub, fontSize = 11.sp, color = InkSoft, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

// ======================= 라임이 소리 모음 =======================
@Composable
fun SoundsScreen(c: LimeController, withMic: (() -> Unit) -> Unit) {
    BackHandler { c.cancelAll(); c.screen = Screen.MAIN }
    var editing by remember { mutableStateOf<SoundClip?>(null) }
    var deleting by remember { mutableStateOf<SoundClip?>(null) }

    Scaffold(
        containerColor = LimeBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("라임이 소리 모음", fontWeight = FontWeight.Bold, color = LimeDeep) },
                navigationIcon = {
                    IconButton(onClick = { c.cancelAll(); c.screen = Screen.MAIN }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = LimeDeep)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LimeBg)
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "라임이 소리를 녹음하고 분위기를 붙여 두면, 그 분위기의 말을 할 때 라임이 진짜 목소리로 들려줘요.",
                    color = InkSoft, fontSize = 14.sp, lineHeight = 21.sp
                )
            }
            item {
                Surface(
                    onClick = { withMic { c.toggleClipRecording() } },
                    shape = RoundedCornerShape(26.dp),
                    color = if (c.clipRecording) Color(0xFFE5534B) else LimeDeep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (c.clipRecording) "⏹ 녹음 멈추기" else "⏺ 라임이 소리 녹음하기 (최대 10초)",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp
                        )
                    }
                }
                if (c.clipRecording) {
                    LinearProgressIndicator(
                        progress = { c.level },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = LimeMain,
                        trackColor = LimeSoft
                    )
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("합성 지저귐에 내 목소리 섞기", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (c.voiceConflict) "이 폰은 음성인식 중 동시 녹음이 안 돼서 합성 소리만 써요. 껐다 켜면 다시 시도해요."
                                else "녹음이 없는 분위기일 때, 내 목소리를 높고 짧게 바꿔서 지저귀게 해요.",
                                fontSize = 12.sp, color = InkSoft
                            )
                        }
                        Switch(checked = c.useMyVoice && !c.voiceConflict, onCheckedChange = { c.setUseMyVoiceOption(it) })
                    }
                }
            }
            item {
                Text(
                    Mood.entries.joinToString("   ") { m -> "${m.emoji}${m.label} ${c.sounds.count { it.mood == m }}" },
                    fontSize = 13.sp, color = InkSoft, modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (c.sounds.isEmpty()) {
                item {
                    Text(
                        "아직 녹음한 소리가 없어요. 라임이가 지저귈 때 녹음 버튼을 눌러 보세요!",
                        Modifier.padding(vertical = 20.dp),
                        color = InkSoft
                    )
                }
            }
            items(c.sounds, key = { it.id }) { clip ->
                ClipRow(
                    clip = clip,
                    playing = c.playingClipId == clip.id,
                    onPlay = { c.playClip(clip) },
                    onEdit = { editing = clip },
                    onDelete = { deleting = clip }
                )
            }
        }
    }

    c.pendingClip?.let { pcm ->
        ClipDialog(
            title = "새 라임이 소리 저장",
            initialName = "라임이 소리 ${c.sounds.size + 1}",
            initialMood = Mood.GREETING,
            info = "길이 ${"%.1f".format(pcm.size / SAMPLE_RATE.toFloat())}초",
            onPreview = { c.previewPending() },
            onConfirm = { name, mood -> c.savePending(name, mood) },
            onDismiss = { c.discardPending() },
            dismissLabel = "버리기"
        )
    }
    editing?.let { clip ->
        ClipDialog(
            title = "소리 정보 바꾸기",
            initialName = clip.name,
            initialMood = clip.mood,
            info = null,
            onPreview = { c.playClip(clip) },
            onConfirm = { name, mood -> c.updateClip(clip, name, mood); editing = null },
            onDismiss = { editing = null },
            dismissLabel = "취소"
        )
    }
    deleting?.let { clip ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("소리 삭제") },
            text = { Text("'${clip.name}'을(를) 삭제할까요?") },
            confirmButton = { TextButton(onClick = { c.deleteClip(clip); deleting = null }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } }
        )
    }
}

@Composable
private fun ClipRow(
    clip: SoundClip,
    playing: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (playing) Sunny else LimeSoft)
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                if (playing) Text("⏹", fontSize = 18.sp)
                else Icon(Icons.Filled.PlayArrow, contentDescription = "듣기", tint = LimeDeep)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(clip.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    "${clip.mood.emoji} ${clip.mood.label} · ${"%.1f".format(clip.durationMs / 1000f)}초",
                    fontSize = 12.sp, color = InkSoft
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "이름·분위기 바꾸기", tint = InkSoft) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "삭제", tint = InkSoft) }
        }
    }
}

@Composable
private fun ClipDialog(
    title: String,
    initialName: String,
    initialMood: Mood,
    info: String?,
    onPreview: () -> Unit,
    onConfirm: (String, Mood) -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String
) {
    var name by remember { mutableStateOf(initialName) }
    var mood by remember { mutableStateOf(initialMood) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (info != null) Text(info, color = InkSoft, fontSize = 13.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("이름") },
                    singleLine = true
                )
                Text("분위기", fontWeight = FontWeight.SemiBold)
                Mood.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { m ->
                            FilterChip(
                                selected = mood == m,
                                onClick = { mood = m },
                                label = { Text("${m.emoji} ${m.label}") }
                            )
                        }
                    }
                }
                TextButton(onClick = onPreview) { Text("▶ 들어보기") }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim(), mood) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}
