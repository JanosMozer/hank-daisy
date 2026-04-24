# MPI Android

Standalone Android scaffold for a dedicated multi-point inspection app built on
top of the glasses-connected HankDaisy Android project.

This folder currently contains:

- A fork of `android/`, still using the Meta Wearables DAT SDK and glasses flow
- [MultipointInspection_SmartGlasses.md](MultipointInspection_SmartGlasses.md),
  the copied product/spec brief for the MPI app

Current scaffold status:

- Project name: `MPIAndroid`
- Android package / application ID:
  `com.meta.wearable.dat.externalsampleapps.mpi`
- App label: `Multipoint Inspection`
- Callback URL scheme: `multipointinspection`

What is intentionally not done yet:

- Replacing the general HankDaisy workflow with a dedicated MPI inspection flow
- Restructuring the screens around RO intake, inspection steps, findings, and
  customer/advisor report generation
- Renaming every internal class or theme symbol copied from the source app

## Prerequisites

- Android Studio
- Android SDK 31+
- Meta Wearables Device Access Toolkit access through GitHub Packages
- A `local.properties` file in `mpi-android/` with DAT credentials and any model keys you want to use

Example `local.properties` entries:

```properties
github_token=
mwdat_application_id=
mwdat_client_token=
openrouter_api_key=
elevenlabs_api_key=
elevenlabs_voice_id=
```

## Next Build Step

Open `mpi-android/` as its own Android Studio project, or run Gradle from this
folder once DAT package access is configured.
