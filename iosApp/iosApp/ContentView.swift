import SwiftUI
import AVFoundation

/**
 * Main iOS ContentView.
 * Implements Apple Native design patterns (Liquid Glass, SF Symbols, Inset Grouped Navigation)
 * with 100% functional parity with Android:
 * - 54-class card detection & Layer-2 HSV Chroma Joker disambiguation
 * - Continuous hands-free Cantonese/Mandarin/English voice commands (including mode/role switching)
 * - Apple Native TTS engine (AVSpeechSynthesizer) with Google TTS fallback
 * - Hardware volume button observation (Vol Down = Lock/Reset, Vol Up = Readout)
 * - Non-visual audio/haptic aiming guidance
 * - Frame-to-frame card accumulation with instant hand evaluation
 */
public struct ContentView: View {
    @ObservedObject private var settings = AppSettings.shared
    @StateObject private var modelHandler = CardModelLiteRT()
    @StateObject private var guidanceEngine = AudioHapticGuidance()
    @ObservedObject private var speechService = SpeechRecognitionService.shared
    @ObservedObject private var speechEngine = NativeSpeechEngine.shared
    
    // Core game state
    @State private var isLocked: Bool = false
    @State private var accumulatedCards: [PlayingCard] = []
    @State private var detectedDdzHands: [DdzHand] = []
    @State private var detectedCddHands: [CddHand] = []
    @State private var cddJokerWarning: Bool = false
    @State private var showSettings: Bool = false
    @State private var showHandsExplorer: Bool = false
    
    // Volume button observation
    @State private var initialVolume: Float = AVAudioSession.sharedInstance().outputVolume
    @State private var volumeObserver: NSKeyValueObservation?
    @State private var isViewActive: Bool = false
    
    private var targetCount: Int {
        if settings.gameMode == .douDiZhu {
            return settings.ddzRole == .landlord ? 20 : 17
        } else {
            return 13
        }
    }
    
    private var bestHandText: String? {
        if settings.gameMode == .douDiZhu {
            if let best = DdzHandFinder.getBestTacticalHand(hands: detectedDdzHands) {
                return CardSpeechFormatter.describeDdzHand(best, language: settings.voiceLanguage)
            }
        } else {
            if let best = CddHandFinder.getBestTacticalHand(hands: detectedCddHands, variation: settings.cddVariation) {
                return CardSpeechFormatter.describeCddHand(best, language: settings.voiceLanguage)
            }
        }
        return nil
    }
    
    public var body: some View {
        ZStack {
            // Layer 1: Fullscreen Live Camera Preview
            CameraView { pixelBuffer in
                if !isLocked {
                    modelHandler.processFrame(pixelBuffer)
                    
                    // Update guidance centering score
                    DispatchQueue.main.async {
                        if settings.enableAimingGuidance {
                            guidanceEngine.updateCentering(score: modelHandler.centeringScore)
                        }
                    }
                }
            }
            .edgesIgnoringSafeArea(.all)
            
            // Layer 2: Detection Bounding Box Overlays
            if !isLocked && !modelHandler.detectedBoxes.isEmpty {
                detectionOverlayCanvas
                    .edgesIgnoringSafeArea(.all)
            }
            
            // Layer 3: Native Apple UI Floating Interface
            VStack {
                // Top Floating Glass Island Bar
                topFloatingIslandBar
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                
                Spacer()
                
                // Bottom Native Control Dock
                bottomFloatingDock
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
            }
        }
        .onAppear {
            setupInitialState()
        }
        .onDisappear {
            teardownState()
        }
        .onReceive(modelHandler.$detectedCards) { newCards in
            handleIncomingCards(newCards)
        }
        .onChange(of: settings.cddVariation) { _ in
            reevaluateHands()
        }
        .onChange(of: settings.gameMode) { _ in
            handleModeChanged()
        }
        .onChange(of: settings.ddzRole) { _ in
            handleRoleChanged()
        }
        .onChange(of: settings.enableVoiceCommands) { enabled in
            if enabled {
                speechService.startListening(language: settings.voiceLanguage, playEarcon: true)
            } else {
                speechService.stopListening(playEarcon: true)
            }
        }
        .onChange(of: settings.voiceLanguage) { newLang in
            if settings.enableVoiceCommands && speechService.isListening {
                speechService.startListening(language: newLang, playEarcon: false)
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsView()
        }
        .sheet(isPresented: $showHandsExplorer) {
            HandsExplorerView(
                cards: accumulatedCards,
                gameMode: settings.gameMode,
                ddzRole: settings.ddzRole,
                cddVariation: settings.cddVariation,
                voiceLanguage: settings.voiceLanguage
            ) { selectedHandDesc in
                speechEngine.speak(
                    text: selectedHandDesc,
                    language: settings.voiceLanguage,
                    rate: settings.speechRate,
                    pitch: settings.speechPitch,
                    volume: settings.speechVolume
                )
            }
        }
    }
    
    // MARK: - Top Floating Glass Island Bar
    private var topFloatingIslandBar: some View {
        HStack(spacing: 8) {
            // Game Mode Switch Button
            Button(action: toggleGameMode) {
                HStack(spacing: 5) {
                    Image(systemName: settings.gameMode == .douDiZhu ? "flame.fill" : "suit.spade.fill")
                        .font(.system(size: 13, weight: .bold))
                    Text(settings.gameMode.title)
                        .font(.system(size: 13, weight: .bold))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(Color.blue.opacity(0.85))
                .clipShape(Capsule())
            }
            .accessibilityLabel("切换游戏模式，当前为\(settings.gameMode.title)")
            
            // DDZ Role Switch Button (if DDZ)
            if settings.gameMode == .douDiZhu {
                Button(action: toggleDdzRole) {
                    HStack(spacing: 4) {
                        Image(systemName: settings.ddzRole == .landlord ? "crown.fill" : "person.2.fill")
                            .font(.system(size: 12, weight: .bold))
                        Text(settings.ddzRole == .landlord ? "地主(20)" : "农民(17)")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(settings.ddzRole == .landlord ? Color.orange.opacity(0.85) : Color.indigo.opacity(0.85))
                    .clipShape(Capsule())
                }
                .accessibilityLabel("切换身份，当前为\(settings.ddzRole.title)")
            }
            
            Spacer()
            
            // Centering Quality Indicator
            HStack(spacing: 5) {
                Image(systemName: "target")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(centeringColor)
                Text(isLocked ? "已锁定" : "\(Int(modelHandler.centeringScore * 100))%")
                    .font(.system(size: 12, weight: .heavy))
                    .foregroundColor(centeringColor)
                    .monospacedDigit()
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(Color.black.opacity(0.6))
            .clipShape(Capsule())
            .accessibilityLabel("瞄准居中得分: \(Int(modelHandler.centeringScore * 100))%")
            
            // Mic Listening Indicator
            if settings.enableVoiceCommands {
                HStack(spacing: 4) {
                    Image(systemName: speechService.isListening ? (speechService.isMutedForTTS ? "speaker.wave.2.fill" : "waveform") : "mic.slash")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(speechService.isListening ? (speechService.isMutedForTTS ? .orange : .green) : .gray)
                }
                .frame(width: 30, height: 30)
                .background(Color.black.opacity(0.6))
                .clipShape(Circle())
                .accessibilityLabel(speechService.isListening ? (speechService.isMutedForTTS ? "TTS播报中，临时静音收音" : "语音指令监听中") : "语音监听已停止")
            }
            
            // Settings Button
            Button(action: { showSettings = true }) {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 30, height: 30)
                    .background(Color.black.opacity(0.6))
                    .clipShape(Circle())
            }
            .accessibilityLabel("打开设置")
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(.ultraThinMaterial)
        .clipShape(Capsule())
        .overlay(Capsule().stroke(Color.white.opacity(0.2), lineWidth: 1))
        .shadow(color: Color.black.opacity(0.35), radius: 8, x: 0, y: 4)
    }
    
    // MARK: - Bottom Floating Control Dock
    private var bottomFloatingDock: some View {
        VStack(spacing: 10) {
            // Header Row: Card Count & Action Chips
            HStack(spacing: 8) {
                // 1. Card Count Badge
                HStack(spacing: 6) {
                    Image(systemName: isLocked ? "lock.fill" : "camera.viewfinder")
                        .font(.system(size: 12, weight: .bold))
                    Text(isLocked ? "🔒 手牌: \(accumulatedCards.count) 张 (已锁定)" : "📸 扫描中: \(accumulatedCards.count) / \(targetCount) 张")
                        .font(.system(size: 13, weight: .bold))
                }
                .foregroundColor(isLocked ? Color(red: 1.0, green: 0.72, blue: 0.3) : .white)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(isLocked ? Color.orange.opacity(0.25) : Color.blue.opacity(0.25))
                .clipShape(Capsule())
                .overlay(Capsule().stroke(isLocked ? Color.orange : Color.blue.opacity(0.5), lineWidth: 1))
                
                Spacer()
                
                // Hands Explorer Sheet Button
                Button(action: { showHandsExplorer = true }) {
                    HStack(spacing: 4) {
                        Image(systemName: "rectangle.stack.fill")
                            .font(.system(size: 11, weight: .bold))
                        Text("牌型库")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundColor(Color(red: 0.85, green: 0.8, blue: 0.98))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.purple.opacity(0.35))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.purple.opacity(0.6), lineWidth: 1))
                }
                .accessibilityLabel("查看所有可用牌型")
                
                // 6. Lock / Unlock Toggle Button
                Button(action: toggleLock) {
                    HStack(spacing: 4) {
                        Image(systemName: isLocked ? "lock.open.fill" : "lock.fill")
                            .font(.system(size: 11, weight: .bold))
                        Text(isLocked ? "解锁" : "锁定")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(isLocked ? Color.blue.opacity(0.85) : Color.orange.opacity(0.85))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .accessibilityLabel(isLocked ? "解锁手牌继续扫描" : "锁定手牌")
                
                // 5. Reset Button
                Button(action: resetCards) {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.counterclockwise")
                            .font(.system(size: 11, weight: .bold))
                        Text("重置")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundColor(Color(red: 1.0, green: 0.5, blue: 0.5))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.red.opacity(0.2))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.red.opacity(0.4), lineWidth: 1))
                }
                .accessibilityLabel("清空手牌重置")
            }
            
            // 4. Cards Horizontal Shelf
            if !accumulatedCards.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(accumulatedCards, id: \.id) { card in
                            AccessibleCardView(
                                suit: card.suit.rawValue,
                                rank: card.rank.label,
                                isRed: card.isRed,
                                isLocked: isLocked,
                                onRemove: {
                                    if isLocked {
                                        removeCard(card)
                                    }
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 4)
                }
            } else {
                HStack {
                    Image(systemName: "hand.raised.slash.fill")
                        .foregroundColor(.secondary)
                    Text("请将手牌平铺并对准摄像头")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
                .frame(height: 56)
            }
            
            // 2. CDD Joker Warning Banner with Quick Switch Action
            if cddJokerWarning && settings.gameMode == .choDaiDee {
                HStack {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(Color(red: 1.0, green: 0.72, blue: 0.3))
                        .font(.system(size: 12))
                    Text("⚠️ 锄大地不含王牌，请切换到斗地主")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(red: 1.0, green: 0.72, blue: 0.3))
                    
                    Spacer()
                    
                    Button(action: {
                        settings.gameMode = .douDiZhu
                        cddJokerWarning = false
                        handleModeChanged()
                    }) {
                        Text("切到斗地主")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.blue)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.orange.opacity(0.22))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.orange, lineWidth: 1))
            }
            
            // 3. Best Hand Recommendation Banner
            if let bestText = bestHandText {
                HStack(spacing: 6) {
                    Text("💡 推荐最大出牌:")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.31))
                    
                    Text(bestText)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                    
                    Spacer()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color(red: 0.12, green: 0.13, blue: 0.18))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.yellow.opacity(0.4), lineWidth: 1))
            }
            
            // Live Voice Transcript Debug Band & TTS Status
            // Shows partial speech-to-text in real time or TTS muted notice
            if settings.enableVoiceCommands && (speechService.isMutedForTTS || !speechService.currentTranscript.isEmpty) {
                HStack(spacing: 6) {
                    if speechService.isMutedForTTS {
                        Image(systemName: "speaker.wave.2.fill")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.orange)
                        Text(settings.voiceLanguage == .cantonese ? "正在播報中..." : (settings.voiceLanguage == .english ? "Speaking..." : "正在播报中..."))
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.orange)
                    } else {
                        Image(systemName: "waveform.badge.mic")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.green)
                        Text(speechService.currentTranscript)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .truncationMode(.head)
                    }
                    Spacer()
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(speechService.isMutedForTTS ? Color.orange.opacity(0.18) : Color.green.opacity(0.18))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke((speechService.isMutedForTTS ? Color.orange : Color.green).opacity(0.4), lineWidth: 1))
            }

            // Primary Call-to-Action Bar (>= 56pt height, large touch target)
            Button(action: primaryActionTriggered) {
                HStack(spacing: 8) {
                    Image(systemName: isLocked ? "speaker.wave.3.fill" : "lock.fill")
                        .font(.system(size: 18, weight: .heavy))
                    Text(isLocked ? "朗读报牌 (按音量加键亦可)" : "锁定当前手牌 (按音量减键亦可)")
                        .font(.system(size: 16, weight: .heavy))
                }
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(isLocked ?
                    LinearGradient(colors: [Color.blue, Color(red: 0.1, green: 0.35, blue: 0.9)], startPoint: .top, endPoint: .bottom) :
                    LinearGradient(colors: [Color.orange, Color(red: 0.9, green: 0.4, blue: 0.0)], startPoint: .top, endPoint: .bottom))
                .foregroundColor(.white)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .shadow(color: Color.black.opacity(0.4), radius: 6, x: 0, y: 3)
            }
            .accessibilityLabel(isLocked ? "朗读报牌，当前已锁定\(accumulatedCards.count)张手牌" : "锁定当前手牌")
            .accessibilityAddTraits(.isButton)
        }
        .padding(14)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.18), lineWidth: 1))
        .shadow(color: Color.black.opacity(0.4), radius: 12, x: 0, y: 6)
    }
    
    // MARK: - Detection Overlay Canvas
    private var detectionOverlayCanvas: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            
            // Camera sensor is 720x1280 portrait (aspect 9:16)
            let sensorW: CGFloat = 720.0
            let sensorH: CGFloat = 1280.0
            let scale = max(w / sensorW, h / sensorH)
            let displayedW = sensorW * scale
            let displayedH = sensorH * scale
            let offsetX = (w - displayedW) / 2.0
            let offsetY = (h - displayedH) / 2.0
            
            // Model input is a center square crop of size min(sensorW, sensorH) = 720x720
            let cropRatioY: CGFloat = sensorW / sensorH // 720 / 1280 = 0.5625
            let cropOffsetY: CGFloat = (1.0 - cropRatioY) / 2.0 // 0.21875
            
            ForEach(modelHandler.detectedBoxes) { box in
                let sensorXMin = CGFloat(box.xMin)
                let sensorXMax = CGFloat(box.xMax)
                let sensorYMin = cropOffsetY + CGFloat(box.yMin) * cropRatioY
                let sensorYMax = cropOffsetY + CGFloat(box.yMax) * cropRatioY
                
                let rectX = offsetX + sensorXMin * displayedW
                let rectY = offsetY + sensorYMin * displayedH
                let rectW = (sensorXMax - sensorXMin) * displayedW
                let rectH = (sensorYMax - sensorYMin) * displayedH
                
                let isJoker = (box.classId == 52 || box.classId == 53)
                let card = PlayingCard.fromClassId(box.classId)
                let labelText: String = {
                    if let c = card {
                        return c.isJoker ? c.rank.label : "\(c.suit.symbol)\(c.rank.label)"
                    }
                    return "Card"
                }()
                
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(isJoker ? Color.purple : Color.green, lineWidth: 2.5)
                        .frame(width: rectW, height: rectH)
                        .background(RoundedRectangle(cornerRadius: 6).fill(isJoker ? Color.purple.opacity(0.12) : Color.green.opacity(0.08)))
                    
                    Text("\(labelText) \(Int(box.confidence * 100))%")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 2)
                        .background(isJoker ? Color.purple : Color.black.opacity(0.75))
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                        .offset(x: 2, y: -18)
                }
                .position(x: rectX + rectW / 2.0, y: rectY + rectH / 2.0)
            }
        }
    }
    
    // MARK: - Setup and Lifecycle
    private func setupInitialState() {
        isViewActive = true
        if settings.enableAimingGuidance {
            guidanceEngine.startGuidance()
        }
        setupVolumeObservation()
        setupVoiceCommandListener()

        if settings.enableVoiceCommands {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                guard self.isViewActive else { return }
                speechService.startListening(language: settings.voiceLanguage)
            }
        }
    }
    
    private func teardownState() {
        isViewActive = false
        guidanceEngine.stopGuidance()
        speechService.stopListening()
        volumeObserver?.invalidate()
        volumeObserver = nil
        speechService.onCommandMatched = nil
    }
    
    private func setupVoiceCommandListener() {
        speechService.onCommandMatched = { [self] command in
            switch command {
            case .lockCards:
                if !isLocked { lockCards() }
            case .newCards:
                resetCards()
            case .readCards:
                if !isLocked { lockCards() }
                speakCards()
            case .switchMode:
                toggleGameMode()
            case .switchToCdd:
                if settings.gameMode != .choDaiDee {
                    settings.gameMode = .choDaiDee
                    handleModeChanged()
                    speakAnnouncement(
                        cantonese: "已切換至鋤大D模式",
                        mandarin: "已切换至锄大地模式",
                        english: "Switched to Big Two mode"
                    )
                }
            case .switchToDdz:
                if settings.gameMode != .douDiZhu {
                    settings.gameMode = .douDiZhu
                    handleModeChanged()
                    speakAnnouncement(
                        cantonese: "已切換至鬥地主模式",
                        mandarin: "已切换至斗地主模式",
                        english: "Switched to Dou Di Zhu mode"
                    )
                }
            case .switchRole:
                toggleDdzRole()
            case .switchToLandlord:
                if settings.gameMode == .douDiZhu && settings.ddzRole != .landlord {
                    settings.ddzRole = .landlord
                    handleRoleChanged()
                    speakAnnouncement(
                        cantonese: "已選擇地主身份，20隻手牌",
                        mandarin: "已选择地主身份，20张手牌",
                        english: "Selected Landlord, 20 cards"
                    )
                }
            case .switchToPeasant:
                if settings.gameMode == .douDiZhu && settings.ddzRole != .peasant {
                    settings.ddzRole = .peasant
                    handleRoleChanged()
                    speakAnnouncement(
                        cantonese: "已選擇農民身份，17隻手牌",
                        mandarin: "已选择农民身份，17张手牌",
                        english: "Selected Peasant, 17 cards"
                    )
                }
            }
        }
    }
    
    // MARK: - Card Accumulation & Hand Reevaluation
    private func handleIncomingCards(_ newCards: [PlayingCard]) {
        guard !isLocked else { return }
        guard !newCards.isEmpty else { return }
        
        if settings.gameMode == .choDaiDee {
            cddJokerWarning = newCards.contains(where: { $0.isJoker })
            let validCards = newCards.filter { !$0.isJoker }
            guard !validCards.isEmpty else { return }
            
            let combined = (accumulatedCards + validCards).reduce(into: [PlayingCard]()) {
                if !$0.contains($1) { $0.append($1) }
            }
            let truncated = Array(combined.prefix(13))
            if truncated != accumulatedCards {
                accumulatedCards = truncated
                reevaluateHands()
            }
        } else {
            cddJokerWarning = false
            let maxCards = targetCount
            let combined = (accumulatedCards + newCards).reduce(into: [PlayingCard]()) {
                if !$0.contains($1) { $0.append($1) }
            }
            let truncated = Array(combined.prefix(maxCards))
            if truncated != accumulatedCards {
                accumulatedCards = truncated
                reevaluateHands()
            }
        }
    }
    
    private func reevaluateHands() {
        detectedDdzHands = DdzHandFinder.findAllHands(cards: accumulatedCards)
        detectedCddHands = CddHandFinder.findAllHands(cards: accumulatedCards, variation: settings.cddVariation)
    }
    
    private func removeCard(_ card: PlayingCard) {
        if let idx = accumulatedCards.firstIndex(of: card) {
            accumulatedCards.remove(at: idx)
            reevaluateHands()
            guidanceEngine.playHaptic(type: "search_tick")
        }
    }
    
    private func handleModeChanged() {
        if settings.gameMode == .choDaiDee {
            cddJokerWarning = false
            let nonJokers = accumulatedCards.filter { !$0.isJoker }
            accumulatedCards = Array(nonJokers.prefix(13))
        } else {
            cddJokerWarning = false
            let maxCards = targetCount
            accumulatedCards = Array(accumulatedCards.prefix(maxCards))
        }
        reevaluateHands()
    }
    
    private func handleRoleChanged() {
        let maxCards = targetCount
        if accumulatedCards.count > maxCards {
            accumulatedCards = Array(accumulatedCards.prefix(maxCards))
        }
        reevaluateHands()
    }
    
    // MARK: - Actions
    private func primaryActionTriggered() {
        if !isLocked {
            lockCards()
        } else {
            speakCards()
        }
    }
    
    private func toggleLock() {
        if isLocked {
            unlockCards()
        } else {
            lockCards()
        }
    }
    
    private func lockCards() {
        guard !isLocked else { return }
        isLocked = true
        
        guidanceEngine.playLockChord()
        guidanceEngine.stopGuidance()
        
        speakAnnouncement(
            cantonese: "手牌已鎖定，共 \(accumulatedCards.count) 隻。隨時按音量加鍵或點擊報牌。",
            mandarin: "手牌已锁定，共 \(accumulatedCards.count) 张。随时按音量加键或点击报牌。",
            english: "\(accumulatedCards.count) cards locked. Press volume up or tap to read cards."
        )
    }
    
    private func unlockCards() {
        guard isLocked else { return }
        isLocked = false
        
        if settings.enableAimingGuidance {
            guidanceEngine.startGuidance()
        }
        guidanceEngine.playHaptic(type: "search_tick")
        speakAnnouncement(
            cantonese: "手牌已解鎖，繼續掃描",
            mandarin: "手牌已解锁，继续扫描",
            english: "Hand unlocked, continuing scan"
        )
    }
    
    private func resetCards() {
        isLocked = false
        accumulatedCards.removeAll()
        detectedDdzHands.removeAll()
        detectedCddHands.removeAll()
        cddJokerWarning = false
        guidanceEngine.playHaptic(type: "search_tick")
        
        if settings.enableAimingGuidance {
            guidanceEngine.startGuidance()
        }
        speakAnnouncement(
            cantonese: "手牌已清空重置",
            mandarin: "手牌已清空重置",
            english: "Cards cleared and reset"
        )
    }
    
    private func speakCards() {
        let cards = accumulatedCards
        let text: String
        if settings.gameMode == .douDiZhu {
            text = CardSpeechFormatter.formatDdzSpeech(
                cards: cards,
                hands: detectedDdzHands,
                language: settings.voiceLanguage,
                detailed: true
            )
        } else {
            text = CardSpeechFormatter.formatCddSpeech(
                cards: cards,
                hands: detectedCddHands,
                language: settings.voiceLanguage,
                variation: settings.cddVariation,
                detailed: true
            )
        }
        
        speechEngine.speak(
            text: text,
            language: settings.voiceLanguage,
            rate: settings.speechRate,
            pitch: settings.speechPitch,
            volume: settings.speechVolume
        )
    }
    
    private func speakAnnouncement(cantonese: String, mandarin: String, english: String) {
        let text: String
        switch settings.voiceLanguage {
        case .cantonese: text = cantonese
        case .mandarin: text = mandarin
        case .english: text = english
        }
        speechEngine.speak(
            text: text,
            language: settings.voiceLanguage,
            rate: settings.speechRate,
            pitch: settings.speechPitch,
            volume: settings.speechVolume
        )
    }
    
    private func speakAnnouncement(_ text: String) {
        speechEngine.speak(
            text: text,
            language: settings.voiceLanguage,
            rate: settings.speechRate,
            pitch: settings.speechPitch,
            volume: settings.speechVolume
        )
    }
    
    private func toggleGameMode() {
        settings.gameMode = (settings.gameMode == .douDiZhu) ? .choDaiDee : .douDiZhu
        guidanceEngine.playHaptic(type: "alignment_pulse")
        handleModeChanged()
        let isDdz = settings.gameMode == .douDiZhu
        speakAnnouncement(
            cantonese: isDdz ? "已切換為鬥地主模式" : "已切換為鋤大D模式",
            mandarin: isDdz ? "已切换为斗地主模式" : "已切换为锄大地模式",
            english: isDdz ? "Switched to Dou Di Zhu mode" : "Switched to Big Two mode"
        )
    }
    
    private func toggleDdzRole() {
        guard settings.gameMode == .douDiZhu else { return }
        settings.ddzRole = (settings.ddzRole == .peasant) ? .landlord : .peasant
        guidanceEngine.playHaptic(type: "alignment_pulse")
        handleRoleChanged()
        let isLandlord = settings.ddzRole == .landlord
        speakAnnouncement(
            cantonese: isLandlord ? "已切換為地主，20隻手牌" : "已切換為農民，17隻手牌",
            mandarin: isLandlord ? "已切换为地主，20张手牌" : "已切换为农民，17张手牌",
            english: isLandlord ? "Switched to Landlord, 20 cards" : "Switched to Peasant, 17 cards"
        )
    }
    
    private var centeringColor: Color {
        if modelHandler.centeringScore >= 0.80 {
            return .green
        } else if modelHandler.centeringScore >= 0.50 {
            return .yellow
        } else if modelHandler.centeringScore > 0.05 {
            return .orange
        } else {
            return .gray
        }
    }
    
    // MARK: - Hardware Volume Key Observation
    private func setupVolumeObservation() {
        volumeObserver = AVAudioSession.sharedInstance().observe(\.outputVolume, options: [.new]) { session, change in
            DispatchQueue.main.async {
                let newVol = session.outputVolume
                if newVol < self.initialVolume {
                    self.primaryActionTriggered()
                } else if newVol > self.initialVolume {
                    self.speakCards()
                }
                self.initialVolume = newVol
            }
        }
    }
}
