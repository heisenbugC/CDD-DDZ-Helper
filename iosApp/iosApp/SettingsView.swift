import SwiftUI
import Speech
import AVFoundation

/**
 * SettingsView: Apple Native Inset Grouped Form interface.
 * Implements full parity with Android settings while adhering strictly to iOS Human Interface Guidelines:
 * - Native NavigationStack with toolbar done action
 * - Inset grouped Form with SF Symbols
 * - Segmented Pickers for Mode, Role, and Language
 * - Sliders for TTS Speech Rate, Pitch, and Audio Volume
 * - Real-time Voice Diagnostics & Mic Level testing
 */
public struct SettingsView: View {
    @ObservedObject var settings = AppSettings.shared
    @ObservedObject var speechService = SpeechRecognitionService.shared
    @ObservedObject var speechEngine = NativeSpeechEngine.shared
    
    @Environment(\.presentationMode) var presentationMode
    
    // Diagnostic test states
    @State private var isTestingVoiceInput: Bool = false
    @State private var testVoiceResult: String = ""
    @State private var testVoiceCommand: GameVoiceCommand? = nil
    @State private var isTestingTts: Bool = false
    
    public init() {}
    
    public var body: some View {
        NavigationStack {
            Form {
                // Section 1: 🎮 游戏模式与规则设置
                Section {
                    // Game Mode Picker
                    VStack(alignment: .leading, spacing: 8) {
                        Label("游戏模式", systemImage: "gamecontroller.fill")
                            .font(.subheadline)
                            .foregroundColor(.primary)
                        
                        Picker("游戏模式", selection: $settings.gameMode) {
                            Text("🎴 斗地主").tag(GameMode.douDiZhu)
                            Text("♠️ 锄大地").tag(GameMode.choDaiDee)
                        }
                        .pickerStyle(.segmented)
                    }
                    .padding(.vertical, 4)
                    
                    // DDZ Role Picker (if DDZ)
                    if settings.gameMode == .douDiZhu {
                        VStack(alignment: .leading, spacing: 8) {
                            Label("斗地主身份", systemImage: "person.crop.circle.badge.checkmark")
                                .font(.subheadline)
                                .foregroundColor(.primary)
                            
                            Picker("身份选择", selection: $settings.ddzRole) {
                                Text("🌾 农民 (17张)").tag(DdzRole.peasant)
                                Text("👑 地主 (20张)").tag(DdzRole.landlord)
                            }
                            .pickerStyle(.segmented)
                        }
                        .padding(.vertical, 4)
                    }
                    
                    // CDD Variation Picker (if CDD)
                    if settings.gameMode == .choDaiDee {
                        VStack(alignment: .leading, spacing: 8) {
                            Label("锄大地规则", systemImage: "flag.fill")
                                .font(.subheadline)
                                .foregroundColor(.primary)
                            
                            Picker("规则", selection: $settings.cddVariation) {
                                Text("大陆规则 (同花>顺子)").tag(CddVariation.mainland)
                                Text("香港规则 (蛇>同花)").tag(CddVariation.hongKong)
                            }
                            .pickerStyle(.segmented)
                        }
                        .padding(.vertical, 4)
                    }
                } header: {
                    Text("游戏模式与规则")
                } footer: {
                    Text(settings.gameMode == .douDiZhu ?
                         "斗地主包含大小王共54张牌；地主拥有20张手牌，农民17张手牌。" :
                         "锄大地使用无大小王的52张扑克牌；黑桃2最大，方块3最小。")
                }
                
                // Section 2: 🗣️ 语音与播报设置
                Section {
                    // Spoken language
                    VStack(alignment: .leading, spacing: 8) {
                        Label("播报语言 / 语音识别", systemImage: "globe.asia.australia.fill")
                            .font(.subheadline)
                        
                        Picker("语言", selection: $settings.voiceLanguage) {
                            Text("🇭🇰 粤语 (香港)").tag(VoiceLanguage.cantonese)
                            Text("🇨🇳 普通话").tag(VoiceLanguage.mandarin)
                            Text("🇺🇸 English").tag(VoiceLanguage.english)
                        }
                        .pickerStyle(.segmented)
                    }
                    .padding(.vertical, 4)
                    
                    // Voice Commands Toggle
                    Toggle(isOn: $settings.enableVoiceCommands) {
                        Label("实时语音指令监听", systemImage: "waveform.circle.fill")
                    }
                    
                    // Aiming Guidance Toggle
                    Toggle(isOn: $settings.enableAimingGuidance) {
                        Label("非视觉瞄准声调指引", systemImage: "speaker.wave.2.fill")
                    }
                    
                    // Haptic Feedback Toggle
                    Toggle(isOn: $settings.enableHapticFeedback) {
                        Label("触觉震动反馈", systemImage: "iphone.radiowaves.left.and.right")
                    }
                    
                    // Speech Rate Slider
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Label("朗读语速", systemImage: "speedometer")
                            Spacer()
                            Text("\(Int(settings.speechRate * 100))%")
                                .foregroundColor(.secondary)
                                .monospacedDigit()
                        }
                        Slider(value: $settings.speechRate, in: 0.3...0.8, step: 0.05)
                    }
                    .padding(.vertical, 2)
                    
                    // Speech Pitch Slider
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Label("朗读音调", systemImage: "tuningfork")
                            Spacer()
                            Text("\(String(format: "%.1f", settings.speechPitch))x")
                                .foregroundColor(.secondary)
                                .monospacedDigit()
                        }
                        Slider(value: $settings.speechPitch, in: 0.7...1.3, step: 0.1)
                    }
                    .padding(.vertical, 2)
                    
                    // Volume Slider
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Label("提示音量", systemImage: "speaker.wave.3.fill")
                            Spacer()
                            Text("\(Int(settings.speechVolume * 100))%")
                                .foregroundColor(.secondary)
                                .monospacedDigit()
                        }
                        Slider(value: $settings.speechVolume, in: 0.2...1.0, step: 0.05)
                    }
                    .padding(.vertical, 2)
                } header: {
                    Text("语音与感官辅助")
                }
                
                // Section 3: 🎙️ 语音输入与诊断测试
                Section {
                    // Real-time Mic Audio Level Meter
                    HStack(spacing: 12) {
                        Label("实时麦克风音量", systemImage: "mic.fill")
                            .font(.subheadline)
                        
                        Spacer()
                        
                        // Animated Level Bar
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(Color.gray.opacity(0.25))
                                    .frame(height: 8)
                                
                                Capsule()
                                    .fill(speechService.audioLevel > 0.6 ? Color.orange : Color.green)
                                    .frame(width: max(4, geo.size.width * CGFloat(speechService.audioLevel)), height: 8)
                                    .animation(.easeOut(duration: 0.1), value: speechService.audioLevel)
                            }
                        }
                        .frame(width: 80, height: 8)
                        
                        Text("\(Int(speechService.audioLevel * 100))%")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .frame(width: 36, alignment: .trailing)
                            .monospacedDigit()
                    }
                    
                    // Current Recognition Status
                    HStack {
                        Label("语音引擎", systemImage: "applelogo")
                        Spacer()
                        Text("Apple 原生 SFSpeechRecognizer")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                    
                    // Test Speech Input Button
                    Button(action: runVoiceInputTest) {
                        HStack {
                            Image(systemName: isTestingVoiceInput ? "waveform.badge.magnifyingglass" : "mic.badge.plus")
                                .foregroundColor(isTestingVoiceInput ? .orange : .blue)
                            Text(isTestingVoiceInput ? "正在倾听中 (请说出如'锁定'或'睇牌')..." : "测试语音输入 (录音3秒)")
                                .fontWeight(.semibold)
                        }
                    }
                    .disabled(isTestingVoiceInput)
                    
                    if !testVoiceResult.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("识别结果：")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text(testVoiceResult)
                                .font(.subheadline)
                                .bold()
                                .foregroundColor(.primary)
                            
                            if let cmd = testVoiceCommand {
                                HStack {
                                    Text("匹配指令：")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                    Text(cmd.description)
                                        .font(.caption)
                                        .bold()
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.green.opacity(0.2))
                                        .foregroundColor(.green)
                                        .clipShape(Capsule())
                                }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    
                    // Test Apple Native TTS Button
                    Button(action: runTtsTest) {
                        HStack {
                            Image(systemName: "speaker.wave.2.circle.fill")
                                .foregroundColor(.purple)
                            Text("测试语音朗读 (Apple 原生 TTS)")
                                .fontWeight(.semibold)
                        }
                    }
                    
                    if speechEngine.usedFallback {
                        HStack {
                            Image(systemName: "info.circle")
                                .foregroundColor(.orange)
                            Text("当前系统未安装该语言原生音库，已启用备用引擎。")
                                .font(.caption)
                                .foregroundColor(.orange)
                        }
                    }
                } header: {
                    Text("语音输入与诊断测试")
                } footer: {
                    Text("支持免提语音控制。如果系统首次请求权限，请允许麦克风及语音识别权限。")
                }
                
                // Section 4: 📱 物理按键与快捷操作指南
                Section {
                    HStack(spacing: 12) {
                        Image(systemName: "speaker.minus.fill")
                            .foregroundColor(.blue)
                            .frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("音量减键 (Volume Down)")
                                .font(.subheadline)
                                .bold()
                            Text("轻按一键锁定当前手牌；已锁定时轻按重置")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.vertical, 2)
                    
                    HStack(spacing: 12) {
                        Image(systemName: "speaker.plus.fill")
                            .foregroundColor(.orange)
                            .frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("音量加键 (Volume Up)")
                                .font(.subheadline)
                                .bold()
                            Text("随时朗读报牌并播报最佳推荐出牌")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.vertical, 2)
                    
                    VStack(alignment: .leading, spacing: 6) {
                        Text("支持的语音指令列表：")
                            .font(.subheadline)
                            .bold()
                        Text("• 锁定：\"锁定\"、\"锁牌\"、\"定牌\"、\"得喇\"、\"搞掂\"、\"Lock\"\n• 报牌：\"报牌\"、\"读牌\"、\"睇牌\"、\"点样出\"、\"有咩牌\"、\"Read cards\"\n• 重置：\"洗牌\"、\"新一局\"、\"重新发牌\"、\"解锁\"、\"Reset\"\n• 模式：\"换模式\"、\"锄大地\"、\"斗地主\"\n• 身份：\"换身份\"、\"当选地主\"、\"做地主\"、\"农民\"")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .lineSpacing(4)
                    }
                    .padding(.vertical, 4)
                } header: {
                    Text("物理按键与免提操作指南")
                }
            }
            .navigationTitle("设置与无障碍")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") {
                        presentationMode.wrappedValue.dismiss()
                    }
                    .fontWeight(.bold)
                }
            }
        }
    }
    
    // MARK: - Actions
    private func runVoiceInputTest() {
        guard !isTestingVoiceInput else { return }
        isTestingVoiceInput = true
        testVoiceResult = "正在聆听中 (3秒)..."
        testVoiceCommand = nil
        
        speechService.runDiagnosticTest(language: settings.voiceLanguage, testDuration: 3.5) { result, cmd in
            self.isTestingVoiceInput = false
            self.testVoiceResult = result
            self.testVoiceCommand = cmd
        }
    }
    
    private func runTtsTest() {
        let sampleText: String
        switch settings.voiceLanguage {
        case .cantonese:
            sampleText = "已鎖定17張手牌。最佳推薦出牌：大飛機帶翅膀。語音引擎運行正常。"
        case .mandarin:
            sampleText = "已锁定17张手牌。最佳推荐出牌：大飞机带翅膀。语音引擎运行正常。"
        case .english:
            sampleText = "17 cards locked. Recommended play: Airplane with wings. Speech engine is working properly."
        }
        
        speechEngine.speak(
            text: sampleText,
            language: settings.voiceLanguage,
            rate: settings.speechRate,
            pitch: settings.speechPitch,
            volume: settings.speechVolume
        )
    }
}

