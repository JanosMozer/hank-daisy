# Deploying Hank Daisy to Your iPhone

This guide walks you through getting the iOS app onto your iPhone with real Oakley Meta Vanguard glasses.

## Prerequisites

Before you can deploy to a real iPhone, you need:

### 1. **Apple Developer Program** ($99/year)
The glasses integration requires entitlements that **only paid Apple accounts can sign**:
- `bluetooth-central` — Bluetooth connectivity
- `external-accessory` — `com.meta.ar.wearable` protocol
- `keychain-access-groups` — Secure credential storage

**Action:** Sign up at https://developer.apple.com/programs/

### 2. **Meta Wearables Developer Center** (free)
Get API credentials for production use.

**Action:** Register at https://wearables.developer.meta.com/
- Enroll your app
- Get `MWDAT.ClientToken` and `MWDAT.MetaAppID`
- Register your glasses device

### 3. **Meta AI App** on iPhone
Required for glasses OAuth registration flow.

**Action:** Download from App Store, log in with your Meta account

### 4. **Oakley Meta Vanguard Glasses**
Paired with your Meta account in Meta AI app.

**Action:** Set up in Meta AI app following on-screen prompts

### 5. **Mac with Xcode 15.0+**
For building and deploying.

**Action:** Install Xcode from Mac App Store (free)

---

## Step-by-Step Deployment

### Step 1: Configure the App

**Copy the config template:**
```bash
cd ios
cp Config.xcconfig.template Config.xcconfig
```

**Edit Config.xcconfig:**
```xcconfig
HANK_AGENT_URL = ws://192.168.1.100:8765
```
Replace `192.168.1.100` with your Mac's IP address on the local network.

**To find your Mac's IP:**
```bash
# Terminal
ifconfig | grep "inet " | grep -v 127.0.0.1
# Look for something like: inet 192.168.1.100
```

### Step 2: Set Up Xcode

**Open the project:**
```bash
xcode HankDaisy.xcodeproj
```

**In Xcode:**
1. Click on the "HankDaisy" project in the left sidebar
2. Select the "HankDaisy" target
3. Go to the "Signing & Capabilities" tab
4. In the "Team" dropdown, select your Apple Developer account
5. Set a unique **Bundle ID** (e.g., `com.yourname.hankdaisy`)
6. Verify these capabilities are present:
   - ✅ Bluetooth Peripheral
   - ✅ Bluetooth Central
   - ✅ External Accessory
   - ✅ Keychain Sharing

### Step 3: Update Info.plist (Production)

**Open ios/HankDaisy/Info.plist in a text editor:**

Replace these keys with your actual values from Meta Wearables Developer Center:
```xml
<key>MWDAT</key>
<dict>
  <key>MetaAppID</key>
  <string>YOUR_REAL_APP_ID_HERE</string>

  <key>ClientToken</key>
  <string>YOUR_REAL_CLIENT_TOKEN_HERE</string>

  <key>TeamID</key>
  <string>YOUR_APPLE_TEAM_ID_HERE</string>
</dict>
```

If you don't have these yet, leave them blank and they'll default to development mode.

### Step 4: Start the Python Agent

The glasses need to reach your Mac's agent server.

**In one terminal:**
```bash
cd hank-daisy
python -m agent.server
```

You should see:
```
✓ Hank agent server running on ws://0.0.0.0:8765
```

### Step 5: Deploy to iPhone

**Plug your iPhone into your Mac via USB.**

**In Xcode:**
1. Top toolbar, select your iPhone as the deployment target (not "iPhone Simulator")
2. Product → Run (⌘R)
3. Wait for the build to complete

**On your iPhone:**
- A notification appears: "Your Developer Is Not Trusted"
- Go to Settings → General → VPN & Device Management
- Find your name and tap it
- Tap "Trust [Your Name]"
- Tap "Trust" again in the popup

The app will now launch on your phone.

### Step 6: Register Your Glasses

**In the HankDaisy app:**
1. The RegistrationView appears ("Pair with Oakley Meta Vanguard")
2. Tap "Start Registration"
3. Meta AI app opens
4. Follow the on-screen prompts to complete glasses pairing
5. Return to HankDaisy
6. Tap "Connect to Glasses"
7. Wait for "StreamView" (live glasses video feed)

### Step 7: Test End-to-End

**In the app:**
1. Tap the microphone button at the bottom
2. Say: "What's wrong with my alternator?" (or any question)
3. Watch for:
   - Status shows "Listening..." (blue icon)
   - Status shows "Analyzing..." (orange icon) while agent responds
   - Response streams in the chat
   - TTS plays through glasses audio

---

## Troubleshooting

### "Could Not Launch HankDaisy"
**Problem:** Entitlements not signed properly

**Fix:**
1. Product → Clean Build Folder (⇧⌘K)
2. Check Team is set (Project Settings → Signing & Capabilities)
3. Check Bundle ID is unique (not used by another app)
4. Try again: Product → Run (⌘R)

### "Failed to connect to agent"
**Problem:** iPhone can't reach Python server

**Fixes:**
1. Check Python server is running: `python -m agent.server`
2. Verify iPhone is on **same WiFi network** as Mac
3. In Settings view, change ws:// URL to your Mac's correct IP
4. Make sure firewall allows port 8765 (or check your router)

### "Microphone Permission Denied"
**Fix:** Settings → HankDaisy → Microphone → Allow

### "Glasses Not Found"
**Problem:** Glasses not registered

**Fix:**
1. Open Meta AI app on iPhone
2. Check glasses are paired with your Meta account
3. Try unpairing and re-pairing
4. Ensure glasses firmware is V22 or later

### "Glass Registration Fails"
**Problem:** App can't reach Meta servers

**Fixes:**
1. Check you're logged into Meta AI app
2. Check iPhone has internet (WiFi or cellular)
3. Check entitlements: Signing & Capabilities must show external-accessory

### "TTS Not Playing on Glasses"
**Problem:** Audio routing issue

**Fixes:**
1. Check glasses are connected (should see live video feed)
2. Check iPhone audio is not muted (mute switch on side)
3. Check glasses volume is not 0
4. Restart glasses Bluetooth connection

---

## Going into Production

Once you've confirmed everything works on your device:

### 1. Get Real Credentials
- Log in to Meta Wearables Developer Center
- Register your app formally
- Obtain `MWDAT.ClientToken` and `MWDAT.MetaAppID`
- Update Info.plist with real values

### 2. Change MetaAppID from `0` to Real Value
```xml
<key>MetaAppID</key>
<string>YOUR_REAL_ID</string>
```

### 3. Distribute via TestFlight or App Store
- In Xcode: Product → Archive
- Follow Xcode organizer prompts
- Can share with others via TestFlight (beta testing) or submit to App Store

---

## Architecture Reminder

```
iPhone + Oakley Meta Vanguard glasses (Bluetooth)
                ↓
         [iOS App — HankDaisy]
                ↓
         [MWDAT SDK integration]
        - Glasses registration (Meta AI app OAuth)
        - Video frame streaming
        - Audio routing to glasses speaker
                ↓
         [Voice + TTS pipeline]
        - SFSpeechRecognizer ("Hey Hank" wake word)
        - AVSpeechSynthesizer (sentence-by-sentence)
                ↓
         [WebSocket agent client]
        - Same protocol as Android
        - Streaming text responses
                ↓
         [Python Agent Server (on Mac)]
        - ws://192.168.1.100:8765
        - Receives: {text, frame?, session_id}
        - Streams: {chunk, done, error}
        - Maintains conversation history per session_id
                ↓
         [OpenRouter / Gemini API]
```

---

## Support

If something doesn't work:

1. Check **ios/README.md** for architecture overview
2. Check **ios/QUICKSTART.md** for simulator testing (easier to debug)
3. Check **troubleshooting** section above
4. Check Python agent logs: `python -m agent.server` (watch for errors)
5. Check Xcode build output: Product → Build Log (⌘⇧K)

---

## What's Next

Once you have iOS working:

**Phase 2b — Android Changes**
The Android app (in `android/` dir) will also be wired to the same WebSocket agent. This means:
- Change prompts once → both platforms update
- Share conversation history across devices
- Consistent mechanics expertise across all platforms
