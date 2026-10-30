package com.adguardskipper.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adguardskipper.R
import com.adguardskipper.ui.components.CialloBackdrop
import com.adguardskipper.ui.components.PixelTag
import com.adguardskipper.ui.theme.CialloPalette

/**
 * Launch showcase. The second Ciallo key art is presented as a floating
 * "sticker poster" so the whole illustration stays visible on any aspect ratio,
 * with an arcade loading strip underneath.
 */
@Composable
fun CialloSplashScreen() {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val posterScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.94f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "cialloPosterScale",
    )
    val posterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "cialloPosterAlpha",
    )
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0.06f,
        animationSpec = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
        label = "cialloSplashProgress",
    )

    CialloBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelTag(
                    text = "CIALLO",
                    containerColor = CialloPalette.Lemon,
                    contentColor = CialloPalette.Navy,
                )
                Spacer(Modifier.weight(1f))
                PixelTag(
                    text = stringResource(R.string.splash_badge),
                    containerColor = CialloPalette.Blue,
                    contentColor = Color.White,
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val posterWidth = minOf(
                    maxWidth * 0.94f,
                    (maxHeight - 6.dp) / (1024f / 687f),
                    420.dp,
                )
                PosterCard(
                    modifier = Modifier
                        .scale(posterScale)
                        .width(posterWidth)
                        .aspectRatio(687f / 1024f)
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(30.dp),
                            ambientColor = CialloPalette.Blue.copy(alpha = 0.35f),
                            spotColor = CialloPalette.Navy.copy(alpha = 0.35f),
                        )
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color.White)
                        .padding(5.dp)
                        .border(
                            width = 1.5.dp,
                            color = CialloPalette.Navy.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(30.dp),
                        ),
                    alpha = posterAlpha,
                )
            }

            Spacer(Modifier.height(14.dp))
            SplashLoadingStrip(progress = progress)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "广告秒跳过 · ADS SKIPPER",
                style = MaterialTheme.typography.labelSmall,
                color = CialloPalette.Navy.copy(alpha = 0.55f),
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PosterCard(
    modifier: Modifier = Modifier,
    alpha: Float,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.splash_ciallo),
            contentDescription = stringResource(R.string.app_name_short),
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
                .clip(RoundedCornerShape(25.dp)),
            contentScale = ContentScale.Crop,
            alpha = alpha,
        )
    }
}

@Composable
private fun SplashLoadingStrip(progress: Float) {
    val transition = rememberInfiniteTransition(label = "cialloSplashLoader")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cialloLoaderRotation",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cialloLoaderPulse",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.86f))
            .border(
                width = 1.5.dp,
                color = CialloPalette.Navy.copy(alpha = 0.10f),
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = CialloPalette.Lemon,
                modifier = Modifier
                    .size(25.dp)
                    .rotate(rotation),
            )
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = CialloPalette.Blue,
                modifier = Modifier
                    .size(11.dp)
                    .rotate(-rotation)
                    .scale(pulse),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.splash_loading_text),
                style = MaterialTheme.typography.titleSmall,
                color = CialloPalette.Navy,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(CialloPalette.Ice),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    CialloPalette.Cyan,
                                    CialloPalette.Blue,
                                ),
                            ),
                        ),
                )
            }
        }
        Text(
            text = stringResource(R.string.splash_skip_chip),
            style = MaterialTheme.typography.labelSmall,
            color = CialloPalette.Blue,
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(CialloPalette.Lemon.copy(alpha = 0.55f))
                .border(
                    1.dp,
                    CialloPalette.Navy.copy(alpha = 0.10f),
                    RoundedCornerShape(7.dp),
                )
                .padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
}
