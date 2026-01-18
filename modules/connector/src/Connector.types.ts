/* eslint-disable @typescript-eslint/no-redeclare */
import { Static, Type } from "typebox";

export enum STATUS {
  IDLE = "IDLE",
  ENCRYPTING = "ENCRYPTING",
  KEYS_READY = "KEYS_READY",
  TRANSPORT_INITIALIZING = "TRANSPORT_INITIALIZING",
  READY = "READY",
  DISCOVERING = "DISCOVERING",
  CONNECTING = "CONNECTING",
  CONNECTED = "CONNECTED",
  DISCONNECTED = "DISCONNECTED",
  ERROR = "ERROR",
}

const Base = Type.Object({
  id: Type.String(),
  type: Type.String(),
  address: Type.Optional(Type.String()),
  timestamp: Type.Number(),
});

export const Clipboard = Type.Intersect([
  Base,
  Type.Object({
    type: Type.Literal("CLIPBOARD"),
    value: Type.String(),
  })
])
export type Clipboard = Static<typeof Clipboard>;

export const File = Type.Intersect([
  Base,
  Type.Object({
    type: Type.Literal("FILE_URL"),
    name: Type.String(),
    size: Type.String(),
    url: Type.String(),
  })
])
export type File = Static<typeof File>;

export const Message = Type.Union([Clipboard, File]);
export type Message = Static<typeof Message>;

export type ConnectorModuleEvents = {
  onReceived: (payload: Message) => void;
  onStateChanged: (payload: { status: `${STATUS}` }) => void;
};