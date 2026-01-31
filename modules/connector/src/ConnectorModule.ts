import { NativeModule, requireNativeModule } from 'expo';

import { ConnectorModuleEvents, MessagePayload, STATUS } from './Connector.types';

declare class ConnectorModule extends NativeModule<ConnectorModuleEvents> {
  start(): Promise<void>;

  getStatus(): STATUS;
  send(data: string): Promise<void>;

  getHistory(): Promise<MessagePayload[]>;
  clearHistory(): Promise<void>;

  isReady(): boolean;
  getMnemonic(): string | null;
  setup(mnemonic: string, salt: string): Promise<void>;
  reset(): Promise<void>;

  setBool(key: string, value: boolean): void;
  getBool(key: string, defaultValue: boolean): boolean;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ConnectorModule>('Connector');
