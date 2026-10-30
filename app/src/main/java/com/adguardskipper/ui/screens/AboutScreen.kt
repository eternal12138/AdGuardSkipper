package com.adguardskipper.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adguardskipper.R
import com.adguardskipper.ui.components.CialloCard
import com.adguardskipper.ui.components.CialloIconTile
import com.adguardskipper.ui.components.UserAgreementDialog
import com.adguardskipper.ui.theme.CialloPalette
import kotlinx.coroutines.launch

private const val GITHUB_URL = "https://github.com/eternal12138"
private const val GITHUB_DISPLAY = "github.com/eternal12138"

@Composable
fun AboutScreen(
    versionLabel: String,
    onRevokeAgreement: () -> Unit,
    buildDebugExport: suspend () -> String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAgreementReader by rememberSaveable { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    if (showAgreementReader) {
        UserAgreementDialog(
            isFirstRun = false,
            onAccept = { showAgreementReader = false },
            onDecline = { showAgreementReader = false },
            onDismiss = { showAgreementReader = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, top = 4.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AboutBrandCard(versionLabel = versionLabel)

        AgreementCard(
            onOpenAgreement = { showAgreementReader = true },
            onRevokeAgreement = {
                onRevokeAgreement()
                Toast.makeText(
                    context,
                    "已撤回协议同意，自动跳过服务已停用",
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )

        ContactCard(
            exporting = exporting,
            onOpenGithub = { openExternalUrl(context, GITHUB_URL) },
            onCopyGithub = {
                copyToClipboard(context, "Ciallo 跳广 GitHub", GITHUB_URL)
            },
            onExport = {
                if (!exporting) {
                    exporting = true
                    scope.launch {
                        runCatching { buildDebugExport() }
                            .onSuccess { json ->
                                val shared = shareDebugExport(context, json)
                                Toast.makeText(
                                    context,
                                    if (shared) {
                                        "调试信息已生成，正在打开分享面板"
                                    } else {
                                        "已保存到应用私有目录，并复制到剪贴板"
                                    },
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            .onFailure { error ->
                                Toast.makeText(
                                    context,
                                    "导出失败：${error.message ?: "未知错误"}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        exporting = false
                    }
                }
            },
        )

        Text(
            text = "Ciallo～跳广 · 纯本地运行 · 无广告无联网",
            style = MaterialTheme.typography.labelSmall,
            color = CialloPalette.Muted.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun AboutBrandCard(versionLabel: String) {
    CialloCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color.White.copy(alpha = 0.84f),
        borderColor = CialloPalette.Blue.copy(alpha = 0.16f),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val iconShape = RoundedCornerShape(22.dp)
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .shadow(10.dp, iconShape)
                    .clip(iconShape)
                    .background(Color.White)
                    .border(1.5.dp, CialloPalette.Navy.copy(alpha = 0.14f), iconShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "Ciallo～跳广 应用图标",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp)
                        .clip(RoundedCornerShape(18.dp)),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Ciallo～(∠・ω< )⌒☆跳广",
                style = MaterialTheme.typography.titleLarge,
                color = CialloPalette.Navy,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = versionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "⚡ 广告秒跳过 · 纯本地零联网守护开屏净土",
                style = MaterialTheme.typography.bodySmall,
                color = CialloPalette.BlueDeep,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AgreementCard(
    onOpenAgreement: () -> Unit,
    onRevokeAgreement: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color.White.copy(alpha = 0.84f),
        ),
        border = BorderStroke(1.5.dp, CialloPalette.Blue.copy(alpha = 0.16f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onOpenAgreement)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CialloIconTile(
                    icon = Icons.Outlined.Description,
                    tint = CialloPalette.Blue,
                    containerColor = CialloPalette.Ice,
                    size = 46.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "用户协议与隐私声明",
                        style = MaterialTheme.typography.titleMedium,
                        color = CialloPalette.Navy,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "100% 纯本地运行承诺与权限用途说明",
                        style = MaterialTheme.typography.bodySmall,
                        color = CialloPalette.Muted,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = CialloPalette.Muted,
                )
            }
            HorizontalDivider(
                color = CialloPalette.Navy.copy(alpha = 0.07f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRevokeAgreement) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        tint = CialloPalette.Muted,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "撤回协议同意并停用服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = CialloPalette.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactCard(
    exporting: Boolean,
    onOpenGithub: () -> Unit,
    onCopyGithub: () -> Unit,
    onExport: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color.White.copy(alpha = 0.84f),
        ),
        border = BorderStroke(1.5.dp, CialloPalette.Blue.copy(alpha = 0.16f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "作者与联系方式",
                style = MaterialTheme.typography.titleMedium,
                color = CialloPalette.Navy,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CialloIconTile(
                    icon = Icons.Outlined.Person,
                    tint = CialloPalette.Blue,
                    containerColor = CialloPalette.Ice,
                    size = 38.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "开发者：Lir Moriarty",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CialloPalette.Navy,
                )
            }

            HorizontalDivider(color = CialloPalette.Navy.copy(alpha = 0.07f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onOpenGithub)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CialloIconTile(
                    icon = Icons.Outlined.Code,
                    tint = CialloPalette.BlueDeep,
                    containerColor = CialloPalette.Lavender,
                    size = 38.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "开源主页 / 项目地址",
                        style = MaterialTheme.typography.labelSmall,
                        color = CialloPalette.Muted,
                    )
                    Text(
                        text = GITHUB_DISPLAY,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CialloPalette.BlueDeep,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onCopyGithub) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "复制 GitHub 链接",
                        tint = CialloPalette.Blue,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            HorizontalDivider(color = CialloPalette.Navy.copy(alpha = 0.07f))

            Text(
                text = "反馈与建议",
                style = MaterialTheme.typography.titleSmall,
                color = CialloPalette.Navy,
            )

            Button(
                onClick = onExport,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CialloPalette.Blue,
                    contentColor = Color.White,
                    disabledContainerColor = CialloPalette.Ice,
                    disabledContentColor = CialloPalette.Muted,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (exporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (exporting) {
                        "正在生成调试信息…"
                    } else {
                        "📤 导出调试日志与自定义规则"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = "导出内容仅在本机生成，包含自定义规则、最近拦截记录与设备型号，不会自动上传。",
                style = MaterialTheme.typography.bodySmall,
                color = CialloPalette.Muted,
            )
        }
    }
}

private fun openExternalUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (error: Exception) {
        Toast.makeText(
            context,
            "无法打开浏览器：${error.message ?: "未知错误"}",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun copyToClipboard(
    context: Context,
    label: String,
    text: String,
    showToast: Boolean = true,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        if (showToast) {
            Toast.makeText(context, "当前设备不支持剪贴板", Toast.LENGTH_SHORT).show()
        }
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    if (showToast) {
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}

private fun shareDebugExport(context: Context, json: String): Boolean {
    return try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Ciallo～跳广 调试信息")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        context.startActivity(Intent.createChooser(sendIntent, "导出调试日志与自定义规则"))
        true
    } catch (error: Exception) {
        copyToClipboard(context, "Ciallo 调试信息", json, showToast = false)
        false
    }
}
