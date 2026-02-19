/* eslint-disable @typescript-eslint/no-redeclare */
import { Static, Type } from "typebox";

export enum BleState {
  IDLE = "IDLE",
  SCANNING = "SCANNING",
  CONNECTING = "CONNECTING",
  CONNECTED = "CONNECTED",
  ERROR = "ERROR",
}

export enum TcpState {
  IDLE = "IDLE",
  PINGING = "PINGING",
  TRANSFERRING = "TRANSFERRING",
  ERROR = "ERROR",
}

export enum EncryptionState {
  IDLE = "IDLE",
  ENCRYPTING = "ENCRYPTING",
  KEYS_READY = "KEYS_READY",
  ERROR = "ERROR",
}

export enum BridgerType {
  TEXT = "TEXT",
  FILE = "FILE",
  IMAGE = "IMAGE",
}

export enum PorterStatus {
  PENDING = "PENDING",
  ACTIVE = "ACTIVE",
  COMPLETED = "COMPLETED",
  ERROR = "ERROR",
}

export const Status = <T extends string>(EnumType: Record<string, T>) =>
  Type.Object({
    current: Type.Enum(EnumType),
    error: Type.Union([Type.String(), Type.Null()]),
  });

export const BrokerState = Type.Object({
  ble: Status(BleState),
  tcp: Status(TcpState),
  encryption: Status(EncryptionState),
});
export type BrokerState = Static<typeof BrokerState>;

export const Porter = Type.Object({
  id: Type.String(),
  timestamp: Type.Number(),
  isOutgoing: Type.Boolean(),
  status: Type.Enum(PorterStatus),
  name: Type.String(),
  type: Type.Enum(BridgerType),
  totalSize: Type.Number(),
  currentSize: Type.Number(),
  progress: Type.Number(),
  data: Type.Optional(Type.String()),
  error: Type.Optional(Type.String()),
  isTruncated: Type.Boolean(),
});
export type Porter = Static<typeof Porter>;

export const PorterUpdate = Type.Object({
  id: Type.String(),
  progress: Type.Number(),
  currentSize: Type.Number(),
  speed: Type.Optional(Type.Number()), // For future speed calculation
});
export type PorterUpdate = Static<typeof PorterUpdate>;

export type ConnectorModuleEvents = {
  onStateChanged: (payload: BrokerState) => void;
  onHistoryChanged: (payload: { items: Porter[] }) => void;
  onPorterUpdated: (payload: PorterUpdate) => void;
};
