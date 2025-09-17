import type { TurboModule } from "react-native";
import { TurboModuleRegistry } from "react-native";
import type { EventEmitter } from "react-native/Libraries/Types/CodegenTypes";

export interface ConnectionFail {
  device: string;
  reason: string;
}

export interface Received {
  value: string;
  id: string;
}

export interface Spec extends TurboModule {
  // Methods
  setup(service: string, write: string, notify: string): Promise<void>;
  connect(address: string): Promise<void>;
  disconnect(): Promise<void>;
  send(data: string): Promise<void>;
  isConnected(): Promise<boolean>;

  // Events
  readonly onConnected: EventEmitter<void>;
  readonly onDisconnected: EventEmitter<void>;
  readonly onReceived: EventEmitter<Received>;
  readonly onConnectionFailed: EventEmitter<ConnectionFail>;
}

export const BleConnector =
  TurboModuleRegistry.getEnforcing<Spec>("BleConnector");

export default BleConnector;
