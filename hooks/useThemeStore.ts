import { create } from "zustand";

interface ThemeState {
  themeMode: "light" | "dark" | "system";
  toggleTheme: () => void;
}

export const useThemeStore = create<ThemeState>((set) => ({
  themeMode: "system",
  toggleTheme: () => set((state) => ({ 
    themeMode: state.themeMode === "dark" ? "light" : "dark" 
  })),
}));
