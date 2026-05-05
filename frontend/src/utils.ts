import type { CommandStatus, DeviceStatus } from './types';

export function displayName(device: { name: string | null; deviceId: string }) {
  return device.name?.trim() || device.deviceId;
}

export function formatTemp(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return '--';
  }
  return `${Number(value).toFixed(1)} C`;
}

export function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return 'No data';
  }
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}

export function relativeTime(value: string | null | undefined) {
  if (!value) {
    return 'No telemetry';
  }
  const diffMs = Date.now() - new Date(value).getTime();
  const diffMinutes = Math.max(0, Math.round(diffMs / 60000));
  if (diffMinutes < 1) {
    return 'Just now';
  }
  if (diffMinutes < 60) {
    return `${diffMinutes}m ago`;
  }
  const hours = Math.round(diffMinutes / 60);
  if (hours < 48) {
    return `${hours}h ago`;
  }
  return formatDateTime(value);
}

export function stateLabel(status: DeviceStatus | null | undefined) {
  if (!status) {
    return 'Unknown';
  }
  if (!status.enabled) {
    return 'Disabled';
  }
  if (!status.online) {
    return 'Offline';
  }
  return status.latestState ?? 'Online';
}

export function commandStatusTone(status: CommandStatus) {
  if (status === 'ACKED') {
    return 'good';
  }
  if (status === 'FAILED' || status === 'EXPIRED') {
    return 'bad';
  }
  if (status === 'PENDING' || status === 'SENT') {
    return 'warn';
  }
  return 'neutral';
}

