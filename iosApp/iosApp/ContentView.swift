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
 */
public struct ContentView: View {
    @ObservedObject private var settings = AppSettings.shared
    @StateObject private var modelHandler = CardModelLiteRT()
    @StateObject private var guidanceEngine = AudioHapticGuidance()
    @ObservedObject private var speechService = SpeechRecognitionService.shared
    @ObservedObject private var speechEngine = NativeSpeechEngine.shared
    
    // Core game state
    @State private var isLocked: Bool = false
    @State private var lockedCards: [PlayingCard] = []
    @State private var showSettings: Bool = false
    @State private var showHandsExplorer: Bool = false
    
    // Volume button observation
    @State private var initialVolume: Float = AVAudioSession.sharedInstance().outputVolume
    
    // Active cards: locked cards if locked, else real-time detected cards
    private var activeCards: [PlayingCard] {
        return isLocked ? lockedCards : modelHandler.detectedCards
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
        .sheet(isPresented: $showSettings) {
            SettingsView()
        }
        .sheet(isPresented: $showHandsExplorer) {
            HandsExplorerView(
                cards: activeCards,
                gameMode: settings.gameMode,
                ddzRole: settings.ddzRole,
                cddVariation: settings.cddVariation
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
                    Image(systemName: speechService.isListening ? "waveform" : "mic.slash")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(speechService.isListening ? .green : .gray)
                }
                .frame(width: 30, height: 30)
                .background(Color.black.opacity(0.6))
                .clipShape(Circle())
                .accessibilityLabel(speechService.isListening ? "语音指令监听中" : "语音监听已停止")
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
        VStack(spacing: 12) {
            // Header Row: Card Count & Action Chips
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: isLocked ? "lock.fill" : "camera.viewfinder")
                        .font(.system(size: 12, weight: .bold))
                    Text(isLocked ? "手牌: \(activeCards.count)张 (已锁定)" : "扫描中: \(activeCards.count)张")
                        .font(.system(size: 13, weight: .bold))
                }
                .foregroundColor(isLocked ? .orange : .white)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color.black.opacity(0.55))
                .clipShape(Capsule())
                
                Spacer()
                
                // Hands Explorer Sheet Button
                Button(action: { showHandsExplorer = true }) {
                    HStack(spacing: 4) {
                        Image(systemName: "rectangle.stack.fill")
                            .font(.system(size: 11, weight: .bold))
                        Text("牌型库")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.purple.opacity(0.75))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .accessibilityLabel("查看所有可用牌型")
                
                // Reset Button
                Button(action: resetCards) {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.counterclockwise")
                            .font(.system(size: 11, weight: .bold))
                        Text("重置")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundColor(Color(red: 1.0, green: 0.4, blue: 0.4))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.red.opacity(0.25))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .accessibilityLabel("清空手牌重置")
            }
            
            // Cards Horizontal Shelf
            if !activeCards.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(Array(activeCards.enumerated()), id: \.offset) { index, card in
                            AccessibleCardView(
                                suit: card.suit.rawValue,
                                rank: card.rank.label,
                                isRed: card.isRed,
                                isLocked: isLocked,
                                onRemove: {
                                    if isLocked && index < lockedCards.count {
                                        lockedCards.remove(at: index)
                                        guidanceEngine.playHaptic(type: "search_tick")
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
            .accessibilityLabel(isLocked ? "朗读报牌，当前已锁定\(activeCards.count)张手牌" : "锁定当前手牌")
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
            
            ForEach(modelHandler.detectedBoxes) { box in
                let rect = CGRect(
                    x: CGFloat(box.xMin) * w,
                    y: CGFloat(box.yMin) * h,
                    width: CGFloat(box.xMax - box.xMin) * w,
                    height: CGFloat(box.yMax - box.yMin) * h
                )
                
                RoundedRectangle(cornerRadius: 4)
                    .stroke(box.classId == 52 || box.classId == 53 ? Color.purple : Color.green, lineWidth: 2)
                    .frame(width: rect.width, height: rect.height)
                    .position(x: rect.midX, y: rect.midY)
            }
        }
    }
    
    // MARK: - Setup and Lifecycle
    private func setupInitialState() {
        if settings.enableAimingGuidance {
            guidanceEngine.startGuidance()
        }
        setupVolumeObservation()
        setupVoiceCommandListener()
        
        if settings.enableVoiceCommands {
            speechService.startListening(language: settings.voiceLanguage)
        }
    }
    
    private func teardownState() {
        guidanceEngine.stopGuidance()
        speechService.stopListening()
    }
    
    private func setupVoiceCommandListener() {
        speechService.onCommandMatched = { [self] command in
            switch command {
            case .lockCards:
                if !isLocked { lockCards() }
            case .newCards:
                resetCards()
            case .readCards:
                speakCards()
            case .switchMode:
                toggleGameMode()
            case .switchToCdd:
                settings.gameMode = .choDaiDee
                speakAnnouncement("已切换至锄大地模式")
            case .switchToDdz:
                settings.gameMode = .douDiZhu
                speakAnnouncement("已切换至斗地主模式")
            case .switchRole:
                toggleDdzRole()
            case .switchToLandlord:
                settings.ddzRole = .landlord
                speakAnnouncement("已选择地主身份，20张手牌")
            case .switchToPeasant:
                settings.ddzRole = .peasant
                speakAnnouncement("已选择农民身份，17张手牌")
            }
        }
    }
    
    // MARK: - Actions
    private func primaryActionTriggered() {
        if !isLocked {
            lockCards()
        } else {
            speakCards()
        }
    }
    
    private func lockCards() {
        guard !isLocked else { return }
        isLocked = true
        lockedCards = modelHandler.detectedCards
        
        guidanceEngine.playLockChord()
        guidanceEngine.stopGuidance()
        
        speakAnnouncement(isDouDiZhuMode ?
            "手牌已锁定，共 \(lockedCards.count) 张。随时按音量加键或点击报牌。" :
            "手牌已锁定，共 \(lockedCards.count) 张。随时按音量加键或点击报牌。")
    }
    
    private func resetCards() {
        isLocked = false
        lockedCards.removeAll()
        guidanceEngine.playHaptic(type: "search_tick")
        
        if settings.enableAimingGuidance {
            guidanceEngine.startGuidance()
        }
    }
    
    private func speakCards() {
        let cards = activeCards
        let text: String
        if settings.gameMode == .douDiZhu {
            let hands = DdzHandFinder.findAllHands(cards: cards)
            text = CardSpeechFormatter.formatDdzSpeech(
                cards: cards,
                hands: hands,
                language: settings.voiceLanguage,
                detailed: true
            )
        } else {
            let hands = CddHandFinder.findAllHands(cards: cards, variation: settings.cddVariation)
            text = CardSpeechFormatter.formatCddSpeech(
                cards: cards,
                hands: hands,
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
        speakAnnouncement("已切换为\(settings.gameMode.title)模式")
    }
    
    private func toggleDdzRole() {
        guard settings.gameMode == .douDiZhu else { return }
        settings.ddzRole = (settings.ddzRole == .peasant) ? .landlord : .peasant
        guidanceEngine.playHaptic(type: "alignment_pulse")
        speakAnnouncement("已切换为\(settings.ddzRole.title)")
    }
    
    private var isDouDiZhuMode: Bool {
        return settings.gameMode == .douDiZhu
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
        NotificationCenter.default.addObserver(
            forName: NSNotification.Name(rawValue: "AVSystemController_SystemVolumeDidChangeNotification"),
            object: nil,
            queue: .main
        ) { _ in
            let newVol = AVAudioSession.sharedInstance().outputVolume
            if newVol < initialVolume {
                // Volume Down -> Lock cards if unlocked, Reset if locked
                if !isLocked {
                    lockCards()
                } else {
                    resetCards()
                }
            } else if newVol > initialVolume {
                // Volume Up -> Readout cards
                if isLocked {
                    speakCards()
                } else {
                    lockCards()
                }
            }
            initialVolume = newVol
        }
    }
}
