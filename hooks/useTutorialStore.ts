import { RefObject } from "react";
import { View } from "react-native";
import { create } from "zustand";

interface TutorialState {
  // register of ref elements
  targets: Record<string, RefObject<View | null>>;
  registerTarget: (name: string, ref: RefObject<View | null>) => void;
  unregisterTarget: (name: string) => void;

  // state of current step
  isTutorialVisible: boolean;
  currentStep: number;

  startTutorial: () => void;
  nextStep: () => void;
  finishTutorial: () => void;
}

export const useTutorialStore = create<TutorialState>((set) => ({
  targets: {},
  registerTarget: (name, ref) =>
    set((state) => ({
      targets: { ...state.targets, [name]: ref },
    })),
  unregisterTarget: (name) =>
    set((state) => {
      const next = { ...state.targets };
      delete next[name];
      return { targets: next };
    }),

  isTutorialVisible: false,
  currentStep: 0,
  startTutorial: () => set({ isTutorialVisible: true, currentStep: 0 }),
  nextStep: () => set((state) => ({ currentStep: state.currentStep + 1 })),
  finishTutorial: () => set({ isTutorialVisible: false, currentStep: 0 }),
}));
