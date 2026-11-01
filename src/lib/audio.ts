// Clean harmonic chime + Voice Announcement for Hospital Smart TV

let audioCtx: AudioContext | null = null;

export function playHospitalChime(): void {
  try {
    const AudioContextClass =
      window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextClass) return;

    if (!audioCtx) {
      audioCtx = new AudioContextClass();
    }
    if (audioCtx.state === 'suspended') {
      audioCtx.resume();
    }

    const now = audioCtx.currentTime;

    // Two-tone warm hospital ding-dong (E5 -> C5 major third interval)
    const playTone = (freq: number, startTime: number, duration: number, gainPeak: number) => {
      if (!audioCtx) return;
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();

      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, startTime);

      gain.gain.setValueAtTime(0.0001, startTime);
      gain.gain.exponentialRampToValueAtTime(gainPeak, startTime + 0.03);
      gain.gain.exponentialRampToValueAtTime(0.0001, startTime + duration);

      osc.connect(gain);
      gain.connect(audioCtx.destination);

      osc.start(startTime);
      osc.stop(startTime + duration);
    };

    playTone(659.25, now, 0.55, 0.22); // E5
    playTone(523.25, now + 0.34, 0.85, 0.25); // C5
  } catch {
    // Ignore audio context restrictions if user hasn't interacted yet
  }
}

export function announceTicketVoice(displayCode: string, counterName: string, isReturnFromExam = false): void {
  playHospitalChime();

  if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
    return;
  }

  try {
    window.speechSynthesis.cancel();
    const formattedCode = displayCode.replace('-', ' ');
    const returnNote = isReturnFromExam ? ', retorno de exame, ' : ', ';
    const text = `Senha ${formattedCode}${returnNote}por favor dirigir-se ao ${counterName}.`;

    setTimeout(() => {
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'pt-PT';
      utterance.rate = 0.94;
      utterance.pitch = 1.0;

      const voices = window.speechSynthesis.getVoices();
      const ptVoice = voices.find(
        (v) => v.lang.toLowerCase().includes('pt-pt') || v.lang.toLowerCase().includes('pt-br') || v.lang.toLowerCase().includes('pt')
      );
      if (ptVoice) {
        utterance.voice = ptVoice;
      }

      window.speechSynthesis.speak(utterance);
    }, 650);
  } catch {
    // Speech synthesis fallback
  }
}
