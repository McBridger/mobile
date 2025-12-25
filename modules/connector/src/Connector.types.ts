export type DataReceivedPayload = {
  value: string;
  id: string;
};

export type ConnectorModuleEvents = {
  onConnected: () => void;
  onDisconnected: () => void;
  // onConnectionFailed: (payload: ConnectionFailedPayload) => void;
  onReceived: (payload: DataReceivedPayload) => void;
};
