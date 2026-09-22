# TranscriberApp 🎙️

<p align="center">
  <img src="TranscriberApp_logo.png" width="160" alt="TranscriberApp Logo" />
</p>

App Android per la trascrizione rapida e anonima delle note vocali (WhatsApp, Telegram e file audio), con supporto sia al motore integrato di sistema **Google Speech On-Device** sia alla rete neurale autonoma **Whisper Turbo** (100% offline).

---

## 🌟 Caratteristiche Principali

* **Doppio Motore di Riconoscimento Vocale:**
  * **Google Speech On-Device:** Riconoscimento vocale ultra-rapido e leggero tramite i servizi di sistema di Google.
  * **Whisper Turbo:** Modello neurale Whisper ottimizzato con accelerazione multithreading, zero consumo dati e 100% privacy.
* **Animazioni Stile Google & Gemini:**
  * Barra di avanzamento fluida `GoogleShimmerBar` con gradiente multicolore continuo.
  * Onde animate e punti di ascolto Google Assistant.
  * **Generazione progressiva delle parole:** Le parole appaiono in tempo reale parola per parola con cursore pulsante `▍`, eliminando scatti e pop-in improvvisi.
* **Integrazione "Condividi" di Android:**
  * Condividi qualsiasi vocale da WhatsApp o Telegram per aprirlo all'istante in un popup compatto senza lasciare la chat.
* **Ascolto Anonimo:**
  * Ascolta le note vocali con il lettore integrato senza inviare conferme di lettura.

---

## 🚀 Download APK

Scarica l'ultimo file APK pre-compilato direttamente dalla sezione [Releases](https://github.com/fra06083/transcriber/releases) di questo repository.

---

## 🛠️ Compilazione da Sorgenti

Per compilare il progetto in locale:

```bash
git clone git@github.com:fra06083/transcriber.git
cd transcriber
./gradlew assembleDebug
```

L'APK compilato si troverà in `app/build/outputs/apk/debug/app-debug.apk`.
