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

export type MessagePayload = {
  id: string;
  type: number;
  value: string;
  timestamp: number;
};

export type ConnectorModuleEvents = {
  onConnected: () => void;
  onDisconnected: () => void;
  onReceived: (payload: MessagePayload) => void;
  onStateChanged: (payload: { status: `${STATUS}` }) => void;
};