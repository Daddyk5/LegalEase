import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { google } from 'googleapis';
import { classify } from './verdict.js';

const PACKAGE_NAME = 'com.hcdc.legalease';
const MAX_TOKEN_LENGTH = 16_384;
const MAX_HASH_LENGTH = 500;

/**
 * Decodes a Play Integrity standard token for the LegalEase app and returns only
 * { verdict: 'TRUSTED' | 'COMPROMISED' | 'UNKNOWN' }. The raw verdict never leaves the server.
 *
 * Requires the Play Integrity API to be enabled in the Cloud project linked to the Play Console app,
 * and the function's service account to be allowed to call it (see SETUP.md).
 */
export const integrityVerdict = onCall(
  { region: 'asia-southeast1', enforceAppCheck: true, maxInstances: 10 },
  async (request) => {
    const { token, requestHash } = request.data ?? {};
    if (typeof token !== 'string' || token.length === 0 || token.length > MAX_TOKEN_LENGTH ||
        typeof requestHash !== 'string' || requestHash.length === 0 || requestHash.length > MAX_HASH_LENGTH) {
      throw new HttpsError('invalid-argument', 'Expected a token and its request hash.');
    }

    const auth = new google.auth.GoogleAuth({ scopes: ['https://www.googleapis.com/auth/playintegrity'] });
    const playintegrity = google.playintegrity({ version: 'v1', auth });
    try {
      const response = await playintegrity.v1.decodeIntegrityToken({
        packageName: PACKAGE_NAME,
        requestBody: { integrityToken: token },
      });
      return { verdict: classify(response.data.tokenPayloadExternal, { packageName: PACKAGE_NAME, requestHash }) };
    } catch (error) {
      // An undecodable token (expired, forged, or for another app) is reported as UNKNOWN, not as an error,
      // so a transient Google API failure never marks a device compromised.
      console.warn('decodeIntegrityToken failed', error?.code ?? error?.message);
      return { verdict: 'UNKNOWN' };
    }
  },
);
