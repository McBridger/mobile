export type MessagePayload = {
  id: string;
  type: number;
  value: string;
  time: number;
};

export type BrokerStatus = 
  | "idle" 
  | "encrypting" 
  | "keys_ready" 
  | "transport_initializing" 
  | "ready" 
  | "discovering" 
  | "connecting" 
  | "connected" 
  | "disconnected" 
  | "error";

export type ConnectorModuleEvents = {
  onConnected: () => void;
  onDisconnected: () => void;
  onReceived: (payload: MessagePayload) => void;
  onStateChanged: (payload: { status: BrokerStatus }) => void;
};