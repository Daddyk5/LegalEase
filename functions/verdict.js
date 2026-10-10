/**
 * Reduces a decoded Play Integrity payload to the one thing the app needs: TRUSTED, COMPROMISED,
 * or UNKNOWN. No dependencies, so it is unit-tested without the Google APIs client.
 *
 * COMPROMISED means the token was not issued for this app and request, the app binary is not the
 * one Google Play distributes, or the device does not meet basic device integrity (rooted, unlocked
 * bootloader, or emulated). UNKNOWN means Play could not evaluate; the app then changes nothing.
 */
export function classify(payload, { packageName, requestHash }) {
  if (!payload || typeof payload !== 'object') return 'UNKNOWN';

  const request = payload.requestDetails ?? {};
  if (request.requestPackageName !== packageName || request.requestHash !== requestHash) {
    return 'COMPROMISED';
  }

  const app = payload.appIntegrity?.appRecognitionVerdict;
  if (app === 'UNRECOGNIZED_VERSION') return 'COMPROMISED';

  const device = payload.deviceIntegrity?.deviceRecognitionVerdict;
  if (!Array.isArray(device)) return 'UNKNOWN';
  if (!device.includes('MEETS_DEVICE_INTEGRITY')) return 'COMPROMISED';

  return app === 'PLAY_RECOGNIZED' ? 'TRUSTED' : 'UNKNOWN';
}
