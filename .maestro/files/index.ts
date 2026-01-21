import { serve } from "bun";
import { resolve } from "path";
import { MaestroTest } from "../base";
import { Test } from "../decorators";

export class FileTest extends MaestroTest {
    protected name = "File Transfer Operations";

    @Test("Receive and Download File")
    async testFileTransfer() {
        await this.cleanupDownloads();
        await this.asConnected();

        // Resolve path properly for Bun.file
        const filePath = resolve(import.meta.dirname, "test.txt");

        // Start a temporary HTTP server to serve the file
        // 10.0.2.2 in emulator connects to 127.0.0.1 on host
        const server = serve({
            port: 8080,
            hostname: "0.0.0.0",
            async fetch(req) {
                const url = new URL(req.url);
                if (url.pathname === "/test.txt") return new Response(Bun.file(filePath));
                return new Response("Not Found", { status: 404 });
            },
        });

        this.log(`Serving test file at ${server.url}`);

        try {
            this.log("Simulating File Offer...");
            await this.simulateFile(
                this.mockMac.address,
                "maestro_test.txt",
                "http://10.0.2.2:8080/test.txt",
                "1KB"
            );

            this.log("Running Notification & Download flow...");
            await this.openNotifications();
            await this.runFlow(import.meta.resolve("./1_transfer.yaml"));

            this.log("Verifying file in System Explorer...");
            await this.openDownloadsUI();
            await this.runFlow(import.meta.resolve("./2_verify_file.yaml"));
        } finally {
            server.stop();
            this.log("Test server stopped.");
        }
    }
    @Test("Send and Host File")
    async testSendFile() {
        await this.asConnected();

        // 1. Establish the Real Bridge (Simulating a Mac client)
        const bridge = await this.connectToBridge();

        const localFile = resolve(import.meta.dirname, "test.txt");
        const originalContent = await Bun.file(localFile).text();

        // Inject file directly into app's private cache via ADB root hop
        const internalPath = await this.pushFile(localFile, "maestro_send.txt");

        this.log("Triggering file share and intercepting via WebSocket...");
        // Start waiting BEFORE triggering, to avoid race conditions
        const messagePromise = this.waitForFileMessage(bridge);

        await this.shareFile(`file://${internalPath}`);

        const fileMsg = await messagePromise;
        this.log(`Caught FileMessage! URL: ${fileMsg.url}`);

        // Rewrite URL for adb forward loopback
        const downloadUrl = fileMsg.url.replace(/http:\/\/([0-9.]+):/, "http://localhost:");

        this.log(`Attempting to download from captured URL: ${downloadUrl}`);
        const response = await fetch(downloadUrl);
        if (!response.ok) throw new Error(`Download failed: ${response.status}`);

        const downloadedContent = await response.text();
        if (downloadedContent !== originalContent) {
            throw new Error("Content integrity check failed!");
        }

        this.log("✅ End-to-End File Hosting & Delivery verified via Real WebSocket Bridge.");
        bridge.close();
    }
}
