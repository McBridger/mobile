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

const Base = Type.Object({
  id: Type.String(),
  type: Type.String(),
  timestamp: Type.Number(),
  isOutgoing: Type.Boolean(),
});

export const Tiny = Type.Intersect([
  Base,
  Type.Object({
    type: Type.Literal("TINY"),
    value: Type.String(),
  }),
]);
export type Tiny = Static<typeof Tiny>;

export const Blob = Type.Intersect([
  Base,
  Type.Object({
    type: Type.Literal("BLOB"),
    name: Type.String(),
    size: Type.Number(),
    blobType: Type.String(), // FILE, TEXT, IMAGE
  }),
]);
export type Blob = Static<typeof Blob>;

export const Message = Type.Union([Tiny, Blob]);
export type Message = Static<typeof Message>;

export type ConnectorModuleEvents = {
  onReceived: (payload: Message) => void;
  onStateChanged: (payload: BrokerState) => void;
};
