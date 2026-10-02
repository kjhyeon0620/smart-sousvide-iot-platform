import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Database, Loader2, Plus, RefreshCw, SlidersHorizontal, Trash2 } from 'lucide-react';
import {
  addDemoCommands,
  clearDemoData,
  createDemoScenario,
  getDemoAvailability,
  updateDemoDevice
} from './demoApi';
import type { DemoDevice, DemoResponse, DemoScenario } from './types';

const scenarios: DemoScenario[] = ['mixed', 'heating', 'holding', 'offline', 'command-failure'];

export function DemoDataPanel({ onChanged }: { onChanged: () => Promise<void> }) {
  const [available, setAvailable] = useState<boolean | null>(null);
  const [count, setCount] = useState(5);
  const [scenario, setScenario] = useState<DemoScenario>('mixed');
  const [baseTargetTemp, setBaseTargetTemp] = useState(64.5);
  const [deviceId, setDeviceId] = useState('SV-DEMO-001');
  const [online, setOnline] = useState(true);
  const [enabled, setEnabled] = useState(true);
  const [temp, setTemp] = useState(61.2);
  const [targetTemp, setTargetTemp] = useState(64.5);
  const [state, setState] = useState<'HEATING' | 'HOLDING' | 'OFF'>('HEATING');
  const [response, setResponse] = useState<DemoResponse | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    getDemoAvailability()
      .then((result) => {
        if (mounted) {
          setAvailable(Boolean(result?.available));
        }
      })
      .catch(() => {
        if (mounted) {
          setAvailable(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, []);

  const demoDevices = useMemo(() => response?.devices ?? [], [response]);

  if (!available) {
    return null;
  }

  async function run(action: string, work: () => Promise<DemoResponse>) {
    setBusy(action);
    setError(null);
    setMessage(null);
    try {
      const result = await work();
      setResponse(result);
      const first = result.devices[0];
      if (first) {
        applyDevice(first);
      }
      setMessage(`${result.createdDevices} devices, ${result.temperaturePoints} telemetry points, ${result.commands} commands`);
      await onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Demo action failed');
    } finally {
      setBusy(null);
    }
  }

  function applyDevice(device: DemoDevice) {
    setDeviceId(device.deviceId);
    setOnline(device.online);
    setEnabled(device.enabled);
    setTemp(Number(device.temp));
    setTargetTemp(Number(device.targetTemp));
    setState(device.state);
  }

  return (
    <details className="demo-panel">
      <summary>
        <span>
          <Database size={18} />
          Demo data
        </span>
        {available === null && <Loader2 size={16} className="spin" />}
      </summary>

      <div className="demo-body">
        {error && (
          <div className="inline-alert">
            <AlertTriangle size={16} />
            {error}
          </div>
        )}
        {message && <div className="inline-notice">{message}</div>}

        <div className="demo-grid">
          <label>
            Count
            <input type="number" min="1" max="20" value={count} onChange={(event) => setCount(Number(event.target.value))} />
          </label>
          <label>
            Scenario
            <select value={scenario} onChange={(event) => setScenario(event.target.value as DemoScenario)}>
              {scenarios.map((item) => (
                <option key={item} value={item}>{item}</option>
              ))}
            </select>
          </label>
          <label>
            Target temp
            <input type="number" min="1" step="0.1" value={baseTargetTemp} onChange={(event) => setBaseTargetTemp(Number(event.target.value))} />
          </label>
        </div>

        <div className="demo-actions">
          <button
            className="primary-button"
            type="button"
            disabled={!!busy}
            onClick={() => run('scenario', () => createDemoScenario({ count, scenario, baseTargetTemp }))}
          >
            {busy === 'scenario' ? <Loader2 size={16} className="spin" /> : <Plus size={16} />}
            Create scenario
          </button>
          <button
            className="command-button"
            type="button"
            disabled={!!busy}
            onClick={() => run('commands', () => addDemoCommands(deviceId, { statuses: ['PENDING', 'SENT', 'FAILED', 'EXPIRED'] }))}
          >
            {busy === 'commands' ? <Loader2 size={16} className="spin" /> : <RefreshCw size={16} />}
            Add commands
          </button>
          <button
            className="danger-button"
            type="button"
            disabled={!!busy}
            onClick={() => run('clear', clearDemoData)}
          >
            {busy === 'clear' ? <Loader2 size={16} className="spin" /> : <Trash2 size={16} />}
            Clear demo data
          </button>
        </div>

        <div className="demo-edit">
          <label>
            Device
            <input value={deviceId} onChange={(event) => setDeviceId(event.target.value)} />
          </label>
          <label>
            Temp
            <input type="number" step="0.1" value={temp} onChange={(event) => setTemp(Number(event.target.value))} />
          </label>
          <label>
            Target
            <input type="number" step="0.1" value={targetTemp} onChange={(event) => setTargetTemp(Number(event.target.value))} />
          </label>
          <label>
            State
            <select value={state} onChange={(event) => setState(event.target.value as 'HEATING' | 'HOLDING' | 'OFF')}>
              <option value="HEATING">HEATING</option>
              <option value="HOLDING">HOLDING</option>
              <option value="OFF">OFF</option>
            </select>
          </label>
          <label className="check-row">
            <input type="checkbox" checked={online} onChange={(event) => setOnline(event.target.checked)} />
            Online
          </label>
          <label className="check-row">
            <input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />
            Enabled
          </label>
          <button
            className="command-button"
            type="button"
            disabled={!!busy}
            onClick={() => run('update', () => updateDemoDevice(deviceId, { online, enabled, temp, targetTemp, state }))}
          >
            {busy === 'update' ? <Loader2 size={16} className="spin" /> : <SlidersHorizontal size={16} />}
            Update state
          </button>
        </div>

        {demoDevices.length > 0 && (
          <div className="demo-device-row" aria-label="Demo devices">
            {demoDevices.map((device) => (
              <button key={device.deviceId} type="button" onClick={() => applyDevice(device)}>
                <strong>{device.deviceId}</strong>
                <span>{device.online ? 'online' : 'offline'} / {device.state}</span>
              </button>
            ))}
          </div>
        )}

        {response?.warning && <p className="muted">{response.warning}</p>}
      </div>
    </details>
  );
}
