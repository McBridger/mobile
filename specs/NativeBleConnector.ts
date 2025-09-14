import type { TurboModule } from "react-native";
import { TurboModuleRegistry } from "react-native";
import type { EventEmitter } from "react-native/Libraries/Types/CodegenTypes";

export interface ConnectionFail {
  device: string;
  reason: string;
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
  readonly onReceived: EventEmitter<string>;
  readonly onConnectionFailed: EventEmitter<ConnectionFail>;
}

const Module = TurboModuleRegistry.getEnforcing<Spec>("BleConnector");
export default Module;

export const IS_CONNECTED_QUERY_KEY = ["ble", "isConnected"];
export const IS_CONNECTED_QUERY = {
  queryKey: IS_CONNECTED_QUERY_KEY,
  queryFn: Module.isConnected
}

