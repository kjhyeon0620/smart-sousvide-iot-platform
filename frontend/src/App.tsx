import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Flame,
  Loader2,
  Pause,
  Power,
  RefreshCw,
  Save,
  Thermometer,
  Wifi,
  WifiOff
} from 'lucide-react';
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts';
import {
  getCommands,
  getControlPolicy,
  getDevice,
  getDevices,
  getDeviceStatus,
  getTemperatureSeries,
  sendCommand,
  updateControlPolicy,
  updateDeviceEnabled
} from './api';
import type {
  CommandPage,
  CommandType,
  ControlPolicy,
  Device,
  DevicePage,
  DeviceStatus,
  TemperaturePoint,
  TemperatureSeries
} from './types';
import { commandStatusTone, displayName, formatDateTime, formatTemp, relativeTime, stateLabel } from './utils';

type Filter = 'all' | 'online' | 'offline' | 'disabled' | 'heating';
type RangeHours = 1 | 6 | 24;

interface LoadState<T> {
  loading: boolean;
  error: string | null;
  data: T | null;
}

const emptyState = <T,>(): LoadState<T> => ({ loading: true, error: null, data: null });

export function App() {
  const [selectedId, setSelectedId] = useState<number | null>(null);

  return (
    <main className="app-shell">
      {selectedId === null ? (
        <DeviceListPage onSelect={setSelectedId} />
      ) : (
        <DeviceDetailPage deviceId={selectedId} onBack={() => setSelectedId(null)} />
      )}
    </main>
  );
}

function DeviceListPage({ onSelect }: { onSelect: (id: number) => void }) {
  const [devices, setDevices] = useState<LoadState<DevicePage>>(emptyState);
  const [statuses, setStatuses] = useState<Record<number, DeviceStatus>>({});
  const [filter, setFilter] = useState<Filter>('all');
  const [refreshing, setRefreshing] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const loadDevices = useCallback(async (quiet = false) => {
    if (quiet) {
      setRefreshing(true);
    } else {
      setDevices((prev) => ({ ...prev, loading: true, error: null }));
    }

    try {
      const page = await getDevices();
      setDevices({ loading: false, error: null, data: page });
      const statusPairs = await Promise.allSettled(
        page.items.map(async (device) => [device.id, await getDeviceStatus(device.id)] as const)
      );
      setStatuses((current) => {
        const next = { ...current };
        for (const result of statusPairs) {
          if (result.status === 'fulfilled') {
            next[result.value[0]] = result.value[1];
          }
        }
        return next;
      });
    } catch (error) {
      setDevices({ loading: false, error: error instanceof Error ? error.message : 'Failed to load devices', data: null });
    } finally {
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    loadDevices();
    const id = window.setInterval(() => loadDevices(true), 30000);
    return () => window.clearInterval(id);
  }, [loadDevices]);

  const items = devices.data?.items ?? [];
  const summary = useMemo(() => {
    const visibleStatuses = items.map((device) => statuses[device.id]).filter(Boolean);
    return {
      total: devices.data?.totalElements ?? items.length,
      online: visibleStatuses.filter((status) => status.online).length,
      offline: visibleStatuses.filter((status) => !status.online).length,
      heating: visibleStatuses.filter((status) => status.latestState === 'HEATING').length
    };
  }, [devices.data?.totalElements, items, statuses]);

  const filtered = items.filter((device) => {
    const status = statuses[device.id];
    if (filter === 'online') {
      return status?.online;
    }
    if (filter === 'offline') {
      return status && !status.online;
    }
    if (filter === 'disabled') {
      return status ? !status.enabled : !device.enabled;
    }
    if (filter === 'heating') {
      return status?.latestState === 'HEATING';
    }
    return true;
  });

  async function toggleEnabled(device: Device, enabled: boolean) {
    setActionError(null);
    try {
      await updateDeviceEnabled(device.id, enabled);
      const status = await getDeviceStatus(device.id);
      setStatuses((current) => ({ ...current, [device.id]: status }));
      await loadDevices(true);
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to update device');
    }
  }

  return (
    <section className="page">
      <header className="topbar">
        <div>
          <p className="eyebrow">Sousvide dashboard</p>
          <h1>My devices</h1>
        </div>
        <button className="icon-button" type="button" onClick={() => loadDevices(true)} aria-label="Refresh devices">
          <RefreshCw size={18} className={refreshing ? 'spin' : ''} />
        </button>
      </header>

      {actionError && <InlineAlert message={actionError} />}

      <div className="summary-grid" aria-label="Device summary">
        <SummaryTile icon={<Activity size={18} />} label="Total" value={summary.total} />
        <SummaryTile icon={<Wifi size={18} />} label="Online" value={summary.online} tone="good" />
        <SummaryTile icon={<WifiOff size={18} />} label="Offline" value={summary.offline} tone="bad" />
        <SummaryTile icon={<Flame size={18} />} label="Heating" value={summary.heating} tone="warn" />
      </div>

      <div className="filter-row" role="tablist" aria-label="Device filters">
        {(['all', 'online', 'offline', 'disabled', 'heating'] as Filter[]).map((value) => (
          <button
            key={value}
            className={filter === value ? 'filter active' : 'filter'}
            type="button"
            onClick={() => setFilter(value)}
          >
            {value}
          </button>
        ))}
      </div>

      {devices.loading && <LoadingPanel label="Loading devices" />}
      {devices.error && <ErrorPanel message={devices.error} onRetry={() => loadDevices()} />}
      {!devices.loading && !devices.error && items.length === 0 && (
        <EmptyPanel title="No devices yet" body="Registered sousvide devices will appear here after they are added through the API." />
      )}
      {!devices.loading && !devices.error && items.length > 0 && filtered.length === 0 && (
        <EmptyPanel title="No devices match this filter" body="Change the filter or refresh the list to check the latest device state." />
      )}

      <div className="device-grid">
        {filtered.map((device) => (
          <DeviceCard
            key={device.id}
            device={device}
            status={statuses[device.id]}
            onOpen={() => onSelect(device.id)}
            onToggle={(enabled) => toggleEnabled(device, enabled)}
          />
        ))}
      </div>
    </section>
  );
}

function DeviceCard({
  device,
  status,
  onOpen,
  onToggle
}: {
  device: Device;
  status?: DeviceStatus;
  onOpen: () => void;
  onToggle: (enabled: boolean) => void;
}) {
  const online = status?.online ?? false;
  const enabled = status?.enabled ?? device.enabled;

  return (
    <article className="device-card">
      <button className="card-main" type="button" onClick={onOpen}>
        <div className="card-title-row">
          <div>
            <h2>{displayName(device)}</h2>
            <p>{device.deviceId}</p>
          </div>
          <StatusBadge online={online} enabled={enabled} />
        </div>
        <div className="metric-row">
          <Metric label="Now" value={formatTemp(status?.latestTemp)} />
          <Metric label="Target" value={formatTemp(status?.latestTargetTemp)} />
          <Metric label="State" value={stateLabel(status)} />
        </div>
        <p className={online ? 'last-seen' : 'last-seen offline'}>{relativeTime(status?.lastSeenAt)}</p>
      </button>
      <div className="card-actions">
        <Toggle checked={enabled} onChange={onToggle} label="Enabled" />
      </div>
    </article>
  );
}

function DeviceDetailPage({ deviceId, onBack }: { deviceId: number; onBack: () => void }) {
  const [range, setRange] = useState<RangeHours>(1);
  const [device, setDevice] = useState<LoadState<Device>>(emptyState);
  const [status, setStatus] = useState<LoadState<DeviceStatus>>(emptyState);
  const [temps, setTemps] = useState<LoadState<TemperatureSeries>>(emptyState);
  const [policy, setPolicy] = useState<LoadState<ControlPolicy>>(emptyState);
  const [commands, setCommands] = useState<LoadState<CommandPage>>(emptyState);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const loadDetail = useCallback(async () => {
    setDevice((prev) => ({ ...prev, loading: true, error: null }));
    setStatus((prev) => ({ ...prev, loading: true, error: null }));
    setPolicy((prev) => ({ ...prev, loading: true, error: null }));
    setCommands((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const [deviceResponse, statusResponse, policyResponse, commandsResponse] = await Promise.all([
        getDevice(deviceId),
        getDeviceStatus(deviceId),
        getControlPolicy(deviceId),
        getCommands(deviceId)
      ]);
      setDevice({ loading: false, error: null, data: deviceResponse });
      setStatus({ loading: false, error: null, data: statusResponse });
      setPolicy({ loading: false, error: null, data: policyResponse });
      setCommands({ loading: false, error: null, data: commandsResponse });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to load device';
      setDevice((prev) => ({ ...prev, loading: false, error: message }));
      setStatus((prev) => ({ ...prev, loading: false, error: message }));
      setPolicy((prev) => ({ ...prev, loading: false, error: message }));
      setCommands((prev) => ({ ...prev, loading: false, error: message }));
    }
  }, [deviceId]);

  const loadTemps = useCallback(async () => {
    setTemps((prev) => ({ ...prev, loading: true, error: null }));
    try {
      setTemps({ loading: false, error: null, data: await getTemperatureSeries(deviceId, range) });
    } catch (error) {
      setTemps({ loading: false, error: error instanceof Error ? error.message : 'Failed to load temperatures', data: null });
    }
  }, [deviceId, range]);

  useEffect(() => {
    loadDetail();
    const id = window.setInterval(async () => {
      try {
        const [statusResponse, commandsResponse] = await Promise.all([getDeviceStatus(deviceId), getCommands(deviceId)]);
        setStatus({ loading: false, error: null, data: statusResponse });
        setCommands({ loading: false, error: null, data: commandsResponse });
      } catch (error) {
        setStatus((prev) => ({
          ...prev,
          loading: false,
          error: error instanceof Error ? error.message : 'Failed to refresh status'
        }));
      }
    }, 5000);
    return () => window.clearInterval(id);
  }, [deviceId, loadDetail]);

  useEffect(() => {
    loadTemps();
    const id = window.setInterval(loadTemps, 30000);
    return () => window.clearInterval(id);
  }, [loadTemps]);

  async function toggleEnabled(enabled: boolean) {
    setActionError(null);
    setActionMessage(null);
    try {
      await updateDeviceEnabled(deviceId, enabled);
      setStatus({ loading: false, error: null, data: await getDeviceStatus(deviceId) });
      setActionMessage(enabled ? 'Device enabled' : 'Device disabled');
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to update enabled state');
    }
  }

  async function savePolicy(targetTemp: number, hysteresis: number) {
    setActionError(null);
    setActionMessage(null);
    try {
      const response = await updateControlPolicy(deviceId, targetTemp, hysteresis);
      setPolicy({ loading: false, error: null, data: response });
      setActionMessage('Control policy saved');
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to save control policy');
    }
  }

  async function issueCommand(commandType: CommandType) {
    setActionError(null);
    setActionMessage(null);
    try {
      await sendCommand(deviceId, commandType);
      setCommands({ loading: false, error: null, data: await getCommands(deviceId) });
      setActionMessage(`${commandType} command requested`);
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to send command');
    }
  }

  const currentStatus = status.data;
  const currentDevice = device.data ?? currentStatus;

  return (
    <section className="page">
      <header className="detail-header">
        <button className="icon-button" type="button" onClick={onBack} aria-label="Back to devices">
          <ArrowLeft size={18} />
        </button>
        <div className="detail-title">
          <p className="eyebrow">Device detail</p>
          <h1>{currentDevice ? displayName(currentDevice) : 'Device'}</h1>
          <p>{currentDevice?.deviceId ?? `#${deviceId}`}</p>
        </div>
        <div className="detail-actions">
          {currentStatus && <StatusBadge online={currentStatus.online} enabled={currentStatus.enabled} />}
          <Toggle checked={currentStatus?.enabled ?? false} onChange={toggleEnabled} label="Enabled" disabled={!currentStatus} />
        </div>
      </header>

      {actionError && <InlineAlert message={actionError} />}
      {actionMessage && <InlineNotice message={actionMessage} />}
      {device.error && <ErrorPanel message={device.error} onRetry={loadDetail} />}

      {currentStatus && !currentStatus.online && (
        <div className="offline-band">
          <WifiOff size={18} />
          Device is offline. State and command results may be stale until telemetry resumes.
        </div>
      )}

      <div className="detail-grid">
        <section className="panel state-panel">
          <div className="panel-heading">
            <h2>Current state</h2>
            {status.loading && <Loader2 size={16} className="spin" />}
          </div>
          {status.loading && !currentStatus ? (
            <LoadingPanel label="Loading state" compact />
          ) : currentStatus ? (
            <div className="state-grid">
              <BigMetric icon={<Thermometer size={20} />} label="Current" value={formatTemp(currentStatus.latestTemp)} />
              <BigMetric icon={<Flame size={20} />} label="Target" value={formatTemp(currentStatus.latestTargetTemp)} />
              <BigMetric icon={<Activity size={20} />} label="State" value={stateLabel(currentStatus)} />
              <BigMetric icon={<Wifi size={20} />} label="Last seen" value={relativeTime(currentStatus.lastSeenAt)} />
            </div>
          ) : (
            <EmptyPanel title="No status yet" body="The device exists, but no telemetry snapshot is available." compact />
          )}
        </section>

        <section className="panel chart-panel">
          <div className="panel-heading with-actions">
            <h2>Temperature</h2>
            <div className="range-selector">
              {([1, 6, 24] as RangeHours[]).map((value) => (
                <button
                  key={value}
                  type="button"
                  className={range === value ? 'range active' : 'range'}
                  onClick={() => setRange(value)}
                >
                  {value}h
                </button>
              ))}
            </div>
          </div>
          {temps.loading && !temps.data && <LoadingPanel label="Loading chart" compact />}
          {temps.error && <ErrorPanel message={temps.error} onRetry={loadTemps} compact />}
          {!temps.loading && !temps.error && temps.data?.items.length === 0 && (
            <EmptyPanel title="No telemetry in range" body="Try a longer range or wait for the device to publish temperatures." compact />
          )}
          {temps.data && temps.data.items.length > 0 && <TemperatureChart points={temps.data.items} />}
        </section>

        <ControlPolicyPanel policy={policy} onSave={savePolicy} />

        <CommandPanel status={currentStatus} commands={commands} onSend={issueCommand} onRefresh={async () => {
          setCommands({ loading: false, error: null, data: await getCommands(deviceId) });
        }} />
      </div>
    </section>
  );
}

function TemperatureChart({ points }: { points: TemperaturePoint[] }) {
  const data = points.map((point) => ({
    time: formatDateTime(point.occurredAt),
    temp: point.temp,
    target: point.targetTemp
  }));

  return (
    <div className="chart-frame">
      <ResponsiveContainer width="100%" height={300}>
        <LineChart data={data} margin={{ top: 12, right: 16, bottom: 8, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#d9e2df" />
          <XAxis dataKey="time" minTickGap={28} tick={{ fontSize: 12 }} />
          <YAxis width={42} tick={{ fontSize: 12 }} domain={['dataMin - 1', 'dataMax + 1']} />
          <Tooltip />
          <Line type="monotone" dataKey="temp" stroke="#0b7f79" strokeWidth={2.5} dot={false} name="Temp" />
          <Line type="monotone" dataKey="target" stroke="#d66b1f" strokeWidth={2} dot={false} name="Target" />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function ControlPolicyPanel({
  policy,
  onSave
}: {
  policy: LoadState<ControlPolicy>;
  onSave: (targetTemp: number, hysteresis: number) => Promise<void>;
}) {
  const [targetTemp, setTargetTemp] = useState('');
  const [hysteresis, setHysteresis] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (policy.data) {
      setTargetTemp(String(policy.data.targetTemp));
      setHysteresis(String(policy.data.hysteresis));
    }
  }, [policy.data]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true);
    try {
      await onSave(Number(targetTemp), Number(hysteresis));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="panel">
      <div className="panel-heading">
        <h2>Control policy</h2>
      </div>
      {policy.loading && !policy.data && <LoadingPanel label="Loading policy" compact />}
      {policy.error && <InlineAlert message={policy.error} />}
      <form className="policy-form" onSubmit={submit}>
        <label>
          Target temp
          <input
            type="number"
            min="1"
            step="0.1"
            value={targetTemp}
            onChange={(event) => setTargetTemp(event.target.value)}
            required
          />
        </label>
        <label>
          Hysteresis
          <input
            type="number"
            min="0.1"
            step="0.1"
            value={hysteresis}
            onChange={(event) => setHysteresis(event.target.value)}
            required
          />
        </label>
        <button className="primary-button" type="submit" disabled={saving || !targetTemp || !hysteresis}>
          {saving ? <Loader2 size={16} className="spin" /> : <Save size={16} />}
          Save
        </button>
      </form>
      {policy.data?.updatedAt && <p className="muted">Updated {formatDateTime(policy.data.updatedAt)}</p>}
    </section>
  );
}

function CommandPanel({
  status,
  commands,
  onSend,
  onRefresh
}: {
  status: DeviceStatus | null;
  commands: LoadState<CommandPage>;
  onSend: (command: CommandType) => Promise<void>;
  onRefresh: () => Promise<void>;
}) {
  const [sending, setSending] = useState<CommandType | null>(null);
  const disabledReason = !status ? 'Status unavailable' : !status.enabled ? 'Device disabled' : !status.online ? 'Device offline' : null;
  const delayed = commands.data?.items.some((command) => command.status === 'PENDING' || command.status === 'SENT');
  const failed = commands.data?.items.find((command) => command.status === 'FAILED' || command.status === 'EXPIRED');

  async function click(command: CommandType) {
    setSending(command);
    try {
      await onSend(command);
    } finally {
      setSending(null);
    }
  }

  return (
    <section className="panel command-panel">
      <div className="panel-heading with-actions">
        <h2>Commands</h2>
        <button className="icon-button small" type="button" onClick={onRefresh} aria-label="Refresh command history">
          <RefreshCw size={16} />
        </button>
      </div>
      {disabledReason && <InlineAlert message={`${disabledReason}. Commands are blocked until the device is online and enabled.`} />}
      {delayed && <InlineNotice message="A command is waiting for ACK. It may expire if the device does not respond in time." />}
      {failed && <InlineAlert message={`${failed.commandType} ended as ${failed.status}${failed.errorMessage ? `: ${failed.errorMessage}` : ''}`} />}
      <div className="command-buttons">
        <CommandButton icon={<Flame size={16} />} label="HEAT_ON" disabled={!!disabledReason || !!sending} active={sending === 'HEAT_ON'} onClick={() => click('HEAT_ON')} />
        <CommandButton icon={<Power size={16} />} label="HEAT_OFF" disabled={!!disabledReason || !!sending} active={sending === 'HEAT_OFF'} onClick={() => click('HEAT_OFF')} />
        <CommandButton icon={<Pause size={16} />} label="HOLD" disabled={!!disabledReason || !!sending} active={sending === 'HOLD'} onClick={() => click('HOLD')} />
      </div>
      {commands.loading && !commands.data && <LoadingPanel label="Loading command history" compact />}
      {commands.error && <InlineAlert message={commands.error} />}
      {!commands.loading && !commands.error && commands.data?.items.length === 0 && (
        <EmptyPanel title="No commands yet" body="Manual and automatic commands will appear here." compact />
      )}
      {commands.data && commands.data.items.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Command</th>
                <th>Status</th>
                <th>Requested</th>
                <th>Sent</th>
              </tr>
            </thead>
            <tbody>
              {commands.data.items.map((command) => (
                <tr key={command.commandId}>
                  <td>{command.commandType}</td>
                  <td>
                    <span className={`pill ${commandStatusTone(command.status)}`}>{command.status}</span>
                  </td>
                  <td>{formatDateTime(command.requestedAt)}</td>
                  <td>{formatDateTime(command.sentAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function CommandButton({
  icon,
  label,
  disabled,
  active,
  onClick
}: {
  icon: React.ReactNode;
  label: CommandType;
  disabled: boolean;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button className="command-button" type="button" disabled={disabled} onClick={onClick}>
      {active ? <Loader2 size={16} className="spin" /> : icon}
      {label}
    </button>
  );
}

function SummaryTile({ icon, label, value, tone = 'neutral' }: { icon: React.ReactNode; label: string; value: number; tone?: string }) {
  return (
    <div className={`summary-tile ${tone}`}>
      {icon}
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function BigMetric({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="big-metric">
      {icon}
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function StatusBadge({ online, enabled }: { online: boolean; enabled: boolean }) {
  if (!enabled) {
    return <span className="status-badge muted-badge">Disabled</span>;
  }
  return <span className={online ? 'status-badge online' : 'status-badge offline'}>{online ? 'Online' : 'Offline'}</span>;
}

function Toggle({
  checked,
  onChange,
  label,
  disabled = false
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  disabled?: boolean;
}) {
  return (
    <label className="toggle">
      <span>{label}</span>
      <input type="checkbox" checked={checked} disabled={disabled} onChange={(event) => onChange(event.target.checked)} />
      <span className="switch" />
    </label>
  );
}

function LoadingPanel({ label, compact = false }: { label: string; compact?: boolean }) {
  return (
    <div className={compact ? 'state-block compact' : 'state-block'}>
      <Loader2 size={18} className="spin" />
      {label}
    </div>
  );
}

function ErrorPanel({ message, onRetry, compact = false }: { message: string; onRetry: () => void; compact?: boolean }) {
  return (
    <div className={compact ? 'state-block error compact' : 'state-block error'}>
      <AlertTriangle size={18} />
      <span>{message}</span>
      <button type="button" onClick={onRetry}>Retry</button>
    </div>
  );
}

function EmptyPanel({ title, body, compact = false }: { title: string; body: string; compact?: boolean }) {
  return (
    <div className={compact ? 'state-block empty compact' : 'state-block empty'}>
      <CheckCircle2 size={18} />
      <div>
        <strong>{title}</strong>
        <p>{body}</p>
      </div>
    </div>
  );
}

function InlineAlert({ message }: { message: string }) {
  return (
    <div className="inline-alert">
      <AlertTriangle size={16} />
      {message}
    </div>
  );
}

function InlineNotice({ message }: { message: string }) {
  return (
    <div className="inline-notice">
      <CheckCircle2 size={16} />
      {message}
    </div>
  );
}

