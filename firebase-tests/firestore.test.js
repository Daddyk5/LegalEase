// Firestore security rules tests. Run with `npm test` (starts the emulator via firebase-tools).
import { after, before, beforeEach, describe, it } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc } from 'firebase/firestore';

let env;

const OWNER = 'alice';
const OTHER = 'mallory';

const liveAnalysis = () => ({
  schemaVersion: 1,
  documentName: 'Contract of Lease.pdf',
  sourceKind: 'PDF',
  status: 'COMPLETED',
  createdAt: 1_760_000_000_000,
  updatedAt: 1_760_000_000_500,
  pageCount: 3,
  unreadablePages: [1],
  classification: 'Voidable',
  confidence: 0.83,
  summary: 'Opening text of the contract.',
  errorCode: null,
  contractType: 'LEASE',
  scoringVersion: 2,
  deleted: false,
});

const contract = () => ({
  schemaVersion: 1,
  contractId: 'c1',
  version: 1,
  type: 'LEASE',
  title: 'Contract of Lease',
  body: 'KNOW ALL MEN BY THESE PRESENTS…',
  createdAt: 1_760_000_000_000,
  updatedAt: 1_760_000_000_000,
});

const db = (uid, verified = true) =>
  env.authenticatedContext(uid, { email_verified: verified }).firestore();

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-legalease',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8') },
  });
});

beforeEach(async () => env.clearFirestore());

after(async () => env.cleanup());

describe('ownership', () => {
  it('owner with a verified email can write and read their analysis', async () => {
    const ref = doc(db(OWNER), `users/${OWNER}/analyses/a1`);
    await assertSucceeds(setDoc(ref, liveAnalysis()));
    await assertSucceeds(getDoc(ref));
  });

  it('another user cannot read or write it', async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), `users/${OWNER}/analyses/a1`), liveAnalysis()));
    await assertFails(getDoc(doc(db(OTHER), `users/${OWNER}/analyses/a1`)));
    await assertFails(setDoc(doc(db(OTHER), `users/${OWNER}/analyses/a2`), liveAnalysis()));
    await assertFails(deleteDoc(doc(db(OTHER), `users/${OWNER}/analyses/a1`)));
  });

  it('unauthenticated and unverified users are denied', async () => {
    const anon = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(anon, `users/${OWNER}/analyses/a1`)));
    await assertFails(setDoc(doc(db(OWNER, false), `users/${OWNER}/analyses/a1`), liveAnalysis()));
  });

  it('paths outside the user tree are denied', async () => {
    await assertFails(setDoc(doc(db(OWNER), 'analyses/a1'), liveAnalysis()));
    await assertFails(setDoc(doc(db(OWNER), `users/${OWNER}/secrets/x`), { a: 1 }));
  });
});

describe('analysis schema', () => {
  const write = (data) => setDoc(doc(db(OWNER), `users/${OWNER}/analyses/a1`), data);

  it('rejects unknown fields, such as OCR page text', async () => {
    await assertFails(write({ ...liveAnalysis(), pages: ['full contract text'] }));
  });

  it('rejects wrong types and out-of-range values', async () => {
    await assertFails(write({ ...liveAnalysis(), confidence: 1.5 }));
    await assertFails(write({ ...liveAnalysis(), confidence: '0.9' }));
    await assertFails(write({ ...liveAnalysis(), classification: 'Valid' }));
    await assertFails(write({ ...liveAnalysis(), sourceKind: 'DOCX' }));
    await assertFails(write({ ...liveAnalysis(), createdAt: 'yesterday' }));
  });

  it('enforces size limits', async () => {
    await assertFails(write({ ...liveAnalysis(), summary: 'x'.repeat(2001) }));
    await assertFails(write({ ...liveAnalysis(), documentName: 'x'.repeat(301) }));
    await assertSucceeds(write({ ...liveAnalysis(), summary: 'x'.repeat(2000) }));
  });

  it('rejects unknown contract types', async () => {
    await assertFails(write({ ...liveAnalysis(), contractType: 'PROMISSORY_NOTE' }));
  });

  it('only finished analyses are synced', async () => {
    await assertFails(write({ ...liveAnalysis(), status: 'EXTRACTING_TEXT' }));
  });

  it('accepts a failed analysis without a verdict', async () => {
    await assertSucceeds(write({
      ...liveAnalysis(), status: 'FAILED', classification: null, confidence: null, errorCode: 'no_text_found',
    }));
  });

  it('accepts a content-free tombstone but not one that carries content', async () => {
    await assertSucceeds(write({ schemaVersion: 1, updatedAt: 5, deleted: true }));
    await assertFails(write({ schemaVersion: 1, updatedAt: 5, deleted: true, documentName: 'leak.pdf' }));
  });
});

describe('contracts', () => {
  const ref = () => doc(db(OWNER), `users/${OWNER}/contracts/v1`);

  it('accepts a valid version and an identical re-upload', async () => {
    await assertSucceeds(setDoc(ref(), contract()));
    await assertSucceeds(setDoc(ref(), contract()));
  });

  it('versions are immutable', async () => {
    await assertSucceeds(setDoc(ref(), contract()));
    await assertFails(setDoc(ref(), { ...contract(), body: 'edited in place' }));
  });

  it('rejects unknown template types and oversized bodies', async () => {
    await assertFails(setDoc(ref(), { ...contract(), type: 'PROMISSORY_NOTE' }));
    await assertFails(setDoc(ref(), { ...contract(), body: 'x'.repeat(200001) }));
  });
});

describe('user profile', () => {
  it('stores only schema version and last sign-in time', async () => {
    const ref = doc(db(OWNER), `users/${OWNER}`);
    await assertSucceeds(setDoc(ref, { schemaVersion: 1, lastSignInAt: 1 }));
    await assertFails(setDoc(ref, { schemaVersion: 1, lastSignInAt: 1, email: 'alice@example.com' }));
  });
});
