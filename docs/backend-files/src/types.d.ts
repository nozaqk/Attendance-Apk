import type { Config } from "./config.js";
import type { Db } from "./db.js";
import type { Mailer } from "./services/mailer.js";
import type { AuthContext } from "./auth/context.js";

declare module "fastify" {
  interface FastifyInstance {
    prisma: Db;
    appConfig: Config;
    mailer: Mailer;
  }
  interface FastifyRequest {
    auth: AuthContext;
    /** Real client address (Cloudflare / X-Forwarded-For aware). Set in app.ts. */
    clientIp: string;
  }
}
