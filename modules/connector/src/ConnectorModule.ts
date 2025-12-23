import { NativeModule, requireNativeModule } from 'expo';

import { ConnectorModuleEvents } from './Connector.types';

declare class ConnectorModule extends NativeModule<ConnectorModuleEvents> {
  start(): Promise<void>;
  stop(): Promise<void>;

  isConnected(): Promise<boolean>;
  connect(address: string): Promise<void>;
  disconnect(): Promise<void>;
  send(data: string): Promise<void>;

  getHistory(): Promise<string[]>;
  clearHistory(): Promise<void>;

  getAdvertiseUUID(): Promise<string>;
  getServiceUUID(): Promise<string>;
  getCharacteristicUUID(): Promise<string>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ConnectorModule>('Connector');
