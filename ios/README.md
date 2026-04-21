# Hank Daisy — iOS App

Real-time voice-driven automotive diagnostics for Oakley Meta Vanguard glasses.

## Architecture

Glasses (Bluetooth)
  -> StreamSessionManager (MWDAT SDK)
  -> Voice: "Hey Hank?" -> VoiceManager (SFSpeechRecognizer)
  -> Question -> StreamViewModel -> AgentClient (WebSocket)
  -> Python Agent Server (ws://host:8765)
  -> Response (streamed) -> TTSManager (AVSpeechSynthesizer)
  -> Glasses Audio

## Requirements

- **iOS 17.0+** (MWDAT SDK requirement)
- **Xcode 15.0+**
- **Apple Developer Program** ($99/year) — **required** for entitlements:
  - `bluetooth-central`, `bluetooth-peripheral`
  - `external-accessory` protocol (`com.meta.ar.wearable`)
  - `keychain-access-groups`
- **Meta Wearables Developer Center** account — for production credentials
- **Meta AI app** on iOS device — required for glasses registration OAuth flow

## Setup

### 1. Clone the Project

```bash
cd hank-daisy
git checkout iosport
```

### 2. Configure Agent URL

```bash
# Copy the config template
cp ios/Config.xcconfig.template ios/Config.xcconfig

# Edit Config.xcconfig and set HANK_AGENT_URL
# For simulator: ws://localhost:8765
# For real device: ws://192.168.1.x:8765 (your Mac's LAN IP)
```

### 3. Start the Python Agent Server

```bash
cd hank-daisy
python -m agent.server
# Server starts on ws://0.0.0.0:8765
```

### 4. Build and Run on Simulator (with MockDeviceKit)

```bash
# Open in Xcode
xcode ios/HankDaisy.xcodeproj

# In Xcode:
# - Project Settings > Signing & Capabilities > Team: (your Apple ID)
# - Product > Run on iPhone simulator
```

The app auto-enables MockDeviceKit in DEBUG builds, so you don't need real glasses to test on simulator.

### 5. Deploy to Real Device (with Glasses)

```bash
# Prerequisites:
# 1. Apple Developer Program membership ($99/year)
# 2. Oakley Meta Vanguard glasses registered in Meta Wearables Developer Center
# 3. Meta AI app installed on your iPhone
# 4. iPhone + Mac on same Wi-Fi network

# In Xcode:
# 1. Project Settings > Signing & Capabilities > Team: (your paid Apple ID)
# 2. Set Bundle ID to something unique (e.g., com.yourname.hankdaisy)
# 3. Plug iPhone into Mac via USB
# 4. Product > Run
# 5. On iPhone: Settings > General > VPN & Device Management > Trust developer
# 6. App launches and prompts for glasses registration
```

## Key Components

### Agent Client (`Agent/AgentClient.swift`)
- `URLSessionWebSocketTask` for WebSocket connection
- Sends: `{text, frame?, session_id}`
- Receives: streaming chunks, done, or error
- Thread-safe via `actor` isolation

### Voice Manager (`Voice/VoiceManager.swift`)
- `SFSpeechRecognizer` for continuous speech-to-text
- Passive listening for "Hey Hank" wake word
- Active listening for user questions after wake word detected
- Publishes `VoiceState` and recognized text

### TTS Manager (`TTS/TTSManager.swift`)
- `AVSpeechSynthesizer` for text-to-speech
- Sentence-by-sentence queue (detects . ! ? boundaries)
- Streamed responses play as they arrive (no waiting for full response)

### Glasses Integration
- **DeviceSessionManager** — handles Wearables registration, device connection
- **StreamSessionManager** — manages StreamSession, captures video frames, encodes to JPEG

### ViewModels
- **StreamViewModel** — orchestrates voice  agent  TTS flow
- Maintains chat history (last 100 messages)
- Auto-saves agent URL to UserDefaults

### UI Views
- **StreamView** — main live view (video feed + chat + voice button)
- **ChatPanel** — scrollable message history
- **SettingsView** — agent URL configuration, clear chat
- **RegistrationView** — glasses pairing flow

## Configuration

### Info.plist Keys

| Key | Value | Purpose |
|-----|-------|---------|
| `MWDAT.AppLinkURLScheme` | `hankdaisy` | Custom URL scheme for registration callback |
| `MWDAT.MetaAppID` | `0` (dev) or real ID | Identifies your app to Meta |
| `MWDAT.ClientToken` | (production) | API token from Wearables Developer Center |
| `NSMicrophoneUsageDescription` | User-facing string | Permission prompt |
| `NSBluetoothAlwaysUsageDescription` | User-facing string | Bluetooth permission |
| `UIBackgroundModes` | Array | Keeps Bluetooth connection alive |

### Config.xcconfig

Set these before building for real device:

```
HANK_AGENT_URL = ws://192.168.1.100:8765
```

## Protocol (to Python Agent)

**Request:**
```json
{
  "text": "What's wrong with this alternator?",
  "frame": "<base64 JPEG>",
  "media_type": "image/jpeg",
  "session_id": "uuid"
}
```

**Response (streamed):**
```json
{"type": "chunk", "text": "The alternator brushes"}
{"type": "chunk", "text": " are worn and need replacement."}
{"type": "done"}
```

## Troubleshooting

### Agent Connection Fails
- Check Python server is running: `python -m agent.server`
- Verify WiFi: iPhone and Mac must be on same network
- Check URL in Settings: `ws://192.168.1.x:8765` (use Mac's IP, not localhost on real device)
- Firewall: may need to allow port 8765

### Glasses Registration Fails
- Ensure Meta AI app is installed
- Ensure you have Apple Developer Program membership
- Check entitlements in Xcode: Signing & Capabilities must show bluetooth-central + external-accessory

### Microphone Permission Denied
- Go to Settings  HankDaisy  Microphone  Allow

### MockDeviceKit Not Working (Simulator)
- Only works in DEBUG builds
- Check Xcode build configuration is set to Debug
- Restart simulator: Device  Erase All Content and Settings

## Testing Checklist

- [ ] Simulator: MockDeviceKit auto-enables, video feed displays test pattern
- [ ] Simulator: "Hey Hank" wake word detected, switches to active listening
- [ ] Simulator: Query sent to agent, response streams back
- [ ] Simulator: Sentence-by-sentence TTS plays
- [ ] Real device: Glasses registered via Meta AI app
- [ ] Real device: Video feed from glasses displays
- [ ] Real device: Voice queries work end-to-end
- [ ] Real device: Agent URL configured for LAN IP

## Next Steps

1. **Hardening the agent** — add session_id support to Python server (Phase 2)
2. **Deploying Android changes** — wire Android app to WebSocket agent (Phase 2)
3. **Production configuration** — register app with Meta Wearables Developer Center for real tokens
