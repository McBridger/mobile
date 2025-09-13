import type { TurboModule } from "react-native";
import { TurboModuleRegistry } from "react-native";

export interface Spec extends TurboModule {
  showBubble(): void;
  hideBubble(): void;
  isBubble(): boolean;
}

export default TurboModuleRegistry.getEnforcing<Spec>("NativeBubble");