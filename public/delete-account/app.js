// Web account deletion for LegalEase. The Firebase config comes from Firebase Hosting's reserved
// /__/firebase/init.json, so no project keys live in the repository.
import { initializeApp } from 'https://www.gstatic.com/firebasejs/13.0.0/firebase-app.js';
import { initializeAppCheck, ReCaptchaEnterpriseProvider } from 'https://www.gstatic.com/firebasejs/13.0.0/firebase-app-check.js';
import {
  getAuth, GoogleAuthProvider, signInWithPopup, signInWithEmailAndPassword, deleteUser,
} from 'https://www.gstatic.com/firebasejs/13.0.0/firebase-auth.js';
import {
  getFirestore, collection, doc, getDocs, limit, query, writeBatch, deleteDoc,
} from 'https://www.gstatic.com/firebasejs/13.0.0/firebase-firestore.js';
import { getStorage, ref, listAll, deleteObject } from 'https://www.gstatic.com/firebasejs/13.0.0/firebase-storage.js';

const $ = (id) => document.getElementById(id);
const status = (text, isError = false) => {
  $('status').textContent = text;
  $('status').className = isError ? 'error' : '';
};

const config = await fetch('/__/firebase/init.json').then((r) => r.json());
const app = initializeApp(config);
if (window.LEGALEASE_RECAPTCHA_SITE_KEY) {
  initializeAppCheck(app, {
    provider: new ReCaptchaEnterpriseProvider(window.LEGALEASE_RECAPTCHA_SITE_KEY),
    isTokenAutoRefreshEnabled: true,
  });
}
const auth = getAuth(app);
const db = getFirestore(app);
const storage = getStorage(app);

function signedIn(user) {
  if (!user.emailVerified) {
    status('Verify your email address first (check your inbox), then sign in again.', true);
    return;
  }
  $('who').textContent = user.email ?? user.uid;
  $('signin').hidden = true;
  $('confirm').hidden = false;
  status('');
}

$('google').addEventListener('click', async () => {
  try {
    signedIn((await signInWithPopup(auth, new GoogleAuthProvider())).user);
  } catch (e) {
    status('Sign-in failed. Try again.', true);
  }
});

$('emailForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    signedIn((await signInWithEmailAndPassword(auth, $('email').value, $('password').value)).user);
  } catch (e) {
    // Same message whether the account exists or not.
    status('Wrong email or password.', true);
  }
});

$('sure').addEventListener('change', () => { $('delete').disabled = !$('sure').checked; });

async function deleteCollection(path) {
  for (;;) {
    const snapshot = await getDocs(query(collection(db, path), limit(400)));
    if (snapshot.empty) return;
    const batch = writeBatch(db);
    snapshot.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
  }
}

$('delete').addEventListener('click', async () => {
  const user = auth.currentUser;
  if (!user) return;
  $('delete').disabled = true;
  status('Deleting…');
  try {
    await deleteCollection(`users/${user.uid}/analyses`);
    await deleteCollection(`users/${user.uid}/contracts`);
    await deleteDoc(doc(db, 'users', user.uid));
    const backups = await listAll(ref(storage, `users/${user.uid}/backups`));
    await Promise.all(backups.items.map((item) => deleteObject(item)));
    await deleteUser(user);
    $('confirm').hidden = true;
    status('Your LegalEase account and cloud backup have been deleted.');
  } catch (e) {
    $('delete').disabled = false;
    // Part of the backup may already be gone; running it again finishes the job.
    status('Deletion did not finish. Try again, or delete your account from the app.', true);
  }
});
