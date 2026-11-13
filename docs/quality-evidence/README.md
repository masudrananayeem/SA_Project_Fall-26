# Quality Attribute Evidence

Store reproducible evidence here. Each run should include:

- date/time, Git commit, machine/CPU/RAM, OS, Java and PostgreSQL versions;
- exact command and configuration;
- raw stdout/stderr or exported k6 JSON;
- expected target and observed result;
- failures, caveats and rerun instructions.

No QA1–QA5 benchmark is marked as passed until evidence is generated from the implemented end-to-end path. In particular, the included k6 workload is a test driver only and is not a performance result.
