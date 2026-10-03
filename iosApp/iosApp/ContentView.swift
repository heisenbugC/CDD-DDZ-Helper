import SwiftUI
import ComposeApp

struct ContentView: View {
    @StateObject private var modelHandler = CardModelLiteRT()
    
    var body: some View {
        ZStack {
            CameraView { pixelBuffer in
                modelHandler.processFrame(pixelBuffer)
            }
            .edgesIgnoringSafeArea(.all)
            
            VStack {
                Spacer()
                VStack(spacing: 8) {
                    Text("CDD-DDZ Helper")
                        .font(.headline)
                        .foregroundColor(.white)
                    
                    Text("Suit: \(modelHandler.suitResult)")
                        .font(.title2)
                        .bold()
                        .foregroundColor(.green)
                    
                    Text("Rank: \(modelHandler.rankResult)")
                        .font(.title2)
                        .bold()
                        .foregroundColor(.yellow)
                }
                .padding()
                .background(Color.black.opacity(0.7))
                .cornerRadius(12)
                .padding(.bottom, 40)
            }
        }
    }
}
