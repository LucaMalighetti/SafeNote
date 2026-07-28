# SafeNote - Versione iOS

Questa cartella è predisposta per ospitare il progetto iOS tramite **Compose Multiplatform**.

## Requisiti
- Un Mac con macOS.
- Xcode installato.
- Android Studio con il plugin "Kotlin Multiplatform Mobile".

## Passi per la migrazione
1. **Sposta la Logica**: Sposta i modelli dati (`SharedPhoto`, `SchoolClass`) nel modulo `commonMain` (da creare).
2. **Interfaccia Comune**: La UI di Compose (Gallery, LoginScreen) può essere condivisa quasi al 100%.
3. **Piattaforme Specifiche**:
   - **Android**: Continua a usare CameraX e ML Kit.
   - **iOS**: Dovrai implementare una `CameraView` usando `UIKitView` che richiama `AVFoundation`.

## Perché una sottocartella?
In un progetto Multiplatform, la struttura diventa:
- `/composeApp`: Codice UI condiviso.
- `/iosApp`: Progetto nativo Xcode che "ospita" la UI condivisa.
- `/app` (o `androidApp`): Il tuo progetto Android attuale.

Se decidi di procedere con Firebase, questa sarà la chiave per far comunicare i due mondi.
