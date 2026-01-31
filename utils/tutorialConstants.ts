import { TutorialStep } from "@/components/TutorialOverlay";
import { Item } from "@/modules/connector";

export const tutorialSteps: TutorialStep[] = [
  {
    targetKey: "header",
    title: "Connection Status",
    description: "Here you can see the current connection state with your Mac.",
  },
  {
    targetKey: "list",
    title: "Activity Feed",
    description: "All synced clipboard items will appear here in real-time.",
  },
  {
    targetKey: "list_send",
    title: "Send from Adnroid to Mac",
    description:
      "To send text from Android, copy it anywhere, swipe down from the top to open Quick Settings, and tap the 'Send to clipboard' icon! 🚀",
  },
  {
    targetKey: "settings",
    title: "Secure Pairing",
    description:
      "Need to change your mnemonic phrase or reset the connection? Use the settings button.",
  },
];

export const connectionTutorialItems: Item[] = [
  {
    id: "fake-1",
    type: "received",
    content: "Hello from Mac!",
    time: Date.now(),
  },
  {
    id: "fake-2",
    type: "sent",
    content: "Sent from Android phone",
    time: Date.now() - 1000,
  },
];
