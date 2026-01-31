import ConnectorModule from "@/modules/connector";
import { create } from "zustand";

interface SettingsState {
  hasSeenTutorial: boolean;
  setHasSeenTutorial: (value: boolean) => void;
}

export const useSettingsStore = create<SettingsState>((set) => ({
  hasSeenTutorial: ConnectorModule.getBool("has_seen_tutorial", false),
  setHasSeenTutorial: (value: boolean) => {
    ConnectorModule.setBool("has_seen_tutorial", value);
    set({ hasSeenTutorial: value });
  },
}));
