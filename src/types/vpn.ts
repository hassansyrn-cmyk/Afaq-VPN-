export type VpnState = 'disconnected' | 'connecting' | 'connected' | 'disconnecting' | 'error';
export interface WireGuardConfig { privateKey: string; address: string; dns: string; publicKey: string; presharedKey?: string; endpoint: string; allowedIps: string; persistentKeepalive: number; }
export interface TrafficStats { receivedBytes: number; transmittedBytes: number; }
export interface VpnStatus { state: VpnState; connectedAt?: number; error?: string; }
export interface ProvisioningStatus { isRegistered: boolean; hasIdentity: boolean; legacyFallbackEnabled?: boolean; address?: string; endpoint?: string; }
export interface ProvisioningResult { state: 'registered' | 'failed'; existing?: boolean; error?: string; isRecoverableError?: boolean; retryAfterSeconds?: number; }
export interface VpnPlugin { prepareVpn(): Promise<{ granted: boolean }>; connect(options: { config?: WireGuardConfig }): Promise<VpnStatus>; disconnect(): Promise<VpnStatus>; getConnectionStatus(): Promise<VpnStatus>; getTrafficStats(): Promise<TrafficStats>; isVpnPermissionGranted(): Promise<{ granted: boolean }>; openVpnSettings(): Promise<void>; addListener(eventName: 'statusChanged', listener: (status: VpnStatus) => void): Promise<{ remove: () => Promise<void> }>; getProvisioningStatus(): Promise<ProvisioningStatus>; provisionDevice(): Promise<ProvisioningResult>; }
