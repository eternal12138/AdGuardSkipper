package com.adguardskipper.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adguardskipper.R
import com.adguardskipper.ui.theme.CialloPalette

/**
 * The consent gate and the read-only agreement reader.
 *
 * First-run mode is deliberately impossible to dismiss by tapping outside or
 * pressing back: the user must explicitly accept or leave the app. Reader mode
 * (opened from About) only exposes a single "我知道了" action.
 */
@Composable
fun UserAgreementDialog(
    isFirstRun: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!isFirstRun) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isFirstRun,
            dismissOnClickOutside = !isFirstRun,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, CialloPalette.Navy.copy(alpha = 0.12f)),
            shadowElevation = 16.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AgreementHeader(isFirstRun = isFirstRun)
                HorizontalDivider(color = CialloPalette.Navy.copy(alpha = 0.08f))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AgreementSectionTitle("一、软件性质与服务说明")
                    AgreementParagraph(
                        "本软件（Ciallo～(∠・ω< )⌒☆跳广）是一款基于 Android 官方无障碍服务" +
                            "（AccessibilityService）开发的个人免费效率辅助工具，纯技术交流作品，" +
                            "不包含任何商业盈利或广告。",
                    )

                    AgreementSectionTitle("二、隐私承诺（100% 纯本地运行）")
                    AgreementParagraph(
                        "本软件郑重承诺绝不联网收集、存储或上传任何用户隐私数据。" +
                            "四项核心权限唯一用途如下：",
                    )
                    AgreementPermissionItem(
                        title = "【无障碍服务】",
                        body = "仅用于实时识别当前屏幕的“跳过/关闭”节点并模拟点击或返回操作，" +
                            "绝不读取密码或聊天内容。",
                    )
                    AgreementPermissionItem(
                        title = "【读取应用列表】",
                        body = "仅用于在本地为您展示应用名单以配置“白名单”与“防强跳黑名单”。",
                    )
                    AgreementPermissionItem(
                        title = "【悬浮窗权限】",
                        body = "仅在您主动开启“布局抓取悬浮球”时，用于辅助点选屏幕控件生成自定义规则。",
                    )
                    AgreementPermissionItem(
                        title = "【通知监听服务】",
                        body = "仅用于辅助维持无障碍服务在系统后台的存活率，" +
                            "绝不读取、解析或记录任何通知消息内容。",
                    )

                    AgreementSectionTitle("三、免责声明与风险提示")
                    AgreementParagraph(
                        "因第三方应用界面频繁变动或用户自行添加“自定义坐标/控件规则”可能带来的" +
                            "操作误触风险由用户自行承担，开发者不承担任何直接或间接责任。" +
                            "本软件严禁用于任何违反法律法规或破坏第三方计算机信息系统的用途。",
                    )

                    if (isFirstRun) {
                        Text(
                            text = "点击“同意并继续”即表示您已阅读、理解并同意上述全部条款。",
                            style = MaterialTheme.typography.bodySmall,
                            color = CialloPalette.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                }
                HorizontalDivider(color = CialloPalette.Navy.copy(alpha = 0.08f))
                AgreementActions(
                    isFirstRun = isFirstRun,
                    onAccept = onAccept,
                    onDecline = onDecline,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun AgreementHeader(isFirstRun: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.White)
                    .padding(3.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isFirstRun) "欢迎使用 Ciallo～跳广" else "用户协议与隐私声明",
                    style = MaterialTheme.typography.titleMedium,
                    color = CialloPalette.Navy,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isFirstRun) {
                        "首次使用前，请阅读并同意以下条款"
                    } else {
                        "100% 纯本地运行承诺与权限用途说明"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = CialloPalette.Muted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelTag(
                text = "100% 纯本地",
                containerColor = CialloPalette.Ice,
                contentColor = CialloPalette.BlueDeep,
            )
            PixelTag(
                text = "ZERO NETWORK",
                containerColor = CialloPalette.Lemon,
                contentColor = CialloPalette.Navy,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = "↓ 上下滑动阅读完整协议",
            style = MaterialTheme.typography.labelSmall,
            color = CialloPalette.Muted.copy(alpha = 0.82f),
        )
    }
}

@Composable
private fun AgreementSectionTitle(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(17.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CialloPalette.Blue),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = CialloPalette.Navy,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AgreementParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AgreementPermissionItem(
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CialloPalette.Ice.copy(alpha = 0.42f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(CialloPalette.Coral),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = CialloPalette.Navy,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AgreementActions(
    isFirstRun: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFirstRun) {
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, CialloPalette.Coral.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CialloPalette.CoralDeep,
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 11.dp),
            ) {
                Text(
                    text = "拒绝并退出",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1.35f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CialloPalette.Blue,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 11.dp),
            ) {
                Text(
                    text = "同意并继续",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        } else {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CialloPalette.Blue,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("我知道了", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
