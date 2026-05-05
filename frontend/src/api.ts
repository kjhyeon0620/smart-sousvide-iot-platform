import type {
  CommandPage,
  CommandType,
  ControlPolicy,
  Device,
  DevicePage,
  DeviceStatus,
  TemperatureSeries
} from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {})
    }
  });

  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`;
    try {
      const body = await response.json();
      message = body.message ?? body.code ?? message;
    } catch {
      // Keep the HTTP status text when the API does not return JSON.
    }
    throw new Error(message);
  }

  return response.json() as Promise<T>;
}

export function getDevices() {
  return request<DevicePage>('/devices?page=0&size=100');
}

export function getDevice(id: number) {
  return request<Device>(`/devices/${id}`);
}

export function updateDeviceEnabled(id: number, enabled: boolean) {
  return request<Device>(`/devices/${id}/enabled`, {
    method: 'PATCH',
    body: JSON.stringify({ enabled })
  });
}

export function getDeviceStatus(id: number) {
  return request<DeviceStatus>(`/devices/${id}/status`);
}

export function getTemperatureSeries(id: number, hours: number) {
  const to = new Date();
  const from = new Date(to.getTime() - hours * 60 * 60 * 1000);
  const params = new URLSearchParams({
    from: from.toISOString(),
    to: to.toISOString(),
    limit: '300'
  });
  return request<TemperatureSeries>(`/devices/${id}/temps?${params.toString()}`);
}

export function getControlPolicy(id: number) {
  return request<ControlPolicy>(`/devices/${id}/control-policy`);
}

export function updateControlPolicy(id: number, targetTemp: number, hysteresis: number) {
  return request<ControlPolicy>(`/devices/${id}/control-policy`, {
    method: 'PATCH',
    body: JSON.stringify({ targetTemp, hysteresis })
  });
}

export function sendCommand(id: number, commandType: CommandType) {
  return request(`/devices/${id}/commands`, {
    method: 'POST',
    body: JSON.stringify({
      commandType,
      idempotencyKey: `dashboard:${id}:${commandType}:${Date.now()}`
    })
  });
}

export function getCommands(id: number) {
  return request<CommandPage>(`/devices/${id}/commands?limit=20`);
}

