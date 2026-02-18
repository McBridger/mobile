import { NativeModule, requireNativeModule } from 'expo';

import { ConnectorModuleEvents, Porter, BrokerState } from './Connector.types';

declare class ConnectorModule extends NativeModule<ConnectorModuleEvents> {
  start(): Promise<void>;

  getBrokerState(): BrokerState;
  send(data: string): Promise<void>;

  getHistory(): Promise<Porter[]>;
  clearHistory(): Promise<void>;

  isReady(): boolean;
  getMnemonic(): string | null;
  setup(mnemonic: string, salt: string): Promise<void>;
  reset(): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ConnectorModule>('Connector');
