// smoke: 負荷をかける前に、環境とスクリプトが壊れていないことを確かめる。
// load や stress の前に必ず流す。ここで落ちるなら性能以前の問題（シード漏れ・API 変更など）。
//
// 合否: エラー 0 件。レイテンシも docs/05 の基準（p95 1 秒）で見るが、2 VU では参考値。
import { group, sleep } from 'k6';
import { POST_COUNT, SLO_THRESHOLDS, usernameFor } from '../lib/config.js';
import { authFlowUserIdForThisVu, ensureSession, login } from '../lib/auth.js';
import * as api from '../lib/api.js';

export const options = {
  scenarios: {
    // 同値分割: 読み取り・書き込みの各クラスを最低 1 回ずつ通す
    browse_and_react: {
      executor: 'constant-vus',
      exec: 'browseAndReact',
      vus: 2,
      duration: '1m',
    },
    // 状態遷移: ログイン → refresh → refresh → ログアウト
    auth_state_transition: {
      executor: 'per-vu-iterations',
      exec: 'authStateTransition',
      vus: 1,
      iterations: 3,
      maxDuration: '1m',
    },
  },
  thresholds: {
    ...SLO_THRESHOLDS,
    // smoke は 1 件でも失敗したら不合格
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

function randomPostId() {
  // 新しい投稿ほど反応が多いので、末尾 1,000 件から選ぶ
  return POST_COUNT - Math.floor(Math.random() * Math.min(1000, POST_COUNT));
}

export function browseAndReact() {
  const session = ensureSession();

  group('read: 全体タイムライン（cursor で 2 ページ目まで）', () => {
    const first = api.getTimeline(session, 'all').json();
    if (first.nextCursor !== null) {
      api.getTimeline(session, 'all', { cursor: first.nextCursor });
    }
  });

  group('read: 境界値 limit=50（上限）', () => {
    api.getTimeline(session, 'all', { limit: 50 });
  });

  group('read: フォロー中タイムライン', () => {
    api.getTimeline(session, 'following');
  });

  const postId = randomPostId();
  group('read: 投稿詳細とコメント', () => {
    api.getPost(session, postId);
    api.getComments(session, postId);
  });

  group('read: プロフィール・ユーザー投稿・検索', () => {
    const someone = usernameFor(1 + Math.floor(Math.random() * 100));
    api.getProfile(session, someone);
    api.getUserPosts(session, someone);
    api.searchUsers(session, 'perf_user_0001');
    api.getMe(session);
  });

  group('write: 状態遷移 いいね → 解除', () => {
    api.like(session, postId);
    api.unlike(session, postId);
  });

  group('write: 状態遷移 フォロー → 解除', () => {
    // 自分自身はフォローできない（SelfFollowException）ので必ず別人を選ぶ
    const target = session.userId === 1 ? 2 : 1;
    api.follow(session, target);
    api.unfollow(session, target);
  });

  group('write: コメント投稿・テキスト投稿（multipart）→ 削除', () => {
    api.createComment(session, postId, `smoke comment vu=${__VU} iter=${__ITER}`);
    const created = api.createTextPost(session, `smoke post vu=${__VU} iter=${__ITER}`).json();
    api.deletePost(session, created.id);
  });

  sleep(1);
}

export function authStateTransition() {
  group('auth: ログイン → refresh × 2 → ログアウト', () => {
    // 専用ユーザーを使う。ここでセッションを壊しても browse_and_react の VU には影響しない
    login(usernameFor(authFlowUserIdForThisVu()));
    // ローテーション: 2 回目は 1 回目で受け取った新しい Cookie で通る
    api.refresh();
    api.refresh();
    api.logout();
  });
  sleep(1);
}
