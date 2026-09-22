# TranscriberApp

<p align="center">
  <img src="TranscriberApp_logo.png" width="130" alt="TranscriberApp Logo" />
</p>

Applicazione Android per trascrivere in testo le note vocali ricevute su WhatsApp, Telegram o file audio locali tramite il menu "Condividi" di sistema.

## Cosa c'è nell'app

- **Trascrizione diretta da condivisione:** basta selezionare un vocale in chat e condividerlo con TranscriberApp per leggere il testo in un popup compatto senza uscire dalla conversazione.
- **Scelta della lingua:** rilevamento automatico, Italiano o Inglese.
- **Generazione testo in tempo reale:** visualizzazione progressiva delle parole con barra animata durante l'elaborazione.
- **Player anonimo:** riproduzione audio integrata per ascoltare i vocali senza attivare conferme di ascolto.

## Tecnologie utilizzate e crediti

L'applicazione supporta due motori di trascrizione selezionabili:

- **Google Speech Recognition (Android SpeechRecognizer)**
  Sfrutta il motore di riconoscimento vocale di sistema Android con streaming audio tramite pipe (`ParcelFileDescriptor`).
  *Crediti:* Google LLC.

- **whisper.cpp & OpenAI Whisper**
  Porting nativo C/C++ del modello Whisper per inferenza locale offline su CPU ARM con istruzioni NEON.
  *Crediti:* [Georgi Gerganov (whisper.cpp)](https://github.com/ggerganov/whisper.cpp) e [OpenAI (Whisper)](https://github.com/openai/whisper).

- **FFmpeg**
  Utilizzato per convertire ed estrarre l'audio dai formati usati dalle app di messaggistica (OPUS, OGG, M4A, AAC) in PCM 16kHz mono.
  *Crediti:* Progetto FFmpeg e ffmpeg-kit.

## Download

Gli APK compilati automaticamente sono disponibili nella scheda [Releases](https://github.com/fra06083/transcriber/releases).
