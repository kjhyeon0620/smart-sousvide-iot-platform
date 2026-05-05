export type DeviceState = 'IDLE' | 'HEATING' | 'HOLDING' | 'COOLING' | 'ERROR' | string;
export type CommandType = 'HEAT_ON' | 'HEAT_OFF' | 'HOLD';
export type CommandStatus = 'PENDING' | 'SENT' | 'ACKED' | 'EXPIRED' | 'FAILED' | string;

export interface Device {
  id: number;
  deviceId: string;
  name: string | null;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface DevicePage {
  items: Device[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface DeviceStatus extends Device {
  lastSeenAt: string | null;
  online: boolean;
  latestTemp: number | null;
  latestTargetTemp: number | null;
  latestState: DeviceState | null;
  latestOccurredAt: string | null;
}

export interface TemperaturePoint {
  occurredAt: string;
  temp: number | null;
  targetTemp: number | null;
  state: DeviceState | null;
}

export interface TemperatureSeries {
  devicePk: number;
  deviceId: string;
  from: string;
  to: string;
  limit: number;
  items: TemperaturePoint[];
}

export interface ControlPolicy {
  devicePk: number;
  deviceId: string;
  targetTemp: number;
  hysteresis: number;
  updatedAt: string | null;
}

export interface DeviceCommand {
  commandId: number;
  devicePk: number;
  deviceId: string;
  commandType: CommandType;
  status: CommandStatus;
  topic: string | null;
  payload: string | null;
  requestedAt: string;
  sentAt: string | null;
  errorMessage: string | null;
}

export interface CommandPage {
  devicePk: number;
  deviceId: string;
  limit: number;
  items: DeviceCommand[];
}

