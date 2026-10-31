@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lime.translator

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
fun MainScreen(c: LimeController, withMic: (() -> Unit) -> Unit, onPickPhoto: () -> Unit) {
    var askClear by remember { mutableStateOf(false) }
    var askPhoto by remember { mutableStateOf(false) }
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
                        .size(185.dp)
                        .clickable { askPhoto = true },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(180.dp)
                            .clip(CircleShape)
                            .background(LimeSoft)
                    )
                    val photo = c.avatar
                    if (photo != null) {
                        PhotoParrot(
                            img = photo,
                            talking = c.mode == Mode.LIME_TALKING,
                            listening = c.mode == Mode.HEARING_ME || c.mode == Mode.LISTENING_LIME,
                            modifier = Modifier.size(172.dp)
                        )
                    } else {
                        Parrot(
                            talking = c.mode == Mode.LIME_TALKING,
                            listening = c.mode == Mode.HEARING_ME || c.mode == Mode.LISTENING_LIME,
                            level = c.level,
                            modifier = Modifier.size(185.dp)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("📷", fontSize = 18.sp) }
                    }
                }
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
                                "📷 위의 라임이 그림을 누르면 사진으로 바꿀 수 있어요",
                            Modifier.padding(12.dp),
                            color = InkSoft,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
                items(c.chat, key = { it.id }) { m -> Bubble(m, c.avatar) }
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
            text = { Text("라임이와 나눈 대화 기록을 모두 지울까요?") },
            confirmButton = { TextButton(onClick = { c.clearChat(); askClear = false }) { Text("지우기") } },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text("취소") } }
        )
    }

    if (askPhoto) {
        AlertDialog(
            onDismissRequest = { askPhoto = false },
            title = { Text("라임이 사진") },
            text = { Text("라임이 사진을 골라서 그림 대신 보여줄 수 있어요.") },
            confirmButton = {
                TextButton(onClick = { askPhoto = false; onPickPhoto() }) { Text("사진 고르기") }
            },
            dismissButton = {
                Row {
                    if (c.avatar != null) {
                        TextButton(onClick = { askPhoto = false; c.resetAvatar() }) { Text("기본 그림으로") }
                    }
                    TextButton(onClick = { askPhoto = false }) { Text("취소") }
                }
            }
        )
    }
}

/** 업로드한 라임이 사진 (말할 때 통통 튀고, 들을 때 갸웃) */
@Composable
fun PhotoParrot(img: ImageBitmap, talking: Boolean, listening: Boolean, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "photo")
    val hop by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(160), RepeatMode.Reverse), label = "hop")
    val wig by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(240), RepeatMode.Reverse), label = "wig")
    val breathe by inf.animateFloat(1f, 1.03f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "breathe")
    val tilt by animateFloatAsState(if (listening) -10f else 0f, label = "tilt")
    Image(
        bitmap = img,
        contentDescription = "라임이",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .graphicsLayer {
                val sc = if (talking) 1f + hop * 0.06f else breathe
                scaleX = sc
                scaleY = sc
                rotationZ = tilt + if (talking) wig * 5f else 0f
                translationY = if (talking) -hop * 8f else 0f
            }
            .clip(CircleShape)
            .border(4.dp, LimeMain, CircleShape)
    )
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
private fun Bubble(m: ChatMessage, avatar: ImageBitmap?) {
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
        }

        Sender.LIME -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LimeSoft),
                contentAlignment = Alignment.Center
            ) {
                if (avatar != null) {
                    Image(
                        bitmap = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Text("🦜", fontSize = 20.sp)
                }
            }
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
                    }
                }
            }
        }
    }
}

