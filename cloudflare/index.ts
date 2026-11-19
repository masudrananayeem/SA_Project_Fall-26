import { DurableObject } from "cloudflare:workers";

/**
 * Cloudflare Worker gateway in front of the Spring Boot container (Cloudflare Containers).
 * Secrets are set with `npx wrangler secret put <NAME>` and passed to the container as env vars.
 */
interface Env {
  METERFLOW_API: DurableObjectNamespace<MeterFlowApiContainer>;
  SPRING_PROFILES_ACTIVE?: string;           // default "cloud" (Basic Auth demo); "prod" = OIDC JWT + Flyway
  SPRING_DATASOURCE_URL: string;             // jdbc:postgresql://<host>/<db>?sslmode=require
  SPRING_DATASOURCE_USERNAME: string;
  SPRING_DATASOURCE_PASSWORD: string;
  ADMIN_USERNAME?: string;
  ADMIN_PASSWORD?: string;
  CUSTOMER_USERNAME?: string;
  CUSTOMER_PASSWORD?: string;
  METERFLOW_CORS_ALLOWED_ORIGINS: string;    // e.g. https://meterflow.vercel.app
  CACHE_TYPE?: string;                       // "simple" (default) or "redis"
  DB_POOL_SIZE?: string;
  METERFLOW_OIDC_ISSUER_URI?: string;        // prod profile only
  METERFLOW_WEBHOOK_ALLOWED_HOSTS?: string;
  SPRING_DATA_REDIS_HOST?: string;
  SPRING_DATA_REDIS_PORT?: string;
  SPRING_DATA_REDIS_PASSWORD?: string;
  SPRING_DATA_REDIS_SSL_ENABLED?: string;
}

const PORT = 8080;
const STARTUP_SECONDS = 240;

export class MeterFlowApiContainer extends DurableObject<Env> {
  private startup?: Promise<void>;
  private readonly bindings: Env;

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.bindings = env;
  }

  async fetch(request: Request): Promise<Response> {
    this.startup ??= this.startAndWait().finally(() => { this.startup = undefined; });
    await this.startup;
    const url = new URL(request.url);
    url.protocol = "http:";
    url.host = "container";
    const forwarded = new Request(url, request);
    forwarded.headers.delete("host");
    return this.ctx.container!.getTcpPort(PORT).fetch(forwarded);
  }

  private containerEnv(): Record<string, string> {
    const b = this.bindings;
    const values: Record<string, string | undefined> = {
      PORT: String(PORT),
      SPRING_PROFILES_ACTIVE: b.SPRING_PROFILES_ACTIVE || "cloud",
      JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=70 -XX:+UseSerialGC",
      SPRING_DATASOURCE_URL: b.SPRING_DATASOURCE_URL,
      SPRING_DATASOURCE_USERNAME: b.SPRING_DATASOURCE_USERNAME,
      SPRING_DATASOURCE_PASSWORD: b.SPRING_DATASOURCE_PASSWORD,
      ADMIN_USERNAME: b.ADMIN_USERNAME,
      ADMIN_PASSWORD: b.ADMIN_PASSWORD,
      CUSTOMER_USERNAME: b.CUSTOMER_USERNAME,
      CUSTOMER_PASSWORD: b.CUSTOMER_PASSWORD,
      METERFLOW_CORS_ALLOWED_ORIGINS: b.METERFLOW_CORS_ALLOWED_ORIGINS,
      CACHE_TYPE: b.CACHE_TYPE || "simple",
      DB_POOL_SIZE: b.DB_POOL_SIZE || "5",
      METERFLOW_OIDC_ISSUER_URI: b.METERFLOW_OIDC_ISSUER_URI,
      METERFLOW_WEBHOOK_ALLOWED_HOSTS: b.METERFLOW_WEBHOOK_ALLOWED_HOSTS,
      SPRING_DATA_REDIS_HOST: b.SPRING_DATA_REDIS_HOST || "localhost",
      SPRING_DATA_REDIS_PORT: b.SPRING_DATA_REDIS_PORT || "6379",
      SPRING_DATA_REDIS_PASSWORD: b.SPRING_DATA_REDIS_PASSWORD,
      SPRING_DATA_REDIS_SSL_ENABLED: b.SPRING_DATA_REDIS_SSL_ENABLED || "false",
    };
    return Object.fromEntries(Object.entries(values).filter(([, v]) => v !== undefined && v !== "")) as Record<string, string>;
  }

  private async startAndWait(): Promise<void> {
    const container = this.ctx.container!;
    if (!container.running) {
      // standard-1 = 1/2 vCPU, 4 GiB: a Spring Boot JVM does not fit in "lite" (256 MiB).
      container.start({ image: container.images.base, instance: "standard-1", enableInternet: true, env: this.containerEnv() });
    }
    await container.setInactivityTimeout(30 * 60 * 1000);
    const port = container.getTcpPort(PORT);
    for (let attempt = 0; attempt < STARTUP_SECONDS; attempt++) {
      try {
        const response = await port.fetch("http://container/actuator/health", { signal: AbortSignal.timeout(1500) });
        await response.body?.cancel();
        if (response.ok) return;
      } catch { /* Spring Boot is still starting or waiting for its database. */ }
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    throw new Error(`MeterFlow Spring Boot container did not become healthy within ${STARTUP_SECONDS} seconds.`);
  }
}

function container(env: Env) {
  return env.METERFLOW_API.get(env.METERFLOW_API.idFromName("meterflow-api-singleton"));
}

/**
 * Daily jobs. The container sleeps after 30 idle minutes, so Spring's own @Scheduled jobs may not
 * be running at their time. This cron trigger wakes the container and runs every date-based job
 * through its ADMIN endpoint. Every job is idempotent: running it twice changes nothing.
 */
async function runDailyJobs(env: Env, now: Date): Promise<void> {
  if (!env.ADMIN_USERNAME || !env.ADMIN_PASSWORD) {
    console.warn("Daily jobs skipped: ADMIN_USERNAME / ADMIN_PASSWORD secrets are not set.");
    return;
  }
  const auth = "Basic " + btoa(`${env.ADMIN_USERNAME}:${env.ADMIN_PASSWORD}`);
  const previousMonth = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 1, 1)).toISOString().slice(0, 7);
  const jobs: string[] = ["/api/subscriptions/lifecycle/run"];
  if (now.getUTCDate() === 1) jobs.push(`/api/invoices/batch/run?period=${previousMonth}`);   // FR5 billing date
  jobs.push("/api/invoices/prepaid/renew", "/api/dunning/process", "/api/payments/standing/enforce");
  const api = container(env);
  for (const path of jobs) {
    try {
      const response = await api.fetch(new Request(`https://meterflow.internal${path}`, {
        method: "POST",
        headers: { Authorization: auth, "X-Requested-With": "XMLHttpRequest", Accept: "application/json" },
      }));
      console.log(`daily job ${path}: ${response.status} ${(await response.text()).slice(0, 300)}`);
    } catch (error) {
      console.error(`daily job ${path} failed`, error);
    }
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/healthz") return Response.json({ service: "meterflow-cloudflare-gateway", status: "ok" });
    return container(env).fetch(request);
  },

  async scheduled(controller: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(runDailyJobs(env, new Date(controller.scheduledTime)));
  },
};
