# ADR-004: React SPA for Admin and Customer Portal

- Status: Accepted
- Context: A separate operator dashboard and customer usage/invoice portal are useful for the demonstration.
- Options: Thymeleaf MVC; React SPA; server-rendered plus HTMX.
- Decision: React + TypeScript SPA consuming Spring REST APIs.
- Consequences: Independent UI iteration and typed components; authentication, route-level roles and API error states must be hardened.
