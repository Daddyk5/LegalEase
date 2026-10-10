import { test } from 'node:test';
import assert from 'node:assert/strict';
import { classify } from '../verdict.js';

const expected = { packageName: 'com.hcdc.legalease', requestHash: 'abc123' };

const payload = (overrides = {}) => ({
  requestDetails: { requestPackageName: 'com.hcdc.legalease', requestHash: 'abc123' },
  appIntegrity: { appRecognitionVerdict: 'PLAY_RECOGNIZED' },
  deviceIntegrity: { deviceRecognitionVerdict: ['MEETS_DEVICE_INTEGRITY'] },
  ...overrides,
});

test('a Play-recognized app on a device that meets integrity is trusted', () => {
  assert.equal(classify(payload(), expected), 'TRUSTED');
});

test('a rooted or emulated device is compromised', () => {
  assert.equal(classify(payload({ deviceIntegrity: { deviceRecognitionVerdict: ['MEETS_BASIC_INTEGRITY'] } }), expected), 'COMPROMISED');
  assert.equal(classify(payload({ deviceIntegrity: { deviceRecognitionVerdict: [] } }), expected), 'COMPROMISED');
});

test('a modified app is compromised', () => {
  assert.equal(classify(payload({ appIntegrity: { appRecognitionVerdict: 'UNRECOGNIZED_VERSION' } }), expected), 'COMPROMISED');
});

test('a token for another request or app is compromised', () => {
  assert.equal(classify(payload({ requestDetails: { requestPackageName: 'com.hcdc.legalease', requestHash: 'other' } }), expected), 'COMPROMISED');
  assert.equal(classify(payload({ requestDetails: { requestPackageName: 'com.example', requestHash: 'abc123' } }), expected), 'COMPROMISED');
});

test('anything Play could not evaluate is unknown, never compromised', () => {
  assert.equal(classify(null, expected), 'UNKNOWN');
  assert.equal(classify(payload({ appIntegrity: { appRecognitionVerdict: 'UNEVALUATED' } }), expected), 'UNKNOWN');
  assert.equal(classify(payload({ deviceIntegrity: {} }), expected), 'UNKNOWN');
});
