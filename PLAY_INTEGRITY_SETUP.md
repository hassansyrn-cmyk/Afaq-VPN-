# Play Integrity Setup & Keyless Backend Integration Contract

This document provides setup instructions for the Google Play Integrity API integration and specifies the keyless/federated Google authentication backend contract.

## 1. Play Integrity Console Configuration

The application is linked with Google Play Console and Google Cloud with the following verified parameters:

* **Android Application ID:** `com.afaq.vpn`
* **Google Cloud Project Number:** `12432926218`
* **Standard Integrity Token Provider:** Triggered with project number `12432926218` over SHA-256 canonical request hash.

### Standard Play Integrity Verdict Policy
The backend must verify the integrity tokens retrieved by the client against Google's Play Integrity servers and validate the following responses:

1. **Initial Device Verdict Condition:**
   - Must meet `MEETS_DEVICE_INTEGRITY` (meaning the device is a trusted Android device passing basic security/CTS checks).
   - Do **not** block or require `MEETS_STRONG_INTEGRITY` as a minimum condition for general users, as this would lock out a large portion of normal retail devices.
2. **Production Application Condition:**
   - `PLAY_RECOGNIZED` is required in production releases to guarantee the application is verified and unmodified from Google Play.
3. **Evaluation-Only Fields:**
   - The following verdict categories should initially be recorded and evaluated in telemetry/logs *without* automatically blocking all users:
     - `App licensing`
     - `Recent device activity`
     - `Device attributes`
     - `Play Protect status`
     - `App access risk`

---

## 1.1 Server-Side Capacity, Concurrency, and Rate Limits

To configure the registration system for the highest practical user capacity while preserving abuse protection, the backend must implement the following rules:

1. **Client Capacity Limit:**
   - Keep `MAX_ACTIVE_CLIENTS = 245` because the current WireGuard network is `10.66.66.0/24`.
2. **Global Protection Rate-Limit:**
   - Apply a limit of **300 requests per minute** per public IP.
3. **New Registration Quotas:**
   - **20 new registration attempts per device_id per hour** maximum.
   - **20 new registration attempts per WireGuard public_key per hour** maximum.
   - *Note:* Successfully registered devices must **not** consume this strict new-registration quota when retrieving their existing status.
4. **IP Limits Removed:**
   - **The old blanket 3-per-hour IP limit must be removed completely** to avoid blocking multiple legitimate users behind the same mobile carrier or shared public IP.
5. **No IP-Based Primary Identity:**
   - Do **not** use the client's public IP address as the primary registration identity. The locally generated, persistent `device_id` must be used as the primary identity.
6. **Concurrent IP Allocation:**
   - The backend must prevent duplicate IP allocation under concurrent registrations by using atomic transactions or locking around the IP address pool allocations.
7. **Play Integrity Early Verification:**
   - Verify the Google Play Integrity token **before** creating any database record or WireGuard peer. Invalid, replayed, or mismatched integrity tokens must never create peers.
8. **Concurrency Control:**
   - Restrict registrations to maximum **one concurrent provisioning request per device_id** at a time.

---

## 2. Keyless / Federated Backend Authentication Contract

Since the Google Cloud Organization blocks the creation of long-lived, static, downloadable service-account JSON keys (`iam.disableServiceAccountKeyCreation` policy), the backend must authenticate **keylessly** using Google Application Default Credentials (ADC) or Federated Identity.

### Option A: Backend is hosted on Google Cloud (Cloud Run, GCE, GKE, Cloud Functions)
1. **Service Account Association:**
   - Create a Google Service Account (e.g., `play-integrity-verifier@afaq-vpn.iam.gserviceaccount.com`).
   - Grant it the role **Play Integrity View Access** (`roles/playintegrity.viewer`) or **Service Account Token Creator** (`roles/iam.serviceAccountTokenCreator`) in the Google Play Console / Google Cloud Console.
   - Associate this Service Account with the hosting compute resource (e.g., set as the identity of the Cloud Run service or GKE pod).
2. **Authentication Flow:**
   - The backend library (e.g. Google APIs Client Library) will automatically detect the hosting environment's identity and obtain temporary OAuth 2.0 access tokens.
   - **No JSON keys or configuration files are required.**

### Option B: Backend is hosted outside Google Cloud (AWS, Azure, Kubernetes, GitHub, or Bare-Metal)
We utilize **Workload Identity Federation (WIF)** to exchange external tokens for temporary Google OAuth 2.0 credentials keylessly.

1. **Create Workload Identity Pool and Provider:**
   - In Google Cloud Project `12432926218`, create a Workload Identity Pool (e.g. `backend-pool`).
   - Add a Workload Identity Provider mapping to your external hosting environment (e.g., AWS IAM OIDC, OpenID Connect, or GitHub Actions).
2. **Grant Impersonation Permission:**
   - Allow the external workload identity principal to impersonate your target Google Service Account (`play-integrity-verifier@afaq-vpn.iam.gserviceaccount.com`) by granting it the **Service Account Token Creator** (`roles/iam.serviceAccountTokenCreator`) role.
3. **Workload Identity Token Exchange Flow:**
   - When the backend starts up, it retrieves its local short-lived provider token (e.g., AWS STS token or OIDC id_token).
   - It sends this token to Google's Secure Token Service (STS) endpoint to receive a federated federated access token.
   - It exchanges this federated token for a short-lived, temporary Google Cloud OAuth 2.0 Access Token of the impersonated service account.
   - This access token is then used to call the Play Integrity API `playintegrity.googleapis.com`.

---

## 3. Play Integrity Token Verification API Contract

### Request Payload (Client -> Backend `POST /v1/register`):
```json
{
  "device_id": "908c6b9071c6d3bc89a71b2d075253df",
  "public_key": "M2ZkMmE1Mzhh...",
  "integrity_token": "eyJhbGciOiJSUzI1NiIs..."
}
```

### Verification Logic on Backend:
1. **Fetch OAuth 2.0 Access Token:**
   - Obtain an access token for the service account dynamically via implicit Google ADC or federated Workload Identity Token exchange.
2. **Invoke Play Integrity API:**
   - Call the Google Play Integrity standard `v1.decodeIntegrityToken` API endpoint:
     ```http
     POST https://playintegrity.googleapis.com/v1/com.afaq.vpn:decodeIntegrityToken
     Authorization: Bearer <temporary_oauth_access_token>
     Content-Type: application/json

     {
       "integrityToken": "<client_supplied_integrity_token>"
     }
     ```
3. **Verify Payload SHA-256 Request Hash:**
   - Construct the exact deterministic canonical registration representation of the security-sensitive fields:
     `"device_id:<deviceId>|public_key:<publicKey>"`
   - Compute the SHA-256 hash of this canonical representation.
   - Convert this digest to a web-safe Base64 encoded string (with `NO_WRAP` and `NO_PADDING`).
   - Match this computed hash against the `requestHash` returned in Google's Play Integrity JSON response payload:
     `tokenPayloadExternal.requestDetails.requestHash`
   - If they do not match, **reject the registration** immediately (prevents reply/replay and token injection attacks).
4. **Enforce Verdict Policies:**
   - Verify that `tokenPayloadExternal.appLicensingVerdict` or `deviceIntegrity` meets the `MEETS_DEVICE_INTEGRITY` level.
   - Verify that `tokenPayloadExternal.appDetails.appRecognitionVerdict` is `PLAY_RECOGNIZED` in production environments.
