import { useConnector } from "@/store/connection.store";
import { bleRecorder } from "@/utils/recorder";
import { Stack } from "expo-router";
import { useEffect } from "react";

export default function RootLayout() {
  const { addRecorded } = useConnector();

  useEffect(() => {
    bleRecorder.processEntries().then((entries) => addRecorded(entries));
  }, [addRecorded]);

  return <Stack />;
}
