// エンドポイント呼び出しの薄いラッパー。ここで必ず 2 つのタグを付ける。
//   type … read / write / auth / upload。負荷の同値クラス（同値分割）で、閾値はこの単位で判定する
//   name … 「GET /api/posts/{id}」のようなパスのひな形。付けないと k6 は URL ごとに
//          メトリクスを分けてしまい、id や cursor の数だけ系列が増えて集計できなくなる
import http from 'k6/http';
import { check } from 'k6';
import encoding from 'k6/encoding';
import { Trend } from 'k6/metrics';
import { BASE_URL } from './config.js';
import { bearer, invalidateSession } from './auth.js';

// エンドポイント別の応答時間。k6 の集計は http_req_duration をタグで分けて表示してくれないので、
// エンドポイントごとに Trend を用意して、どこが遅いのかを集計結果から直接読めるようにする。
// メトリクスは init 時にしか作れないため、先に全部宣言しておく
const ENDPOINT_NAMES = [
  'GET /api/timeline/all',
  'GET /api/timeline/following',
  'GET /api/posts/{id}',
  'GET /api/posts/{id}/comments',
  'GET /api/users/{username}',
  'GET /api/users/{username}/posts',
  'GET /api/users/search',
  'GET /api/auth/me',
  'POST /api/posts/{id}/like',
  'DELETE /api/posts/{id}/like',
  'POST /api/users/{id}/follow',
  'DELETE /api/users/{id}/follow',
  'POST /api/posts/{id}/comments',
  'POST /api/posts',
  'POST /api/posts (image)',
  'DELETE /api/posts/{id}',
];
const endpointTrends = Object.fromEntries(
  ENDPOINT_NAMES.map((name) => [
    name,
    // 例: 'GET /api/posts/{id}' → ep_GET_api_posts_id
    new Trend(`ep_${name.replace(/[^A-Za-z]+/g, '_').replace(/_$/, '')}`, true),
  ]),
);

function params(session, type, name, extraHeaders = {}) {
  return {
    headers: { ...bearer(session), ...extraHeaders },
    tags: { type, name },
  };
}

function verify(res, name, expectedStatus) {
  if (res.status === 401) {
    // トークン切れ。次の反復でログインし直させる
    invalidateSession();
  }
  check(res, { [`${name}: ${expectedStatus}`]: (r) => r.status === expectedStatus });
  if (endpointTrends[name]) {
    endpointTrends[name].add(res.timings.duration);
  }
  return res;
}

function query(obj) {
  const parts = Object.entries(obj)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`);
  return parts.length ? `?${parts.join('&')}` : '';
}

// ---- read ----

/** tab は 'all' か 'following'。フロントエンドと同じく、1 ページ目は cursor を付けない。 */
export function getTimeline(session, tab, { cursor, limit } = {}) {
  const name = `GET /api/timeline/${tab}`;
  const res = http.get(
    `${BASE_URL}/api/timeline/${tab}${query({ cursor, limit })}`,
    params(session, 'read', name),
  );
  return verify(res, name, 200);
}

export function getPost(session, postId) {
  const name = 'GET /api/posts/{id}';
  return verify(http.get(`${BASE_URL}/api/posts/${postId}`, params(session, 'read', name)), name, 200);
}

export function getComments(session, postId, { cursor, limit } = {}) {
  const name = 'GET /api/posts/{id}/comments';
  const res = http.get(
    `${BASE_URL}/api/posts/${postId}/comments${query({ cursor, limit })}`,
    params(session, 'read', name),
  );
  return verify(res, name, 200);
}

export function getProfile(session, username) {
  const name = 'GET /api/users/{username}';
  return verify(http.get(`${BASE_URL}/api/users/${username}`, params(session, 'read', name)), name, 200);
}

export function getUserPosts(session, username, { cursor, limit } = {}) {
  const name = 'GET /api/users/{username}/posts';
  const res = http.get(
    `${BASE_URL}/api/users/${username}/posts${query({ cursor, limit })}`,
    params(session, 'read', name),
  );
  return verify(res, name, 200);
}

export function searchUsers(session, q) {
  const name = 'GET /api/users/search';
  return verify(http.get(`${BASE_URL}/api/users/search${query({ q })}`, params(session, 'read', name)), name, 200);
}

export function getMe(session) {
  const name = 'GET /api/auth/me';
  return verify(http.get(`${BASE_URL}/api/auth/me`, params(session, 'read', name)), name, 200);
}

// ---- write ----

export function like(session, postId) {
  const name = 'POST /api/posts/{id}/like';
  return verify(http.post(`${BASE_URL}/api/posts/${postId}/like`, null, params(session, 'write', name)), name, 200);
}

export function unlike(session, postId) {
  const name = 'DELETE /api/posts/{id}/like';
  return verify(http.del(`${BASE_URL}/api/posts/${postId}/like`, null, params(session, 'write', name)), name, 200);
}

export function follow(session, userId) {
  const name = 'POST /api/users/{id}/follow';
  return verify(http.post(`${BASE_URL}/api/users/${userId}/follow`, null, params(session, 'write', name)), name, 200);
}

export function unfollow(session, userId) {
  const name = 'DELETE /api/users/{id}/follow';
  return verify(http.del(`${BASE_URL}/api/users/${userId}/follow`, null, params(session, 'write', name)), name, 200);
}

export function createComment(session, postId, body) {
  const name = 'POST /api/posts/{id}/comments';
  const res = http.post(
    `${BASE_URL}/api/posts/${postId}/comments`,
    JSON.stringify({ body }),
    params(session, 'write', name, { 'Content-Type': 'application/json' }),
  );
  return verify(res, name, 201);
}

/**
 * テキストだけの投稿。
 *
 * 投稿 API は画像なしでも multipart/form-data しか受け付けない（PostController）。
 * k6 の http.post にオブジェクトを渡すと、ファイルを含まない限り x-www-form-urlencoded に
 * なってしまうため、multipart の本文を自分で組み立てる。
 */
export function createTextPost(session, body) {
  const name = 'POST /api/posts';
  const boundary = `----k6perf${Date.now()}${Math.floor(Math.random() * 1e9)}`;
  const payload =
    `--${boundary}\r\n` +
    'Content-Disposition: form-data; name="body"\r\n\r\n' +
    `${body}\r\n` +
    `--${boundary}--\r\n`;
  const res = http.post(
    `${BASE_URL}/api/posts`,
    payload,
    params(session, 'write', name, { 'Content-Type': `multipart/form-data; boundary=${boundary}` }),
  );
  return verify(res, name, 201);
}

// ---- upload ----

// 既定のアップロード画像は 1×1 px の PNG（約 70 バイト）。リポジトリに画像ファイルを置かずに済む。
// これで測れるのは「形式検証 + S3 への PutObject の往復」で、転送量の影響は含まない。
// 実サイズに近い画像で測りたいときは perf/k6/assets/upload.jpg を置く（git 管理外、2 MB 以下）
const TINY_PNG_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

function loadUploadImage() {
  try {
    // open() は init 時にしか呼べない。ファイルが無ければ例外になるので既定の画像に切り替える
    return { data: open('../assets/upload.jpg', 'b'), filename: 'upload.jpg', contentType: 'image/jpeg' };
  } catch (_) {
    return {
      data: encoding.b64decode(TINY_PNG_BASE64, 'std'),
      filename: 'tiny.png',
      contentType: 'image/png',
    };
  }
}
const uploadImage = loadUploadImage();

/** 画像 1 枚つきの投稿。ファイルを含むオブジェクトを渡すと、k6 が multipart を組み立ててくれる。 */
export function createImagePost(session, body) {
  const name = 'POST /api/posts (image)';
  const res = http.post(
    `${BASE_URL}/api/posts`,
    { body, images: http.file(uploadImage.data, uploadImage.filename, uploadImage.contentType) },
    params(session, 'upload', name),
  );
  return verify(res, name, 201);
}

export function deletePost(session, postId) {
  const name = 'DELETE /api/posts/{id}';
  return verify(http.del(`${BASE_URL}/api/posts/${postId}`, null, params(session, 'write', name)), name, 204);
}

// ---- auth（refresh / logout は Cookie だけで認証する。k6 の Cookie jar は VU ごと） ----

export function refresh() {
  const name = 'POST /api/auth/refresh';
  const res = http.post(`${BASE_URL}/api/auth/refresh`, null, { tags: { type: 'auth', name } });
  check(res, { [`${name}: 200`]: (r) => r.status === 200 });
  return res;
}

export function logout() {
  const name = 'POST /api/auth/logout';
  const res = http.post(`${BASE_URL}/api/auth/logout`, null, { tags: { type: 'auth', name } });
  check(res, { [`${name}: 204`]: (r) => r.status === 204 });
  return res;
}
