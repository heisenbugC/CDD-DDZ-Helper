package com.example.demo.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo.domain.*
import com.example.demo.rules.Variation
import com.example.demo.services.GameVoiceCommand
import com.example.demo.services.SpeechManager

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    speechManager: SpeechManager,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Verification test states
    var isMicTesting by remember { mutableStateOf(false) }
    var lastDetectedCommand by remember { mutableStateOf<GameVoiceCommand?>(null) }

    // Start/Stop mic test listener
    LaunchedEffect(isMicTesting) {
        if (isMicTesting) {
            speechManager.startListening { cmd ->
                lastDetectedCommand = cmd
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isMicTesting) {
                speechManager.stopListening()
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF0F1015),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181A20))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.CenterStart)
                        .clickable { onBack() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("←", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "应用设置 / Settings",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Section 1: Game Mode & Rules
            SettingsCard(title = "🎮 游戏规则设置 (Game Rules)") {
                Text("游戏模式 (Game Mode):", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GameMode.entries.forEach { mode ->
                        val isSelected = settings.gameMode == mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF2979FF) else Color(0xFF21242D),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSettingsChanged(settings.copy(gameMode = mode)) }
                        ) {
                            Text(
                                text = mode.label,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                if (settings.gameMode == GameMode.DOU_DI_ZHU) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("斗地主手牌目标 (Role Target):", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DdzRole.entries.forEach { role ->
                            val isSelected = settings.ddzRole == role
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFFFF9100) else Color(0xFF21242D),
                                modifier = Modifier
                                .weight(1f)
                                .clickable { onSettingsChanged(settings.copy(ddzRole = role)) }
                            ) {
                                Text(
                                    text = role.label,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("五张牌比较规则 (5-Card Comparison):", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Variation.entries.forEach { variation ->
                            val isSelected = settings.cddVariation == variation
                            val label = if (variation == Variation.MAINLAND) {
                                "大陆规则 (同花顺 > 铁支 > 葫芦 > 同花 > 顺子)"
                            } else {
                                "香港规则 (同花顺 > 铁支 > 葫芦 > 顺子 > 同花)"
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF00C853) else Color(0xFF21242D),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSettingsChanged(settings.copy(cddVariation = variation)) }
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: Voice Options
            SettingsCard(title = "🎙️ 语音播报选项 (Voice Options)") {
                // Voice Trigger Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "后台语音监听 (Voice Trigger)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "开启后通过语音自动执行锁定/报牌/新一局",
                            color = Color(0xFF69F0AE),
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = settings.voiceTriggerEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(voiceTriggerEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E676))
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Voice Language
                Text("播报语言 (Speech Language):", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoiceLanguage.entries.forEach { lang ->
                        val isSelected = settings.voiceLanguage == lang
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF7C4DFF) else Color(0xFF21242D),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSettingsChanged(settings.copy(voiceLanguage = lang)) }
                        ) {
                            Text(
                                text = lang.label,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // Voice Testing Sub-Card: Output (TTS) & Input (STT) Live Test
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF161922),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "🎙️ 声音测试与诊断 (Voice Testing - ${settings.voiceLanguage.label})",
                            color = Color(0xFF00E5FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // TTS Engine Status Pill & Open System Settings
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1C202A),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            if (speechManager.ttsStatus.value.contains("就绪") || speechManager.ttsStatus.value.contains("Ready")) {
                                                Color(0xFF00E676)
                                            } else {
                                                Color(0xFFFF9100)
                                            },
                                            CircleShape
                                        )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("TTS 引擎状态:", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                                    Text(
                                        text = speechManager.ttsStatus.value,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                OutlinedButton(
                                    onClick = { speechManager.openSystemTtsSettings() },
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF80D8FF).copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "⚙️ 系统设置",
                                        color = Color(0xFF80D8FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Dual Test Buttons: Output & Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. TTS Output Test Button
                            Button(
                                onClick = {
                                    speechManager.ensureAudibleVolume()
                                    val testText = when (settings.voiceLanguage) {
                                        VoiceLanguage.CANTONESE -> "語音設定測試成功。已經識別17張手牌，推薦出牌王炸！"
                                        VoiceLanguage.CHINESE -> "语音设置测试成功。已识别17张手牌，推荐出牌王炸！"
                                        VoiceLanguage.ENGLISH -> "Voice settings test successful. 17 cards recognized, best hand is ready!"
                                    }
                                    speechManager.speak(
                                        text = testText,
                                        language = settings.voiceLanguage.code,
                                        rate = settings.speechRate,
                                        pitch = settings.speechPitch
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (speechManager.isSpeaking.value) "🔊 播报中..." else "🔊 测试播报 (TTS)",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (speechManager.isSpeaking.value) {
                                Button(
                                    onClick = { speechManager.stopSpeaking() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("⏹ 停止", color = Color.White, fontSize = 11.sp)
                                }
                            }

                            // 2. STT Input Test Button
                            Button(
                                onClick = {
                                    isMicTesting = !isMicTesting
                                    if (!isMicTesting) {
                                        speechManager.stopListening()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMicTesting) Color(0xFFFF9100) else Color(0xFF00C853)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (isMicTesting) "⏹️ 停止输入测试" else "🎙️ 测试声音输入 (STT)",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Live Voice Input Feedback Panel
                        if (isMicTesting) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F1218), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF00E676).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Live RMS meter
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val langTag = when (settings.voiceLanguage) {
                                        VoiceLanguage.CANTONESE -> "粤语 (yue-Hant-HK)"
                                        VoiceLanguage.CHINESE -> "普通话 (zh-CN)"
                                        VoiceLanguage.ENGLISH -> "English (en-US)"
                                    }
                                    Text("麦克风电平 [$langTag]:", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                    Text(
                                        "${(speechManager.soundLevelRms.value * 100).toInt()}%",
                                        color = Color(0xFF00E676),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(speechManager.soundLevelRms.value.coerceIn(0.02f, 1f))
                                            .fillMaxHeight()
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFFFF9100))
                                                ),
                                                RoundedCornerShape(4.dp)
                                            )
                                    )
                                }

                                // Live transcript
                                val placeholder = when (settings.voiceLanguage) {
                                    VoiceLanguage.CANTONESE -> "請對著話筒講粤語，例如: '睇牌'、'搞掂'、'新一局'、'洗牌'..."
                                    VoiceLanguage.CHINESE -> "请对着麦克风说，例如: '报牌'、'读牌'、'锁定'、'新一局'..."
                                    VoiceLanguage.ENGLISH -> "Please speak, e.g.: 'read cards', 'lock cards', 'new cards'..."
                                }
                                Text(
                                    text = speechManager.liveTranscript.value ?: placeholder,
                                    color = Color(0xFF80D8FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                // Command indicator pills
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        when (settings.voiceLanguage) {
                                            VoiceLanguage.CANTONESE -> {
                                                CommandPill("鎖定/搞掂", lastDetectedCommand == GameVoiceCommand.LOCK_CARDS)
                                                CommandPill("睇牌/報牌", lastDetectedCommand == GameVoiceCommand.READ_CARDS)
                                                CommandPill("新一局/洗牌", lastDetectedCommand == GameVoiceCommand.NEW_CARDS)
                                            }
                                            VoiceLanguage.CHINESE -> {
                                                CommandPill("锁定", lastDetectedCommand == GameVoiceCommand.LOCK_CARDS)
                                                CommandPill("报牌/读牌", lastDetectedCommand == GameVoiceCommand.READ_CARDS)
                                                CommandPill("新一局", lastDetectedCommand == GameVoiceCommand.NEW_CARDS)
                                            }
                                            VoiceLanguage.ENGLISH -> {
                                                CommandPill("lock cards", lastDetectedCommand == GameVoiceCommand.LOCK_CARDS)
                                                CommandPill("read cards", lastDetectedCommand == GameVoiceCommand.READ_CARDS)
                                                CommandPill("new cards", lastDetectedCommand == GameVoiceCommand.NEW_CARDS)
                                            }
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        when (settings.voiceLanguage) {
                                            VoiceLanguage.CANTONESE -> {
                                                CommandPill("轉模式/鋤大D/鬥地主", lastDetectedCommand in listOf(GameVoiceCommand.SWITCH_MODE, GameVoiceCommand.SWITCH_TO_CDD, GameVoiceCommand.SWITCH_TO_DDZ))
                                                CommandPill("轉身份/地主/農民", lastDetectedCommand in listOf(GameVoiceCommand.SWITCH_ROLE, GameVoiceCommand.SWITCH_TO_LANDLORD, GameVoiceCommand.SWITCH_TO_PEASANT))
                                            }
                                            VoiceLanguage.CHINESE -> {
                                                CommandPill("切模式/锄大地/斗地主", lastDetectedCommand in listOf(GameVoiceCommand.SWITCH_MODE, GameVoiceCommand.SWITCH_TO_CDD, GameVoiceCommand.SWITCH_TO_DDZ))
                                                CommandPill("切身份/地主/农民", lastDetectedCommand in listOf(GameVoiceCommand.SWITCH_ROLE, GameVoiceCommand.SWITCH_TO_LANDLORD, GameVoiceCommand.SWITCH_TO_PEASANT))
                                            }
                                            VoiceLanguage.ENGLISH -> {
                                                CommandPill("switch mode", lastDetectedCommand in listOf(GameVoiceCommand.SWITCH_MODE, GameVoiceCommand.SWITCH_TO_CDD, GameVoiceCommand.SWITCH_TO_DDZ))
                                                CommandPill("switch role", lastDetectedCommand in listOf(GameVoiceCommand.SWITCH_ROLE, GameVoiceCommand.SWITCH_TO_LANDLORD, GameVoiceCommand.SWITCH_TO_PEASANT))
                                            }
                                        }
                                    }
                                }

                                if (settings.voiceLanguage == VoiceLanguage.CANTONESE) {
                                    Text(
                                        text = "💡 粤语识别提示：支持常用粤语同音词与口语（如：'睇牌'、'搞掂'、'定牌'、'洗牌'、'新一局'、'轉模式'、'轉做地主/農民'、'鋤大D'、'鬥地主'）。若设备未离线支持粤语，请保持网络连接。",
                                        color = Color(0xFFFFD54F),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Detail Level
                Text("播报内容模式 (Detail Level):", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoiceDetailLevel.entries.forEach { level ->
                        val isSelected = settings.voiceDetailLevel == level
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF00B0FF) else Color(0xFF21242D),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSettingsChanged(settings.copy(voiceDetailLevel = level)) }
                        ) {
                            Text(
                                text = level.label,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // Speech Rate Slider
                Text(
                    "语速 (Rate): ${(settings.speechRate * 100).toInt()}%",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
                Slider(
                    value = settings.speechRate,
                    onValueChange = { onSettingsChanged(settings.copy(speechRate = it)) },
                    valueRange = 0.7f..1.4f,
                    steps = 6,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                )
            }

            // Section 3: Game Workflow Voice Instructions
            SettingsCard(title = "📋 牌局语音指令指引 (Game Voice Commands)") {
                CommandGuideRow(
                    stage = "1. 扫描阶段 (Scanning)",
                    command = "\"lock cards\" / \"锁定\"",
                    action = "锁定当前识别到的所有手牌，暂停相机变动，准备进入出牌阶段。"
                )
                CommandGuideRow(
                    stage = "2. 锁定阶段 (Locked)",
                    command = "\"read cards\" / \"读牌\"",
                    action = "播报当前手牌与最佳出牌推荐。每当手牌发生出牌变化时，可随时再次说 \"read cards\" 重新评估。"
                )
                CommandGuideRow(
                    stage = "3. 新一局 (New Round)",
                    command = "\"new cards\" / \"新一局\"",
                    action = "清空旧手牌，解除锁定，重新开启摄像头扫描下一局手牌。"
                )
                CommandGuideRow(
                    stage = "4. 模式切换 (Mode Switch)",
                    command = "\"switch mode\" / \"切换模式\" / \"锄大地\" / \"斗地主\"",
                    action = "在首页切换锄大地(13张)与斗地主玩法，也可直接说 \"锄大地\" 或 \"斗地主\" 快速选定。"
                )
                CommandGuideRow(
                    stage = "5. 身份切换 (Role Switch)",
                    command = "\"switch role\" / \"切换身份\" / \"地主\" / \"农民\"",
                    action = "在斗地主模式下切换农民(17张)与地主(20张)身份，也可直接说 \"地主\" 或 \"农民\" 快速选定。"
                )
            }

            // Section 4: Engine & Model Information
            SettingsCard(title = "⚡ 模型与硬件规格 (Engine & Hardware)") {
                SpecRow(label = "AI 模型架构", value = "YOLOv8s (Small) 54-Class")
                SpecRow(label = "推理引擎", value = "LiteRT / TFLite Float32")
                SpecRow(label = "相机分辨率", value = "1280 x 720 (Continuous AF)")
                SpecRow(label = "王牌类别支持", value = "大王 (Red Joker) + 小王 (Black Joker)")
                SpecRow(label = "平台框架", value = "Kotlin Multiplatform (Android + iOS)")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun CommandPill(name: String, isTriggered: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isTriggered) Color(0xFF00E676) else Color.White.copy(alpha = 0.08f)
    ) {
        Text(
            text = name,
            color = if (isTriggered) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = if (isTriggered) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun CommandGuideRow(stage: String, command: String, action: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B1E28), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stage, color = Color(0xFF80D8FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF2979FF).copy(alpha = 0.3f)
            ) {
                Text(
                    text = command,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(action, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF161820),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            content()
        }
    }
}

@Composable
fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Text(value, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
