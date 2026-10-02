import type {
  DemoCommandRequest,
  DemoResponse,
  DemoScenarioRequest,
  DemoStateRequest
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

export async function getDemoAvailability() {
  try {
    return await request<{ available: boolean; deviceIdPrefix: string }>('/dev/dashboard-demo');
  } catch (error) {
    if (error instanceof Error && error.message.startsWith('404 ')) {
      return null;
    }
    throw error;
  }
}

export function createDemoScenario(payload: DemoScenarioRequest) {
  return request<DemoResponse>('/dev/dashboard-demo/scenario', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function updateDemoDevice(deviceId: string, payload: DemoStateRequest) {
  return request<DemoResponse>(`/dev/dashboard-demo/devices/${encodeURIComponent(deviceId)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function addDemoCommands(deviceId: string, payload: DemoCommandRequest) {
  return request<DemoResponse>(`/dev/dashboard-demo/devices/${encodeURIComponent(deviceId)}/commands`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function clearDemoData() {
  return request<DemoResponse>('/dev/dashboard-demo', {
    method: 'DELETE'
  });
}
