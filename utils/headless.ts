import { type Received } from "@/specs/NativeBleConnector";
import * as Clipboard from "expo-clipboard";
import { bleRecorder } from "./recorder";

export const BridgerHeadlessTask = async (received: Received) => {
  console.log("Headless Task received data:", received.value);
  if (!received?.value) return;

  try {
    // 2. Write the received data to the clipboard
    await Clipboard.setStringAsync(received.value);
    console.log("Headless Task: Copied to clipboard successfully!");

    await bleRecorder.record(received);
  } catch (error) {
    console.error("Headless Task: Failed to copy to clipboard", error);
  }
};
