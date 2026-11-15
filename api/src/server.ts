import { resolve } from "node:path";
import { buildApp } from "./app.js";

const port = Number(process.env.PORT ?? 3000);
const host = process.env.HOST ?? "0.0.0.0";
const dbPath = process.env.DB_PATH ?? resolve("data/phone-golf.db");
const webDir = process.env.WEB_DIR ?? resolve(import.meta.dirname, "../../web");

const { app } = buildApp({ dbPath, webDir, logger: true });
app.listen({ port, host }).catch((err) => {
  app.log.error(err);
  process.exit(1);
});
