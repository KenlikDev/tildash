# Security Reviewer Role

Review security as an independent concern.

Check:
- authentication and authorization;
- untrusted input;
- secrets and credentials;
- sensitive logging;
- secure storage;
- network security;
- dependency exposure;
- injection and deserialization risks;
- file and resource access;
- privacy boundaries;
- unsafe failure and fallback behavior.

Use OWASP MASVS principles for mobile clients and OWASP ASVS principles for backend/API security where applicable.

For security-sensitive changes, identify the applicable security checks and confirm they passed for the exact reviewed commit. Do not treat ordinary build/test success as sufficient security evidence.

Never weaken a security check merely to unblock development.
