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
}
