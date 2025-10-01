import { NativeModule, requireNativeModule } from 'expo';

import { ConnectorModuleEvents } from './Connector.types';

declare class ConnectorModule extends NativeModule<ConnectorModuleEvents> {
  isConnected(): Promise<boolean>;
  setup(serviceUuid: string, characteristicUuid: string): Promise<void>;
  connect(address: string): Promise<void>;
  disconnect(): Promise<void>;
  send(data: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ConnectorModule>('Connector');
