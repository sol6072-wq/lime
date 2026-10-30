package com.lime.translator

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

private val BodyGreen = Color(0xFF7CCB3A)
private val BellyGreen = Color(0xFFA6E36A)
private val WingGreen = Color(0xFF4E9A2A)
private val WingLine = Color(0xFF2F5E1A)
private val FaceYellow = Color(0xFFF7E15A)
private val TailBlue = Color(0xFF2E7DAF)
private val CheekBlue = Color(0xFF3F6FD8)
private val CereBlue = Color(0xFF6FA8E8)
private val BeakTop = Color(0xFFF2C46B)
private val BeakBottom = Color(0xFFDDA24A)
private val Perch = Color(0xFFA9774A)
private val Feet = Color(0xFFE6A8A0)

/** 연두색 사랑앵무 라임이 (말할 때 부리·날개가 움직임) */
@Composable
fun Parrot(talking: Boolean, listening: Boolean, level: Float, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "parrot")
    val beak by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(140, easing = LinearEasing), RepeatMode.Reverse), label = "beak"
    )
    val flap by inf.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(200, easing = LinearEasing), RepeatMode.Reverse), label = "flap"
    )
    val bob by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob"
    )
    val tilt by animateFloatAsState(if (listening) -12f else 0f, label = "tilt")
    val beakOpen = if (talking) beak else 0f
    val wing = if (talking) flap else 0f

    Canvas(modifier) {
        val s = size.minDimension / 200f
        translate((size.width - 200f * s) / 2f, (size.height - 200f * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                drawParrot(beakOpen, wing, bob * 3f + if (talking) beak * 1.5f else 0f, tilt, listening, level, talking)
            }
        }
    }
}

private fun DrawScope.drawParrot(
    beakOpen: Float, wing: Float, bobY: Float, tilt: Float,
    listening: Boolean, level: Float, talking: Boolean
) {
    // 횃대
    drawRoundRect(Perch, Offset(28f, 168f), Size(144f, 11f), CornerRadius(6f, 6f))

    translate(0f, bobY) {
        // 꼬리
        val tail = Path().apply {
            moveTo(86f, 138f); lineTo(70f, 140f); lineTo(46f, 196f); lineTo(58f, 198f); close()
        }
        drawPath(tail, TailBlue)

        // 몸통 + 배
        drawOval(BodyGreen, Offset(64f, 72f), Size(72f, 96f))
        drawOval(BellyGreen, Offset(86f, 96f), Size(46f, 66f))

        // 발
        drawOval(Feet, Offset(92f, 162f), Size(10f, 9f))
        drawOval(Feet, Offset(108f, 162f), Size(10f, 9f))

        // 날개 (어깨 기준으로 퍼덕)
        rotate(wing * 22f, pivot = Offset(90f, 96f)) {
            val w = Path().apply {
                moveTo(90f, 92f)
                cubicTo(70f, 100f, 64f, 136f, 76f, 160f)
                cubicTo(92f, 152f, 106f, 126f, 102f, 100f)
                close()
            }
            drawPath(w, WingGreen)
            for (i in 0..3) {
                val y = 106f + i * 12f
                val p = Path().apply {
                    moveTo(76f + i * 0.5f, y)
                    quadraticBezierTo(86f, y + 7f, 98f - i * 2f, y - 1f)
                }
                drawPath(p, WingLine, style = Stroke(width = 2f, cap = StrokeCap.Round))
            }
        }

        // 머리 (들을 때 갸웃)
        rotate(tilt, pivot = Offset(110f, 88f)) {
            val headCenter = Offset(112f, 62f)
            drawCircle(FaceYellow, 30f, headCenter)

            // 뒷머리 물결 무늬
            val headClip = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(headCenter, 30f))
            }
            clipPath(headClip) {
                for (i in 0..4) {
                    val y = 40f + i * 7f
                    val p = Path().apply {
                        moveTo(80f, y + 4f)
                        quadraticBezierTo(92f, y - 3f, 104f - i * 2f, y + 3f)
                    }
                    drawPath(p, Color(0xFF3A3A2A), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
                }
            }

            // 볼 반점, 목 점
            drawCircle(CheekBlue, 4.5f, Offset(121f, 82f))
            drawCircle(Color(0xFF222222), 1.8f, Offset(112f, 90f))
            drawCircle(Color(0xFF222222), 1.8f, Offset(126f, 89f))

            // 눈
            drawCircle(Color.White, 7f, Offset(123f, 56f))
            drawCircle(Color(0xFF1B1B1B), 5.2f, Offset(123.5f, 56f))
            drawCircle(Color.White, 1.7f, Offset(125.5f, 54f))

            // 아랫부리 (말할 때 벌어짐)
            rotate(beakOpen * 24f, pivot = Offset(132f, 74f)) {
                val lower = Path().apply {
                    moveTo(131f, 73f)
                    quadraticBezierTo(141f, 76f, 139f, 86f)
                    quadraticBezierTo(133f, 84f, 130f, 78f)
                    close()
                }
                drawPath(lower, BeakBottom)
            }
            // 윗부리
            val upper = Path().apply {
                moveTo(131f, 64f)
                quadraticBezierTo(150f, 66f, 143f, 84f)
                quadraticBezierTo(137f, 77f, 130f, 74f)
                close()
            }
            drawPath(upper, BeakTop)
            // 납막(코)
            drawOval(CereBlue, Offset(128f, 59f), Size(11f, 7f))

            // 듣는 중: 귀 옆 소리 물결
            if (listening) {
                val a = 0.35f + level * 0.65f
                for (i in 0..2) {
                    drawArc(
                        Color(0xFF4E8A1E).copy(alpha = a * (1f - i * 0.25f)),
                        startAngle = 140f, sweepAngle = 80f, useCenter = false,
                        topLeft = Offset(52f - i * 9f, 34f - i * 9f),
                        size = Size(40f + i * 18f, 40f + i * 18f),
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )
                }
            }
            // 말하는 중: 부리 앞 음표 방울
            if (talking) {
                drawCircle(Color(0xFFFFC94D), 4f + beakOpen * 2f, Offset(158f, 70f - beakOpen * 6f))
                drawCircle(Color(0xFF8BC34A), 3f + (1f - beakOpen) * 2f, Offset(170f, 56f + beakOpen * 4f))
                drawCircle(Color(0xFFFFC94D), 2.5f, Offset(178f, 76f - beakOpen * 3f))
            }
        }
    }
}
