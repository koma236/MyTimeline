// 利用者の行動モデル。1 反復 = 1 つの行動 + 思考時間。
//
// 比率は SNS の読み取り偏重を反映した初期値で、実トラフィックの計測値ではない。
// 変えると前回の結果と比較できなくなるので、変えたら docs/13 のベースラインも取り直すこと。
import { sleep } from 'k6';
import { POST_COUNT, USER_COUNT, usernameFor } from './config.js';
import { ensureSession } from './auth.js';
import * as api from './api.js';

function randomInt(min, max) {
  return min + Math.floor(Math.random() * (max - min + 1));
}

/** 思考時間。人は 1 操作ごとに 1〜3 秒は画面を見ている。これが無いと VU 数と利用者数が対応しない。 */
function think() {
  sleep(1 + Math.random() * 2);
}

/** 新しい投稿ほど選ばれやすい投稿 id（シードの反応の偏りと同じ向き）。 */
function recentPostId() {
  return POST_COUNT - Math.floor(Math.min(1000, POST_COUNT) * Math.random() ** 2);
}

function pickPostFrom(timelineRes) {
  const posts = timelineRes.status === 200 ? timelineRes.json('posts') : [];
  return posts && posts.length > 0 ? posts[randomInt(0, posts.length - 1)].id : recentPostId();
}

// ---- 行動 ----

/** 全体タイムラインを 1〜3 ページ読む。2 ページ目以降はフロントエンドと同じく cursor で辿る。 */
function browseAll(session) {
  let res = api.getTimeline(session, 'all');
  const pages = Math.random() < 0.6 ? (Math.random() < 0.5 ? 3 : 2) : 1;
  for (let page = 2; page <= pages; page += 1) {
    const cursor = res.status === 200 ? res.json('nextCursor') : null;
    if (cursor === null || cursor === undefined) break;
    think();
    res = api.getTimeline(session, 'all', { cursor });
  }
}

function browseFollowing(session) {
  const res = api.getTimeline(session, 'following');
  const cursor = res.status === 200 ? res.json('nextCursor') : null;
  if (cursor && Math.random() < 0.4) {
    think();
    api.getTimeline(session, 'following', { cursor });
  }
}

/** タイムラインで見かけた投稿を開く。 */
function readPost(session) {
  const postId = pickPostFrom(api.getTimeline(session, 'all'));
  think();
  api.getPost(session, postId);
  api.getComments(session, postId);
}

function viewProfile(session) {
  // シードの投稿者は id の小さいユーザーに偏っているので、見に行く先も同じ向きに偏らせる
  const username = usernameFor(1 + Math.floor(USER_COUNT * Math.random() ** 3));
  api.getProfile(session, username);
  api.getUserPosts(session, username);
}

function search(session) {
  // 数十件ヒットする検索語と、1 件も当たらない検索語（全件走査して 0 件 = TBD-12 の最悪ケース）を混ぜる
  const q = Math.random() < 0.8 ? `perf_user_00${randomInt(10, 99)}` : `nohit${randomInt(1000, 9999)}`;
  api.searchUsers(session, q);
}

/** 状態遷移: いいね → 解除。元に戻すので、実行を重ねてもいいね数が増え続けない。 */
function likeUnlike(session) {
  const postId = recentPostId();
  api.like(session, postId);
  think();
  api.unlike(session, postId);
}

/** 状態遷移: フォロー → 解除。 */
function followUnfollow(session) {
  let target = randomInt(1, USER_COUNT);
  if (target === session.userId) {
    // 自分自身はフォローできない（SelfFollowException）
    target = (target % USER_COUNT) + 1;
  }
  api.follow(session, target);
  think();
  api.unfollow(session, target);
}

function comment(session) {
  api.createComment(session, recentPostId(), `perf comment vu=${__VU} iter=${__ITER}`);
}

function post(session) {
  api.createTextPost(session, `perf post vu=${__VU} iter=${__ITER} ${'x'.repeat(randomInt(10, 120))}`);
}

// 同値分割: 読み取り 88 % / 書き込み 12 %。合計が 100 になるようにしておくと比率を読みやすい
const ACTIONS = [
  { weight: 50, run: browseAll },
  { weight: 20, run: browseFollowing },
  { weight: 8, run: readPost },
  { weight: 5, run: viewProfile },
  { weight: 5, run: search },
  { weight: 5, run: likeUnlike },
  { weight: 3, run: comment },
  { weight: 3, run: post },
  { weight: 1, run: followUnfollow },
];
const TOTAL_WEIGHT = ACTIONS.reduce((sum, a) => sum + a.weight, 0);

function pickAction() {
  let r = Math.random() * TOTAL_WEIGHT;
  for (const action of ACTIONS) {
    r -= action.weight;
    if (r < 0) return action;
  }
  return ACTIONS[0];
}

/** load / stress / spike / soak が共有する利用者シナリオ。 */
export function userJourney() {
  const session = ensureSession();
  pickAction().run(session);
  think();
}

/** 画像つき投稿。MinIO とリクエストサイズの影響が大きいので、低頻度の独立シナリオとして流す。 */
export function uploadJourney() {
  const session = ensureSession();
  api.createImagePost(session, `perf image post vu=${__VU} iter=${__ITER}`);
}
