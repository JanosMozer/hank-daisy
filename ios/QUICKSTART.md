# iOS App Quick Start

## Try it in 5 minutes (Simulator)

### Prerequisites
- Mac with Xcode 15.0+ installed
- iPhone 17.0+ simulator

### Steps

**1. Start the Python agent**
```bash
cd hank-daisy
python -m agent.server
```
You should see:
```
 Hank agent server running on ws://localhost:8765
```

**2. Open iOS project in Xcode**
```bash
cd ios
xcode HankDaisy.xcodeproj
```

**3. Run on simulator**
- In Xcode: Product  Run (R)
- Select iPhone 17.0+ simulator
- App launches

**4. Test the flow**
- RegistrationView appears (MockDeviceKit auto-pairs fake glasses)
- Tap "Connect to Glasses"
- StreamView displays test pattern video feed
- Tap the microphone button
- Say: "What's wrong with my alternator?"
- Agent responds with streaming text
- TTS plays the response

That's it! The full pipeline works: voice  agent  TTS.

---

## Deploy to Real Device (with Oakley Vanguard glasses)

### Prerequisites
- iPhone with iOS 17.0+
- Oakley Meta Vanguard glasses (firmware V22+)
- Meta AI app installed on iPhone
- Apple Developer Program account ($99/year) — **required**
- Meta Wearables Developer Center account (free)

### Steps

**1. Configure**
```bash
cp ios/Config.xcconfig.template ios/Config.xcconfig
# Edit Config.xcconfig:
#   HANK_AGENT_URL = ws://192.168.1.100:8765  (your Mac's IP)
```

**2. Xcode Setup**
- Open ios/HankDaisy.xcodeproj
- Project  Signing & Capabilities
- Set Team to your Apple Developer account
- Set Bundle ID to something unique (e.g., com.yourname.hankdaisy)

**3. Deploy**
- Plug iPhone into Mac via USB
- Product  Run (R)
- On iPhone: Settings  General  VPN & Device Management  Trust Your Name
- App launches, shows RegistrationView

**4. Register Glasses**
- Tap "Start Registration"
- Meta AI app opens
- Complete glasses pairing flow
- Return to Hank Daisy
- Tap "Connect to Glasses"
- Live glasses video appears in StreamView
- Voice queries now reach real glasses over Bluetooth

---

## What Happens Under the Hood

**Passive listening:**
- VoiceManager runs SFSpeechRecognizer continuously
- Listens for "Hey Hank" wake word

**Active listening:**
- When "Hey Hank" detected, user is prompted to speak their question
- Question text is captured

**Agent query:**
- Text sent to Python agent over WebSocket (ws://...)
- Optional: frame from glasses camera sent as base64 JPEG
- Session ID persists conversation history on server

**Streaming response:**
- Agent responds with chunks
- Chunks accumulated and TTS plays sentence-by-sentence
- User hears response on glasses (real device) or device speaker (simulator)

---

## Troubleshooting

### Agent Connection Error
```
Failed to connect to agent
```
**Fix:**
- Check Python server is running: `python -m agent.server`
- In Settings: verify ws:// URL (ws://localhost:8765 for simulator)
- On real device: use Mac's IP (ws://192.168.1.x:8765), not localhost

### Microphone Permission Denied
**Fix:**
- Settings  HankDaisy  Microphone  Allow

### App Crashes on Launch
**Fix:**
- Xcode might need to be rebuilt:
  - Product  Clean Build Folder (K)
  - Product  Build (B)
  - Product  Run (R)

### Simulator Shows "No video feed"
**Fix:**
- Restart simulator: Device  Erase All Content and Settings
- Rebuild app: clean  build  run

### Real Device: Glasses Not Pairing
**Fix:**
- Ensure Meta AI app is installed and logged in
- Ensure you have Apple Developer Program membership
- Ensure glasses firmware is V22 or later
- Try unpairing and re-pairing in Meta AI app

---

## Next: Android Changes (Phase 2b)

Once you confirm the iOS app works with the agent, we'll wire the Android app to use the same Python agent instead of directly calling `GeminiService`. This will let both platforms benefit from a single prompt/logic update.

See `../IMPLEMENTATION_PLAN.md` for details.
