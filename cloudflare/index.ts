import { DurableObject } from "cloudflare:workers";

interface Env {
  METERFLOW_API: DurableObjectNamespace<MeterFlowApiContainer>;
  SPRING_DATASOURCE_URL: string;
  SPRING_DATASOURCE_USERNAME: string;
  SPRING_DATASOURCE_PASSWORD: string;
  METERFLOW_OIDC_ISSUER_URI: string;
  METERFLOW_WEBHOOK_ALLOWED_HOSTS: string;
  SPRING_DATA_REDIS_HOST: string;
  SPRING_DATA_REDIS_PORT: string;
  SPRING_DATA_REDIS_PASSWORD: string;
  SPRING_DATA_REDIS_SSL_ENABLED: string;
  METERFLOW_CORS_ALLOWED_ORIGINS: string;
}

const PORT = 8080;

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

  private async startAndWait(): Promise<void> {
    const container = this.ctx.container!;
    if (!container.running) {
      container.start({
        image: container.images.base,
        instance: "lite",
        enableInternet: true,
        env: {
          PORT: String(PORT),
          SPRING_PROFILES_ACTIVE: "prod",
          SPRING_DATASOURCE_URL: this.bindings.SPRING_DATASOURCE_URL,
          SPRING_DATASOURCE_USERNAME: this.bindings.SPRING_DATASOURCE_USERNAME,
          SPRING_DATASOURCE_PASSWORD: this.bindings.SPRING_DATASOURCE_PASSWORD,
          METERFLOW_OIDC_ISSUER_URI: this.bindings.METERFLOW_OIDC_ISSUER_URI,
          METERFLOW_WEBHOOK_ALLOWED_HOSTS: this.bindings.METERFLOW_WEBHOOK_ALLOWED_HOSTS,
          SPRING_DATA_REDIS_HOST: this.bindings.SPRING_DATA_REDIS_HOST,
          SPRING_DATA_REDIS_PORT: this.bindings.SPRING_DATA_REDIS_PORT || "6379",
          SPRING_DATA_REDIS_PASSWORD: this.bindings.SPRING_DATA_REDIS_PASSWORD || "",
          SPRING_DATA_REDIS_SSL_ENABLED: this.bindings.SPRING_DATA_REDIS_SSL_ENABLED || "true",
          METERFLOW_CORS_ALLOWED_ORIGINS: this.bindings.METERFLOW_CORS_ALLOWED_ORIGINS,
        },
      });
    }
    await container.setInactivityTimeout(30 * 60 * 1000);
    const port = container.getTcpPort(PORT);
    for (let attempt = 0; attempt < 120; attempt++) {
      try {
        const response = await port.fetch("http://container/actuator/health", { signal: AbortSignal.timeout(1500) });
        await response.body?.cancel();
        if (response.ok) return;
      } catch { /* Spring Boot is still starting or waiting for its database. */ }
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    throw new Error("MeterFlow Spring Boot container did not become healthy within 120 seconds.");
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/healthz") return Response.json({ service: "meterflow-cloudflare-gateway", status: "ok" });
    const id = env.METERFLOW_API.idFromName("meterflow-api-singleton");
    return env.METERFLOW_API.get(id).fetch(request);
  },
};
