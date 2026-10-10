package com.roxy.chatui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Paper = Color(0xFFF7F6F2)
private val Ink = Color(0xFF303030)
private val Grey = Color(0xFF888680)
private val Bubble = Color(0xFFFDFCF9)
private val Pale = Color(0xFFECEBE7)

private const val PREFS = "chat_ui_beautify"
private const val KEY_TITLE = "title"
private const val KEY_AVATAR = "avatar"

data class Msg(val id: Long, val text: String, val time: String, val mine: Boolean, val failed: Boolean = false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChatScreen() }
    }
}

@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var title by remember { mutableStateOf(prefs.getString(KEY_TITLE, "测试") ?: "测试") }
    var avatar by remember { mutableStateOf(prefs.getString(KEY_AVATAR, null)?.let(Uri::parse)) }
    var editingTitle by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            avatar = uri
            prefs.edit().putString(KEY_AVATAR, uri.toString()).apply()
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
        }
    }

    val messages = remember {
        mutableStateListOf(
            Msg(1, "你好呀，今天过得怎么样？", "18:02", false),
            Msg(2, "还不错，刚刚在弄这个界面。", "18:03", true),
            Msg(3, "这个风格我很喜欢。", "18:04", false),
            Msg(4, "嗯，我想要它看起来像手绘的小纸条。", "18:05", true),
            Msg(5, "明白啦。", "18:06", false)
        )
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun sendMessage() {
        val text = input.trim()
        if (text.isEmpty()) return
        val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        messages.add(Msg(System.currentTimeMillis(), text, now, true))
        input = ""
        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
    }

    Surface(color = Paper) {
        Box(Modifier.fillMaxSize()) {
            PatternBackground()
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    title = title,
                    onBack = { },
                    onTitle = { editingTitle = true },
                    onAvatar = { picker.launch(arrayOf("image/*")) },
                    menuOpen = menuOpen,
                    onMenuChange = { menuOpen = it }
                )
                DottedDivider()
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { msg -> MessageBubble(msg, avatar, onAvatar = { picker.launch(arrayOf("image/*")) }) }
                }
                InputBar(
                    value = input,
                    onValueChange = { input = it },
                    onSend = ::sendMessage,
                    onAvatar = { picker.launch(arrayOf("image/*")) }
                )
            }
        }
    }

    if (editingTitle) {
        AlertDialog(
            onDismissRequest = { editingTitle = false },
            title = { Text("修改标题", color = Ink) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putString(KEY_TITLE, title.ifBlank { "测试" }).apply()
                    title = title.ifBlank { "测试" }
                    editingTitle = false
                }) { Text("确定", color = Ink) }
            },
            dismissButton = {
                TextButton(onClick = { editingTitle = false }) { Text("取消", color = Grey) }
            }
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    onTitle: () -> Unit,
    onAvatar: () -> Unit,
    menuOpen: Boolean,
    onMenuChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = Ink) }
        Spacer(Modifier.weight(1f))
        BowIcon(Modifier.size(22.dp), Grey)
        Text(
            title,
            modifier = Modifier.padding(horizontal = 5.dp).clickable(onClick = onTitle),
            style = TextStyle(fontSize = 17.sp, color = Ink)
        )
        BowIcon(Modifier.size(22.dp), Grey, mirrored = true)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { }) { Icon(Icons.Default.StarBorder, "收藏", tint = Ink) }
        Box {
            IconButton(onClick = { onMenuChange(true) }) { Icon(Icons.Default.MoreVert, "更多", tint = Ink) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuChange(false) }) {
                DropdownMenuItem(
                    text = { Text("更换头像", color = Ink) },
                    onClick = { onMenuChange(false); onAvatar() }
                )
                DropdownMenuItem(
                    text = { Text("修改标题", color = Ink) },
                    onClick = { onMenuChange(false); onTitle() }
                )
            }
        }
    }
}

@Composable
private fun BowIcon(modifier: Modifier, color: Color, mirrored: Boolean = false) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx - size.width * .38f, cy - size.height * .42f, cx - size.width * .52f, cy + size.height * .25f, cx, cy)
            cubicTo(cx + size.width * .52f, cy - size.height * .25f, cx + size.width * .38f, cy + size.height * .42f, cx, cy)
        }
        if (mirrored) {
            scale(scaleX = -1f, scaleY = 1f, pivot = Offset(cx, cy)) { drawPath(path, color, style = Stroke(1.5f)) }
        } else drawPath(path, color, style = Stroke(1.5f))
        drawCircle(color, radius = size.minDimension * .09f, center = Offset(cx, cy))
    }
}

@Composable
private fun DottedDivider() {
    Canvas(Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 12.dp)) {
        drawLine(
            Grey.copy(alpha = .38f),
            Offset.Zero,
            Offset(size.width, 0f),
            strokeWidth = 1.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f))
        )
    }
}

@Composable
private fun MessageBubble(msg: Msg, avatar: Uri?, onAvatar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!msg.mine) {
            Avatar(avatar, Modifier.size(38.dp), onClick = onAvatar)
            Spacer(Modifier.width(7.dp))
        }
        Column(horizontalAlignment = if (msg.mine) Alignment.End else Alignment.Start) {
            Box(
                Modifier
                    .widthIn(max = 285.dp)
                    .background(Bubble, RoundedCornerShape(13.dp))
                    .handDrawnBorder()
                    .padding(horizontal = 13.dp, vertical = 9.dp)
            ) { Text(msg.text, color = Ink, fontSize = 15.sp, lineHeight = 21.sp) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp)
            ) {
                Text(msg.time, color = Grey, fontSize = 10.sp)
                if (msg.mine && !msg.failed) Text("  已读", color = Grey, fontSize = 10.sp)
                if (msg.failed) Text("  ! 发送失败", color = Grey, fontSize = 10.sp)
            }
        }
        if (msg.mine) {
            Spacer(Modifier.width(7.dp))
            Avatar(avatar, Modifier.size(38.dp), onClick = onAvatar)
        }
    }
}

private fun Modifier.handDrawnBorder() = drawBehind {
    val inset = 0.8.dp.toPx()
    drawRoundRect(
        color = Grey.copy(alpha = .72f),
        topLeft = Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(13.dp.toPx()),
        style = Stroke(width = 1.15.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 3.5f)))
    )
}

@Composable
private fun Avatar(uri: Uri?, modifier: Modifier, onClick: (() -> Unit)?) {
    val clickable = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    if (uri != null) {
        SelectedAvatar(uri, clickable)
    } else {
        Box(
            clickable.clip(CircleShape).background(Pale).handDrawnCircleBorder(),
            contentAlignment = Alignment.Center
        ) { Text("♡", color = Ink, fontSize = 18.sp) }
    }
}

private fun Modifier.handDrawnCircleBorder() = drawBehind {
    drawCircle(
        color = Grey.copy(alpha = .55f),
        radius = size.minDimension / 2f - 1.dp.toPx(),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2f)))
    )
}

@Composable
private fun SelectedAvatar(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(uri) { loadBitmap(context, uri) }
    if (bitmap != null) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "头像", modifier = modifier.clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.clip(CircleShape).background(Pale), contentAlignment = Alignment.Center) { Text("♡", color = Ink) }
    }
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
} catch (_: Exception) { null }

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAvatar: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { }, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.Add, "添加", tint = Ink, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(3.dp))
        Text("⌁", modifier = Modifier.clickable(onClick = onAvatar), color = Ink, fontSize = 24.sp)
        Spacer(Modifier.width(7.dp))
        Box(
            Modifier.weight(1f).height(43.dp).background(Bubble, RoundedCornerShape(22.dp)).handDrawnBorder().padding(horizontal = 15.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 14.sp),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text("输入消息…", color = Grey, fontSize = 14.sp)
                    inner()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(5.dp))
        SmallIcon(Icons.Default.EmojiEmotions, "表情")
        Spacer(Modifier.width(3.dp))
        IconButton(onClick = onSend, modifier = Modifier.size(43.dp).clip(CircleShape).background(Ink)) {
            Icon(Icons.Default.Send, "发送", tint = Paper, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String) {
    IconButton(onClick = { }, modifier = Modifier.size(38.dp)) { Icon(icon, desc, tint = Ink, modifier = Modifier.size(21.dp)) }
}

@Composable
private fun PatternBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val faint = Grey.copy(alpha = .065f)
        drawCircle(faint, radius = 20f, center = Offset(size.width * .12f, size.height * .18f), style = Stroke(1.5f))
        drawCircle(faint, radius = 7f, center = Offset(size.width * .87f, size.height * .28f), style = Stroke(1.5f))
        drawHeart(this, Offset(size.width * .78f, size.height * .72f), faint)
        drawBow(this, Offset(size.width * .17f, size.height * .78f), faint)
        drawPaw(this, Offset(size.width * .90f, size.height * .83f), faint)
        drawTinyFlower(this, Offset(size.width * .35f, size.height * .32f), faint)
        drawTinyFlower(this, Offset(size.width * .58f, size.height * .88f), faint)
    }
}

private fun drawHeart(scope: DrawScope, c: Offset, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + 12)
        cubicTo(c.x - 26, c.y - 5, c.x - 15, c.y - 25, c.x, c.y - 12)
        cubicTo(c.x + 15, c.y - 25, c.x + 26, c.y - 5, c.x, c.y + 12)
    }
    scope.drawPath(p, color, style = Stroke(1.6f))
}

private fun drawBow(scope: DrawScope, c: Offset, color: Color) {
    scope.drawOval(Rect(c.x - 25, c.y - 12, c.x, c.y + 12), color, style = Stroke(1.6f))
    scope.drawOval(Rect(c.x, c.y - 12, c.x + 25, c.y + 12), color, style = Stroke(1.6f))
    scope.drawCircle(c, 5f, color, style = Stroke(1.6f))
}

private fun drawPaw(scope: DrawScope, c: Offset, color: Color) {
    scope.drawCircle(c, 9f, color, style = Stroke(1.6f))
    scope.drawCircle(c + Offset(-11f, -9f), 4f, color, style = Stroke(1.6f))
    scope.drawCircle(c + Offset(0f, -13f), 4f, color, style = Stroke(1.6f))
    scope.drawCircle(c + Offset(11f, -9f), 4f, color, style = Stroke(1.6f))
}

private fun drawTinyFlower(scope: DrawScope, c: Offset, color: Color) {
    for (i in 0 until 5) {
        val a = Math.toRadians((i * 72.0) - 90.0)
        val p = Offset(c.x + kotlin.math.cos(a).toFloat() * 7f, c.y + kotlin.math.sin(a).toFloat() * 7f)
        scope.drawCircle(p, 4f, color, style = Stroke(1.2f))
    }
    scope.drawCircle(c, 2f, color, style = Stroke(1.2f))
}
