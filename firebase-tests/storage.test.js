// Cloud Storage security rules tests. Run with `npm test`.
import { after, before, describe, it } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { getBytes, ref, uploadBytes } from 'firebase/storage';

let env;
const pdf = new Uint8Array([0x25, 0x50, 0x44, 0x46]); // "%PDF"

const storage = (uid, verified = true) =>
  env.authenticatedContext(uid, { email_verified: verified }).storage();

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-legalease',
    storage: { rules: readFileSync('../storage.rules', 'utf8') },
  });
});

after(async () => env.cleanup());

describe('backups', () => {
  it('owner can upload a PDF backup', async () => {
    await assertSucceeds(
      uploadBytes(ref(storage('alice'), 'users/alice/backups/lease.pdf'), pdf, { contentType: 'application/pdf' }));
  });

  it('other users cannot write or read it', async () => {
    await assertFails(
      uploadBytes(ref(storage('mallory'), 'users/alice/backups/x.pdf'), pdf, { contentType: 'application/pdf' }));
    await assertFails(getBytes(ref(storage('mallory'), 'users/alice/backups/lease.pdf')));
  });

  it('only PDFs are accepted', async () => {
    await assertFails(
      uploadBytes(ref(storage('alice'), 'users/alice/backups/x.exe'), pdf, { contentType: 'application/octet-stream' }));
  });

  it('unverified users are denied', async () => {
    await assertFails(
      uploadBytes(ref(storage('alice', false), 'users/alice/backups/y.pdf'), pdf, { contentType: 'application/pdf' }));
  });
});

describe('models', () => {
  it('nobody can upload model files from a client', async () => {
    await assertFails(uploadBytes(ref(storage('alice'), 'models/model.tflite'), pdf));
  });
});
