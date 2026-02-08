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
  address: Type.Optional(Type.Union([Type.String(), Type.Null()])),
  timestamp: Type.Number(),
});

export const Tiny = Type.Intersect([
  Base,
  Type.Object({
    type: Type.Literal("TINY"),
    value: Type.String(),
  })
])
export type Tiny = Static<typeof Tiny>;

export const Intro = Type.Intersect([
  Base,
  Type.Object({
    type: Type.Literal("INTRO"),
    name: Type.String(),
    ip: Type.String(),
    port: Type.Number(),
  })
])
export type Intro = Static<typeof Intro>;

export const Blob = Type.Intersect([
  Base,
  Type.Object({
    type: Type.Literal("BLOB"),
    name: Type.String(),
    size: Type.Number(), // Now number (Long in Kotlin)
    blobType: Type.String(), // FILE, TEXT, IMAGE
  })
])
export type Blob = Static<typeof Blob>;

export const Message = Type.Union([Tiny, Intro, Blob]);
export type Message = Static<typeof Message>;

export type ConnectorModuleEvents = {
  onReceived: (payload: Message) => void;
  onStateChanged: (payload: { status: `${STATUS}` }) => void;
};
