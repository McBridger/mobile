import { Received } from "@/specs/NativeBleConnector";
import * as FileSystem from "expo-file-system";
import { z } from "zod";

const LOG_DIR_URI = FileSystem.documentDirectory + "ble_recorder/";

export const LogEntry = z.object({
  timestamp: z.string(),
  value: z.string(),
  id: z.uuidv4(),
});
// eslint-disable-next-line @typescript-eslint/no-redeclare
export type LogEntry = z.infer<typeof LogEntry>;

class BleRecorder {
  private readonly prefix = `[${this.constructor.name}]`;
  private readonly log = console.log.bind(console, this.prefix);
  private readonly error = console.error.bind(console, this.prefix);
  private readonly schema = LogEntry;

  constructor(private readonly logDirUri: string) {}

  public async record({ value, id }: Received): Promise<void> {
    try {
      await this.ensureDirExists();

      const fileName = `${id}.json`;
      const filePath = this.logDirUri + fileName;

      const entry: LogEntry = {
        timestamp: new Date(Date.now()).toISOString(),
        value,
        id,
      };

      await FileSystem.writeAsStringAsync(filePath, JSON.stringify(entry));
      this.log(`Entry saved to ${fileName}`);
    } catch (e) {
      this.error("Failed to record entry:", e);
    }
  }

  public async processEntries(): Promise<LogEntry[]> {
    const dirInfo = await FileSystem.getInfoAsync(this.logDirUri);
    if (!dirInfo.exists) return [];

    try {
      const fileNames = await FileSystem.readDirectoryAsync(this.logDirUri);
      if (!fileNames.length) return [];

      const readPromises = fileNames.map((name) =>
        FileSystem.readAsStringAsync(this.logDirUri + name)
      );

      const contents = await Promise.all(readPromises);
      const entries = contents.reduce((acc: LogEntry[], content) => {
        const raw = JSON.parse(content);
        const parsed = this.schema.safeParse(raw);
        if (parsed.success) acc.push(parsed.data);
        return acc;
      }, []);

      await FileSystem.deleteAsync(this.logDirUri, { idempotent: true });

      this.log(`Processed and cleared ${entries.length} entries.`);
      return entries;
    } catch (e: any) {
      this.error("Failed to process entries:", e);
      return [];
    }
  }

  private async ensureDirExists(): Promise<void> {
    const dirInfo = await FileSystem.getInfoAsync(this.logDirUri);
    if (!dirInfo.exists) {
      await FileSystem.makeDirectoryAsync(this.logDirUri, {
        intermediates: true,
      });
    }
  }
}

export const bleRecorder = new BleRecorder(LOG_DIR_URI);
