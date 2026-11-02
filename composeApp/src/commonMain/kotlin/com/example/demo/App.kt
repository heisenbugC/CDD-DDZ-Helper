package com.example.demo

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo.domain.*
import com.example.demo.guidance.AimingGuidanceStateMachine
import com.example.demo.guidance.AimingState
import com.example.demo.guidance.HapticType
import com.example.demo.guidance.NormalizedBox
import com.example.demo.rules.*
import com.example.demo.services.*
import com.example.demo.ui.*

enum class AppScreen {
    HOME,
    SETTINGS
}

@Composable
fun App() {
    MaterialTheme {
        CddHelperApp()
    }
}

@Composable
fun CddHelperApp() {
    val settingsStorage = rememberSettingsStorage()
    var settings by remember { mutableStateOf(settingsStorage.loadSettings()) }
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    val speechManager = rememberSpeechManager()

    // Persist settings whenever changed
    val handleSettingsChanged: (AppSettings) -> Unit = { newSettings ->
        settings = newSettings
        settingsStorage.saveSettings(newSettings)
    }

    // Automatic persistence effect ensuring state is never lost
    LaunchedEffect(settings) {
        settingsStorage.saveSettings(settings)
    }

    // Sync speech recognition language with voice language setting immediately at top level
    LaunchedEffect(settings.voiceLanguage) {
        speechManager.setRecognitionLanguage(settings.voiceLanguage.code)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(
            settings = settings,
            onSettingsChanged = handleSettingsChanged,
            speechManager = speechManager,
            onOpenSettings = { currentScreen = AppScreen.SETTINGS },
            isPaused = currentScreen == AppScreen.SETTINGS
        )

        if (currentScreen == AppScreen.SETTINGS) {
            SettingsScreen(
                settings = settings,
                onSettingsChanged = handleSettingsChanged,
                speechManager = speechManager,
                onBack = { currentScreen = AppScreen.HOME }
            )
        }
    }
}

@Composable
fun HomeScreen(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    speechManager: SpeechManager,
    onOpenSettings: () -> Unit,
    isPaused: Boolean = false
) {
    // Detection & card collections
    var detectedCards by remember { mutableStateOf(emptyList<DdzCard>()) }
    var detectedCddCards by remember { mutableStateOf(emptyList<Card>()) }
    var detectedDdzHands by remember { mutableStateOf(emptyList<DdzHand>()) }
    var detectedCddHands by remember { mutableStateOf(emptyList<CddHand>()) }
    var rawDetections by remember { mutableStateOf(emptyList<String>()) }
    var activeBoxes by remember { mutableStateOf(emptyList<NormalizedBox>()) }
    var cddJokerWarning by remember { mutableStateOf(false) }
    var lastSeenDetections by remember { mutableStateOf(emptyList<String>()) }

    // Core game state: locked vs scanning
    var isLocked by remember { mutableStateOf(false) }
    var showHandsExplorer by remember { mutableStateOf(false) }

    // Non-visual Aiming Guidance State Machine
    val guidanceStateMachine = remember { AimingGuidanceStateMachine() }
    val aimingState by guidanceStateMachine.currentState.collectAsState()
    val feedbackSignal by guidanceStateMachine.feedbackSignal.collectAsState()
    val centeringScore by guidanceStateMachine.centeringScore.collectAsState()

    // Platform Audio & Haptic engines
    val audioToneGenerator = rememberAudioToneGenerator()
    val hapticEngine = rememberHapticEngine()

    // Up-to-date state holders to prevent stale closure captures in background listeners
    val currentSettings by rememberUpdatedState(settings)
    val currentDetectedCards by rememberUpdatedState(detectedCards)
    val currentDetectedCddCards by rememberUpdatedState(detectedCddCards)
    val currentDetectedDdzHands by rememberUpdatedState(detectedDdzHands)
    val currentDetectedCddHands by rememberUpdatedState(detectedCddHands)
    val currentIsLocked by rememberUpdatedState(isLocked)

    // Real-time Audio & Haptic guidance loop
    LaunchedEffect(feedbackSignal, isLocked, isPaused, settings.audioFeedbackEnabled, settings.hapticFeedbackEnabled) {
        if (!isLocked && !isPaused) {
            if (settings.audioFeedbackEnabled) {
                audioToneGenerator?.updateSignal(feedbackSignal)
                audioToneGenerator?.start()
            } else {
                audioToneGenerator?.stop()
            }
            if (settings.hapticFeedbackEnabled && feedbackSignal.hapticFeedbackType != HapticType.NONE) {
                hapticEngine?.playHaptic(feedbackSignal.hapticFeedbackType)
            }
        } else {
            audioToneGenerator?.stop()
        }
    }

    // TTS Workflow Trigger Function
    fun triggerTtsWorkflow() {
        val s = currentSettings
        val speechText = if (s.gameMode == GameMode.DOU_DI_ZHU) {
            val ddzCards = currentDetectedCards
            val ddzHands = currentDetectedDdzHands
            CardSpeechFormatter.formatDdzSpeech(
                cards = ddzCards,
                hands = ddzHands,
                language = s.voiceLanguage.code,
                detailed = s.voiceDetailLevel == VoiceDetailLevel.DETAILED
            )
        } else {
            val cddCards = currentDetectedCddCards
            val cddHands = currentDetectedCddHands
            CardSpeechFormatter.formatCddSpeech(
                cards = cddCards,
                hands = cddHands,
                language = s.voiceLanguage.code,
                detailed = s.voiceDetailLevel == VoiceDetailLevel.DETAILED,
                variation = s.cddVariation
            )
        }

        speechManager.speak(
            text = speechText,
            language = s.voiceLanguage.code,
            rate = s.speechRate,
            pitch = s.speechPitch
        )
    }

    // Unified Immediate Card Reporting Function
    fun reportCardsImmediately() {
        if (!isLocked) {
            isLocked = true
            guidanceStateMachine.triggerManualLock()
            audioToneGenerator?.stop()
        }
        triggerTtsWorkflow()
    }

    // Lock cards helper
    fun lockCurrentCards() {
        if (!isLocked) {
            isLocked = true
            guidanceStateMachine.triggerManualLock()
            audioToneGenerator?.stop()

            val s = currentSettings
            val count = if (s.gameMode == GameMode.DOU_DI_ZHU) currentDetectedCards.size else currentDetectedCddCards.size
            val lockMsg = if (s.voiceLanguage == VoiceLanguage.ENGLISH) {
                "Cards locked, $count cards total. You can now put them down. Say 'read cards' anytime to evaluate."
            } else if (s.voiceLanguage == VoiceLanguage.CANTONESE) {
                "手牌已鎖定，共 $count 張。而家可以隨時講 '睇牌' 或按音量鍵聽取牌型。"
            } else {
                "手牌已锁定，共 $count 张。现在可以随时说 '报牌' 或按音量键听取牌型。"
            }
            speechManager.speak(
                text = lockMsg,
                language = s.voiceLanguage.code,
                rate = s.speechRate,
                pitch = s.speechPitch
            )
        }
    }

    // Unlock and start new round helper
    fun startNewRound() {
        isLocked = false
        activeBoxes = emptyList()
        cddJokerWarning = false
        detectedCards = emptyList()
        detectedCddCards = emptyList()
        detectedDdzHands = emptyList()
        detectedCddHands = emptyList()
        guidanceStateMachine.unlockForNewRound()

        val s = currentSettings
        val newRoundMsg = if (s.voiceLanguage == VoiceLanguage.ENGLISH) {
            "New round started. Cards unlocked for scanning."
        } else if (s.voiceLanguage == VoiceLanguage.CANTONESE) {
            "新一局開始。手牌已解鎖，請將新卡牌對準鏡頭掃描。"
        } else {
            "新一局开始。手牌已解锁，请将新卡牌对准摄像头扫描。"
        }
        speechManager.speak(
            text = newRoundMsg,
            language = s.voiceLanguage.code,
            rate = s.speechRate,
            pitch = s.speechPitch
        )
    }

    val currentReportCardsImmediately by rememberUpdatedState(::reportCardsImmediately)
    val currentStartNewRound by rememberUpdatedState(::startNewRound)

    // Automatic locking when 1.0s stability is satisfied
    LaunchedEffect(aimingState) {
        if (aimingState == AimingState.LOCKED && !currentIsLocked) {
            val count = if (currentSettings.gameMode == GameMode.DOU_DI_ZHU) currentDetectedCards.size else currentDetectedCddCards.size
            if (count > 0) {
                currentReportCardsImmediately()
            }
        }
    }

    // Hardware Volume Key Interception Listener
    LaunchedEffect(isPaused) {
        if (!isPaused) {
            HardwareKeyDispatcher.keyEvents.collect { event ->
                when (event) {
                    HardwareKeyEvent.VOLUME_DOWN_TRIGGER -> {
                        if (!currentIsLocked) {
                            currentReportCardsImmediately()
                        } else {
                            currentStartNewRound()
                        }
                    }
                    HardwareKeyEvent.VOLUME_UP_TRIGGER -> {
                        currentReportCardsImmediately()
                    }
                }
            }
        }
    }

    // Voice Command State Machine Listener (Updated with language and live state bindings)
    LaunchedEffect(settings.voiceTriggerEnabled, settings.voiceLanguage, speechManager.hasAudioPermission.value, isPaused) {
        if (!isPaused && settings.voiceTriggerEnabled && speechManager.hasAudioPermission.value) {
            speechManager.startListening { command ->
                when (command) {
                    GameVoiceCommand.LOCK_CARDS -> {
                        currentReportCardsImmediately()
                    }
                    GameVoiceCommand.READ_CARDS -> {
                        currentReportCardsImmediately()
                    }
                    GameVoiceCommand.NEW_CARDS -> {
                        currentStartNewRound()
                    }
                    GameVoiceCommand.SWITCH_MODE -> {
                        val nextMode = if (currentSettings.gameMode == GameMode.DOU_DI_ZHU) GameMode.CHO_DAI_DEE else GameMode.DOU_DI_ZHU
                        onSettingsChanged(currentSettings.copy(gameMode = nextMode))
                    }
                    GameVoiceCommand.SWITCH_TO_CDD -> {
                        if (currentSettings.gameMode != GameMode.CHO_DAI_DEE) {
                            onSettingsChanged(currentSettings.copy(gameMode = GameMode.CHO_DAI_DEE))
                        } else {
                            val alreadyMsg = when (currentSettings.voiceLanguage) {
                                VoiceLanguage.ENGLISH -> "Already in Big Two mode, please scan 13 cards."
                                VoiceLanguage.CANTONESE -> "而家已經係鋤大D模式，請掃描13張手牌。"
                                VoiceLanguage.CHINESE -> "当前已是锄大地模式，请扫描13张手牌。"
                            }
                            speechManager.speak(alreadyMsg, currentSettings.voiceLanguage.code, currentSettings.speechRate, currentSettings.speechPitch)
                        }
                    }
                    GameVoiceCommand.SWITCH_TO_DDZ -> {
                        if (currentSettings.gameMode != GameMode.DOU_DI_ZHU) {
                            onSettingsChanged(currentSettings.copy(gameMode = GameMode.DOU_DI_ZHU))
                        } else {
                            val targetCount = currentSettings.ddzRole.cardsCount
                            val alreadyMsg = when (currentSettings.voiceLanguage) {
                                VoiceLanguage.ENGLISH -> "Already in Dou Di Zhu mode, please scan $targetCount cards."
                                VoiceLanguage.CANTONESE -> "而家已經係鬥地主模式，請掃描$targetCount 張手牌。"
                                VoiceLanguage.CHINESE -> "当前已是斗地主模式，请扫描$targetCount 张手牌。"
                            }
                            speechManager.speak(alreadyMsg, currentSettings.voiceLanguage.code, currentSettings.speechRate, currentSettings.speechPitch)
                        }
                    }
                    GameVoiceCommand.SWITCH_ROLE -> {
                        if (currentSettings.gameMode == GameMode.DOU_DI_ZHU) {
                            val nextRole = if (currentSettings.ddzRole == DdzRole.LANDLORD) DdzRole.PEASANT else DdzRole.LANDLORD
                            onSettingsChanged(currentSettings.copy(ddzRole = nextRole))
                        } else {
                            val notDdzMsg = when (currentSettings.voiceLanguage) {
                                VoiceLanguage.ENGLISH -> "Role toggle is only for Dou Di Zhu mode."
                                VoiceLanguage.CANTONESE -> "身份切換只適用於鬥地主模式。"
                                VoiceLanguage.CHINESE -> "身份切换只适用于斗地主模式。"
                            }
                            speechManager.speak(notDdzMsg, currentSettings.voiceLanguage.code, currentSettings.speechRate, currentSettings.speechPitch)
                        }
                    }
                    GameVoiceCommand.SWITCH_TO_LANDLORD -> {
                        if (currentSettings.gameMode != GameMode.DOU_DI_ZHU) {
                            onSettingsChanged(currentSettings.copy(gameMode = GameMode.DOU_DI_ZHU, ddzRole = DdzRole.LANDLORD))
                        } else if (currentSettings.ddzRole != DdzRole.LANDLORD) {
                            onSettingsChanged(currentSettings.copy(ddzRole = DdzRole.LANDLORD))
                        } else {
                            val alreadyRoleMsg = when (currentSettings.voiceLanguage) {
                                VoiceLanguage.ENGLISH -> "Already in Landlord role, please scan 20 cards."
                                VoiceLanguage.CANTONESE -> "而家已經係地主身份，請掃描20張手牌。"
                                VoiceLanguage.CHINESE -> "当前已是地主身份，请扫描20张手牌。"
                            }
                            speechManager.speak(alreadyRoleMsg, currentSettings.voiceLanguage.code, currentSettings.speechRate, currentSettings.speechPitch)
                        }
                    }
                    GameVoiceCommand.SWITCH_TO_PEASANT -> {
                        if (currentSettings.gameMode != GameMode.DOU_DI_ZHU) {
                            onSettingsChanged(currentSettings.copy(gameMode = GameMode.DOU_DI_ZHU, ddzRole = DdzRole.PEASANT))
                        } else if (currentSettings.ddzRole != DdzRole.PEASANT) {
                            onSettingsChanged(currentSettings.copy(ddzRole = DdzRole.PEASANT))
                        } else {
                            val alreadyRoleMsg = when (currentSettings.voiceLanguage) {
                                VoiceLanguage.ENGLISH -> "Already in Peasant role, please scan 17 cards."
                                VoiceLanguage.CANTONESE -> "而家已經係農民身份，請掃描17張手牌。"
                                VoiceLanguage.CHINESE -> "当前已是农民身份，请扫描17张手牌。"
                            }
                            speechManager.speak(alreadyRoleMsg, currentSettings.voiceLanguage.code, currentSettings.speechRate, currentSettings.speechPitch)
                        }
                    }
                }
            }
        } else {
            speechManager.stopListening()
        }
    }

    var previousMode by remember { mutableStateOf<GameMode?>(null) }
    var previousRole by remember { mutableStateOf<DdzRole?>(null) }

    LaunchedEffect(settings.gameMode) {
        if (previousMode != null && previousMode != settings.gameMode) {
            previousRole = settings.ddzRole // Suppress duplicate role announcement on mode switch
            // 1. Terminate previous game workflow
            isLocked = false
            guidanceStateMachine.unlockForNewRound()
            audioToneGenerator?.stop()

            // 2. Clear old mode collections and immediately re-evaluate raw detections if present
            cddJokerWarning = false
            val candidateDetections = if (rawDetections.isNotEmpty()) rawDetections else lastSeenDetections
            if (settings.gameMode == GameMode.CHO_DAI_DEE) {
                detectedCards = emptyList()
                detectedDdzHands = emptyList()
                val parsed = candidateDetections.mapNotNull { CardMapping.parseCddLabel(it) }
                val updated = parsed.distinct().take(13)
                detectedCddCards = updated
                detectedCddHands = HandFinder.findAllHands(updated)
            } else {
                detectedCddCards = emptyList()
                detectedCddHands = emptyList()
                val parsed = candidateDetections.mapNotNull { CardMapping.parseDdzLabel(it) }
                val maxCards = if (settings.ddzRole == DdzRole.PEASANT) 17 else 20
                val updated = parsed.distinct().take(maxCards)
                detectedCards = updated
                detectedDdzHands = DdzHandFinder.findAllHands(updated)
            }

            // 3. Announce new game mode in active voice language
            val targetCount = if (settings.gameMode == GameMode.DOU_DI_ZHU) settings.ddzRole.cardsCount else 13
            val switchMsg = when (settings.voiceLanguage) {
                VoiceLanguage.ENGLISH -> if (settings.gameMode == GameMode.CHO_DAI_DEE) {
                    "Switched to Big Two mode. Please scan 13 cards."
                } else {
                    "Switched to Dou Di Zhu mode. Please scan $targetCount cards."
                }
                VoiceLanguage.CANTONESE -> if (settings.gameMode == GameMode.CHO_DAI_DEE) {
                    "已切換到鋤大D模式，請掃描13張手牌。"
                } else {
                    "已切換到鬥地主模式，請掃描$targetCount 張手牌。"
                }
                VoiceLanguage.CHINESE -> if (settings.gameMode == GameMode.CHO_DAI_DEE) {
                    "已切换到锄大地模式，请扫描13张手牌。"
                } else {
                    "已切换到斗地主模式，请扫描$targetCount 张手牌。"
                }
            }

            speechManager.speak(
                text = switchMsg,
                language = settings.voiceLanguage.code,
                rate = settings.speechRate,
                pitch = settings.speechPitch
            )
        }
        previousMode = settings.gameMode
    }

    LaunchedEffect(settings.ddzRole) {
        if (previousRole != null && previousRole != settings.ddzRole && settings.gameMode == GameMode.DOU_DI_ZHU && previousMode == settings.gameMode) {
            val maxCards = settings.ddzRole.cardsCount
            if (detectedCards.size > maxCards) {
                detectedCards = detectedCards.take(maxCards)
                detectedDdzHands = DdzHandFinder.findAllHands(detectedCards)
            }

            val roleMsg = when (settings.voiceLanguage) {
                VoiceLanguage.ENGLISH -> if (settings.ddzRole == DdzRole.LANDLORD) {
                    "Switched to Landlord. Please scan 20 cards."
                } else {
                    "Switched to Peasant. Please scan 17 cards."
                }
                VoiceLanguage.CANTONESE -> if (settings.ddzRole == DdzRole.LANDLORD) {
                    "已切換為地主身份，請掃描20張手牌。"
                } else {
                    "已切換為農民身份，請掃描17張手牌。"
                }
                VoiceLanguage.CHINESE -> if (settings.ddzRole == DdzRole.LANDLORD) {
                    "已切换为地主身份，请扫描20张手牌。"
                } else {
                    "已切换为农民身份，请扫描17张手牌。"
                }
            }

            speechManager.speak(
                text = roleMsg,
                language = settings.voiceLanguage.code,
                rate = settings.speechRate,
                pitch = settings.speechPitch
            )
        }
        previousRole = settings.ddzRole
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.stopListening()
            audioToneGenerator?.stop()
        }
    }

    // Full Screen Viewport
    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Live Camera Feed with continuous LiteRT inference
        CameraView(
            modifier = Modifier.fillMaxSize(),
            onBoxesDetected = { boxes ->
                if (!isLocked && !isPaused) {
                    activeBoxes = boxes
                    guidanceStateMachine.onNewFrameDetections(boxes, System.currentTimeMillis())
                } else {
                    activeBoxes = emptyList()
                }
            },
            onCardsDetected = { stringCards ->
                if (!isLocked && !isPaused) {
                    rawDetections = stringCards
                    if (stringCards.isNotEmpty()) {
                        lastSeenDetections = stringCards
                    }
                    if (settings.gameMode == GameMode.DOU_DI_ZHU) {
                        cddJokerWarning = false
                        val parsed = stringCards.mapNotNull { CardMapping.parseDdzLabel(it) }
                        if (parsed.isNotEmpty()) {
                            val maxCards = if (settings.ddzRole == DdzRole.PEASANT) 17 else 20
                            val updated = (detectedCards + parsed).distinct().take(maxCards)
                            if (updated != detectedCards) {
                                detectedCards = updated
                                detectedDdzHands = DdzHandFinder.findAllHands(updated)
                            }
                        }
                    } else {
                        val parsed = stringCards.mapNotNull { CardMapping.parseCddLabel(it) }
                        if (parsed.isNotEmpty()) {
                            val updated = (detectedCddCards + parsed).distinct().take(13)
                            if (updated != detectedCddCards) {
                                detectedCddCards = updated
                                detectedCddHands = HandFinder.findAllHands(updated)
                            }
                        }
                        cddJokerWarning = stringCards.any { it.contains("joker") || it == "52" || it == "53" }
                    }
                }
            }
        )

        // Layer 1.5: Real-time Camera HUD Bounding Box Overlay
        if (activeBoxes.isNotEmpty() && !isLocked && !isPaused) {
            CameraDetectionOverlay(
                boxes = activeBoxes,
                gameMode = settings.gameMode,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Layer 2: Top Floating Island & Status Bar (NowInAndroid Material 3)
        NowInAndroidIslandBar(
            settings = settings,
            onSettingsChanged = onSettingsChanged,
            aimingState = aimingState,
            centeringScore = centeringScore,
            isLocked = isLocked,
            cardCount = if (settings.gameMode == GameMode.DOU_DI_ZHU) detectedCards.size else detectedCddCards.size,
            soundLevel = speechManager.soundLevelRms.value,
            isListening = speechManager.isListening.value,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )

        // Layer 3: Bottom Control Panel (NowInAndroid Material 3)
        val hasCards = if (settings.gameMode == GameMode.DOU_DI_ZHU) detectedCards.isNotEmpty() else detectedCddCards.isNotEmpty()
        val totalCount = if (settings.gameMode == GameMode.DOU_DI_ZHU) detectedCards.size else detectedCddCards.size
        val targetCount = if (settings.gameMode == GameMode.DOU_DI_ZHU) settings.ddzRole.cardsCount else 13

        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Color(0xEE12151D),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Row of Bottom Panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isLocked) Color(0xFFFF9100).copy(alpha = 0.22f) else Color(0xFF2979FF).copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, if (isLocked) Color(0xFFFF9100) else Color(0xFF448AFF))
                    ) {
                        Text(
                            text = if (isLocked) "🔒 手牌: $totalCount 张 (已锁定)" else "📸 扫描中: $totalCount / $targetCount 张",
                            color = if (isLocked) Color(0xFFFFB74D) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Secondary actions: Hands Sheet & Reset
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF7C4DFF).copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color(0xFFB388FF)),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .clickable(role = Role.Button) { showHandsExplorer = true }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "打开全部牌型库"
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("🃏 牌型库", color = Color(0xFFD1C4E9), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFD50000).copy(alpha = 0.20f),
                            border = BorderStroke(1.dp, Color(0xFFFF5252)),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .clickable(role = Role.Button) {
                                    startNewRound()
                                }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "清空手牌重置"
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("重置", color = Color(0xFFFF8A80), fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Detected Cards in Balanced Rows with AccessibleCardBadge
                if (hasCards) {
                    if (settings.gameMode == GameMode.DOU_DI_ZHU) {
                        val sortedDdz = remember(detectedCards) { detectedCards.sorted() }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(sortedDdz, key = { it.toString() }) { card ->
                                AccessibleDdzCardBadge(
                                    card = card,
                                    isLocked = isLocked,
                                    onRemove = {
                                        if (isLocked) {
                                            val updated = detectedCards.filter { it != card }
                                            detectedCards = updated
                                            detectedDdzHands = DdzHandFinder.findAllHands(updated)
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        val sortedCdd = remember(detectedCddCards) { detectedCddCards.sorted() }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(sortedCdd, key = { it.toString() }) { card ->
                                AccessibleCddCardBadge(
                                    card = card,
                                    isLocked = isLocked,
                                    onRemove = {
                                        if (isLocked) {
                                            val updated = detectedCddCards.filter { it != card }
                                            detectedCddCards = updated
                                            detectedCddHands = HandFinder.findAllHands(updated)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // CDD Joker Warning Banner with Quick Switch Action
                if (cddJokerWarning && settings.gameMode == GameMode.CHO_DAI_DEE) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF6D00).copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color(0xFFFF9100)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "⚠️ 锄大地不含王牌，请切换到斗地主",
                                color = Color(0xFFFFB74D),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF2979FF),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 32.dp)
                                    .clickable(role = Role.Button) {
                                        onSettingsChanged(settings.copy(gameMode = GameMode.DOU_DI_ZHU))
                                        cddJokerWarning = false
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("切到斗地主", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Best Hand Recommendation Banner
                val bestHandText = if (settings.gameMode == GameMode.DOU_DI_ZHU) {
                    DdzHandFinder.getBestTacticalHand(detectedDdzHands)?.let { CardSpeechFormatter.describeDdzHandZh(it) }
                } else {
                    HandFinder.getBestTacticalHand(detectedCddHands, settings.cddVariation)?.let { CardSpeechFormatter.describeCddHandZh(it) }
                }

                if (bestHandText != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E222D),
                        border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("💡 推荐最大出牌:", color = Color(0xFFFFD54F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = bestHandText,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Primary Prominent Accessible Action Button (>= 56dp height)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF2979FF),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable(role = Role.Button) {
                            reportCardsImmediately()
                        }
                        .semantics {
                            role = Role.Button
                            contentDescription = "立即报牌，当前识别到 $totalCount 张手牌。也可以按音量键或说 报牌 立即报牌。"
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "🔊 立即报牌 (按音量键或说 '报牌' 亦可)",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Accessible Hands Explorer Modal Sheet
        if (showHandsExplorer) {
            AccessibleHandsExplorer(
                gameMode = settings.gameMode,
                ddzHands = detectedDdzHands,
                cddHands = detectedCddHands,
                onSpeakHand = { description ->
                    speechManager.speak(
                        text = description,
                        language = settings.voiceLanguage.code,
                        rate = settings.speechRate,
                        pitch = settings.speechPitch
                    )
                },
                onClose = { showHandsExplorer = false },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
