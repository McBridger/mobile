import * as Clipboard from 'expo-clipboard'; // 1. Import the library

// Define and register the Headless JS Task
export const BridgerHeadlessTask = async (taskData: { bleData?: string }) => {
  console.log('Headless Task received data:', taskData.bleData);

  if (taskData.bleData) {
    try {
      // 2. Write the received data to the clipboard
      await Clipboard.setStringAsync(taskData.bleData);
      console.log('Headless Task: Copied to clipboard successfully!');
    } catch (error) {
      console.error('Headless Task: Failed to copy to clipboard', error);
    }
  }
};