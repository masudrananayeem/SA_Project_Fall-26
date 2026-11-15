# Cloudflare deployment adapter

This Worker is a gateway to the existing Spring Boot application running in Cloudflare Containers. It does not rewrite the Java backend as a Worker. Container instances require a Cloudflare Workers Paid plan and Docker-compatible build environment. See `docs/DEPLOYMENT.md` before deploying.
