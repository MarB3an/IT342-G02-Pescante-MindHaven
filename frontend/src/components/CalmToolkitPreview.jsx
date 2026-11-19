import { useEffect, useState } from 'react';

const PHASES = [
  { label: 'Inhale', seconds: 4, expand: true },
  { label: 'Hold', seconds: 4, expand: true },
  { label: 'Exhale', seconds: 4, expand: false },
  { label: 'Hold', seconds: 4, expand: false },
];

export default function CalmToolkitPreview() {
  const [running, setRunning] = useState(false);
  const [phaseIdx, setPhaseIdx] = useState(0);
  const [countdown, setCountdown] = useState(4);

  useEffect(() => {
    if (!running) return undefined;
    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev > 1) return prev - 1;
        setPhaseIdx((p) => (p + 1) % PHASES.length);
        return 4;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [running]);

  const current = PHASES[phaseIdx];

  function toggle() {
    if (running) {
      setRunning(false);
      setPhaseIdx(0);
      setCountdown(4);
    } else {
      setRunning(true);
    }
  }

  return (
    <div className="breathing-box">
      <div className={`breath-circle ${running && current.expand ? 'expand' : ''}`}>
        <span style={{ fontSize: '0.72rem', fontWeight: 500 }}>
          {running ? current.label : 'Ready'}
        </span>
        <span style={{ fontSize: '1.2rem' }}>{running ? `${countdown}s` : '4-4-4-4'}</span>
      </div>
      <div style={{ flex: 1 }}>
        <div style={{ fontWeight: 700, marginBottom: '0.2rem' }}>
          Guided Box Breathing (4-4-4-4)
        </div>
        <div style={{ fontSize: '0.84rem', color: 'var(--text-muted)', marginBottom: '0.65rem' }}>
          Client-side self-regulation preview (SRS FR-023). Runs locally without network latency.
        </div>
        <div className="actions-row">
          <button type="button" className="btn btn-primary btn-sm" onClick={toggle}>
            {running ? 'Stop Exercise' : 'Start Box Breathing'}
          </button>
          <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
            NCMH Crisis Hotline: <strong>1553</strong> (Luzon) / <strong>0917-899-8727</strong>
          </span>
        </div>
      </div>
    </div>
  );
}
