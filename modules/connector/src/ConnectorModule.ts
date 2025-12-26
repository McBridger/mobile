import { NativeModule, requireNativeModule } from 'expo';

import { ConnectorModuleEvents, MessagePayload, BrokerStatus } from './Connector.types';

declare class ConnectorModule extends NativeModule<ConnectorModuleEvents> {
  start(): Promise<void>;
  stop(): Promise<void>;

  isConnected(): Promise<boolean>;
  getStatus(): BrokerStatus;
  connect(address: string): Promise<void>;
  disconnect(): Promise<void>;
  send(data: string): Promise<void>;

  getHistory(): Promise<MessagePayload[]>;
  clearHistory(): Promise<void>;

  isReady(): boolean;
  getMnemonic(): string | null;
  setup(mnemonic: string, salt: string): Promise<void>;
  reset(): Promise<void>;
  startDiscovery(): Promise<void>;
  stopDiscovery(): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ConnectorModule>('Connector');
