# HankDaisy No Glasses

Android clone of the HankDaisy app that uses the phone camera instead of Meta glasses.

Use this folder when you want to run HankDaisy without DAT registration, Meta AI pairing, or physical glasses. The original glasses-connected Android app remains in `../android`.

## What This Build Does

- Uses the Android system camera as Hank's live visual context
- Keeps the same Hank voice loop: wake/listen, analyze, speak, barge-in, and follow-up
- Keeps the same chats, repair orders, tips, settings, summaries, exports, and reports
- Captures photos from the current phone-camera frame
- Does not depend on Meta Wearables DAT, Meta AI, Ray-Ban pairing, or MockDeviceKit

## Build

```bash
cd android-no-glasses
ANDROID_HOME=/Users/lhd/Library/Android/sdk \
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
android-no-glasses/app/build/outputs/apk/debug/app-debug.apk
```

## Local Config

Create `android-no-glasses/local.properties` if you want model/TTS calls:

```properties
openrouter_api_key=
elevenlabs_api_key=
elevenlabs_voice_id=
```

Without `openrouter_api_key`, Hank will still launch but model analysis returns a configuration message.
