/* ==========================================================================
   store.js - localStorage 永続化 + 疑似 API
   docs/features/F01〜F06 の「API エンドポイント案」に 1:1 で対応する関数を提供する。
   本番実装では、この層をそのまま Axios によるバックエンド呼び出しに差し替える想定。

   戻り値は次の 2 形式に統一している。
     成功: { ok: true, data }
     失敗: { ok: false, error: { status, field, message } }
   ========================================================================== */

(function (global) {
  'use strict';

  var MT = global.MT || (global.MT = {});

  var STORAGE_KEY = 'mytimeline.mock.v1';

  /** バリデーション上限値（docs/08_constraints.md の TBD-04 / TBD-05 に対応） */
  var LIMITS = {
    postBody: 280,
    commentBody: 500,
    postImages: 4,
    imageBytes: 5 * 1024 * 1024,
    imageTypes: ['image/jpeg', 'image/png'],
    usernameMin: 3,
    usernameMax: 50,
    displayNameMax: 100,
    passwordMin: 8,
    bioMax: 300,
    pageSize: 5,
    searchQueryMax: 50
  };

  var db = null;

  /* ---------------------------------------------------------------- 永続化 */

  function load() {
    try {
      var raw = global.localStorage.getItem(STORAGE_KEY);
      if (raw) return JSON.parse(raw);
    } catch (e) {
      // 壊れたデータ / localStorage 無効時はシードで作り直す
    }
    return null;
  }

  function save() {
    try {
      global.localStorage.setItem(STORAGE_KEY, JSON.stringify(db));
    } catch (e) {
      // 画像を data URL で持つため容量超過しうる。UI 側で気づけるよう警告する。
      global.console.warn('localStorage への保存に失敗しました', e);
    }
  }

  function init() {
    db = load() || MT.createSeed();
    save();
  }

  function reset() {
    db = MT.createSeed();
    save();
  }

  /* -------------------------------------------------------------- 内部ヘルパ */

  function ok(data) { return { ok: true, data: data }; }

  function fail(status, field, message) {
    return { ok: false, error: { status: status, field: field, message: message } };
  }

  function nextId(kind) {
    var id = db.nextId[kind];
    db.nextId[kind] = id + 1;
    return id;
  }

  function findUserById(id) {
    return db.users.find(function (u) { return u.id === id; }) || null;
  }

  function findUserByUsername(username) {
    var lower = String(username).toLowerCase();
    return db.users.find(function (u) { return u.username.toLowerCase() === lower; }) || null;
  }

  function currentUser() {
    return db.session ? findUserById(db.session.userId) : null;
  }

  function requireAuth() {
    var me = currentUser();
    return me ? null : fail(401, '_form', 'ログインが必要です');
  }

  function countLikes(postId) {
    return db.likes.filter(function (l) { return l.postId === postId; }).length;
  }

  function countComments(postId) {
    return db.comments.filter(function (c) { return c.postId === postId; }).length;
  }

  function likedByMe(postId) {
    var me = currentUser();
    if (!me) return false;
    return db.likes.some(function (l) { return l.postId === postId && l.userId === me.id; });
  }

  function publicUser(user) {
    if (!user) return null;
    return {
      id: user.id,
      username: user.username,
      displayName: user.displayName,
      bio: user.bio || ''
    };
  }

  /** 投稿を画面表示用の形（F02 のレスポンス例）に変換する */
  function toPostView(post) {
    var images = db.postImages
      .filter(function (img) { return img.postId === post.id; })
      .sort(function (a, b) { return a.position - b.position; })
      .map(function (img) { return img.dataUrl; });

    return {
      id: post.id,
      body: post.body,
      images: images,
      author: publicUser(findUserById(post.userId)),
      likeCount: countLikes(post.id),
      commentCount: countComments(post.id),
      likedByMe: likedByMe(post.id),
      isMine: !!currentUser() && currentUser().id === post.userId,
      createdAt: post.createdAt
    };
  }

  function sortNewest(posts) {
    return posts.slice().sort(function (a, b) {
      if (a.createdAt === b.createdAt) return b.id - a.id;
      return a.createdAt < b.createdAt ? 1 : -1;
    });
  }

  /**
   * カーソルベースのページング。
   * cursor には「最後に取得した投稿の id」を渡す（本番のカーソルページングの簡易再現）。
   * 不正なカーソルが来た場合は先頭から取得し直す（F02 の異常系）。
   */
  function paginate(sortedPosts, cursor, limit) {
    var size = Math.min(limit || LIMITS.pageSize, 50);
    var start = 0;
    if (cursor != null) {
      var idx = sortedPosts.findIndex(function (p) { return p.id === cursor; });
      start = idx >= 0 ? idx + 1 : 0;
    }
    var slice = sortedPosts.slice(start, start + size);
    var hasMore = start + size < sortedPosts.length;
    return {
      items: slice.map(toPostView),
      nextCursor: hasMore && slice.length ? slice[slice.length - 1].id : null
    };
  }

  /* ============================================================== F01 認証 */

  /** POST /api/auth/signup */
  function signup(input) {
    var username = String(input.username || '').trim();
    var displayName = String(input.displayName || '').trim();
    var email = String(input.email || '').trim();
    var password = String(input.password || '');
    var bio = String(input.bio || '').trim();

    if (!username) return fail(400, 'username', 'ユーザー名を入力してください');
    if (username.length < LIMITS.usernameMin || username.length > LIMITS.usernameMax) {
      return fail(400, 'username', 'ユーザー名は' + LIMITS.usernameMin + '〜' + LIMITS.usernameMax + '文字で入力してください');
    }
    if (!/^[A-Za-z0-9_]+$/.test(username)) {
      return fail(400, 'username', 'ユーザー名は半角英数字とアンダースコアのみ使用できます');
    }
    if (!displayName) return fail(400, 'displayName', '表示名を入力してください');
    if (displayName.length > LIMITS.displayNameMax) {
      return fail(400, 'displayName', '表示名は' + LIMITS.displayNameMax + '文字以内で入力してください');
    }
    if (!email) return fail(400, 'email', 'メールアドレスを入力してください');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return fail(400, 'email', 'メールアドレスの形式が正しくありません');
    }
    if (password.length < LIMITS.passwordMin) {
      return fail(400, 'password', 'パスワードは' + LIMITS.passwordMin + '文字以上で入力してください');
    }
    if (bio.length > LIMITS.bioMax) {
      return fail(400, 'bio', '自己紹介は' + LIMITS.bioMax + '文字以内で入力してください');
    }

    // UNIQUE 制約（users.username / users.email）
    if (findUserByUsername(username)) {
      return fail(409, 'username', 'このユーザー名はすでに使用されています');
    }
    if (db.users.some(function (u) { return u.email.toLowerCase() === email.toLowerCase(); })) {
      return fail(409, 'email', 'このメールアドレスはすでに登録されています');
    }

    var user = {
      id: nextId('user'),
      username: username,
      displayName: displayName,
      email: email,
      // 本番では BCrypt 等でハッシュ化して password_hash に保存する（平文保存は禁止）
      password: password,
      bio: bio,
      createdAt: new Date().toISOString()
    };
    db.users.push(user);
    db.session = { userId: user.id };
    save();
    return ok(publicUser(user));
  }

  /** POST /api/auth/login */
  function login(identifier, password) {
    var id = String(identifier || '').trim();
    if (!id) return fail(400, 'identifier', 'メールアドレスまたはユーザー名を入力してください');
    if (!password) return fail(400, 'password', 'パスワードを入力してください');

    var lower = id.toLowerCase();
    var user = db.users.find(function (u) {
      return u.username.toLowerCase() === lower || u.email.toLowerCase() === lower;
    });

    // どちらが誤りかは明示しない（F01 異常系）
    if (!user || user.password !== password) {
      return fail(401, '_form', 'メールアドレスまたはパスワードが正しくありません');
    }

    db.session = { userId: user.id };
    save();
    return ok(publicUser(user));
  }

  /** POST /api/auth/logout */
  function logout() {
    db.session = null;
    save();
    return ok(null);
  }

  /** GET /api/auth/me */
  function me() {
    return publicUser(currentUser());
  }

  /** モック専用: シードユーザーへ切り替える（本番には存在しない） */
  function switchUser(userId) {
    if (!findUserById(userId)) return fail(404, '_form', 'ユーザーが見つかりません');
    db.session = { userId: userId };
    save();
    return ok(me());
  }

  /* ========================================================= F02 タイムライン */

  /** GET /api/timeline/following */
  function timelineFollowing(options) {
    var guard = requireAuth();
    if (guard) return guard;
    var meUser = currentUser();

    // 対象 = 自分 + follows(follower_id = 自分) の followee_id
    var targetIds = db.follows
      .filter(function (f) { return f.followerId === meUser.id; })
      .map(function (f) { return f.followeeId; });
    targetIds.push(meUser.id);

    var posts = db.posts.filter(function (p) { return targetIds.indexOf(p.userId) !== -1; });
    return ok(paginate(sortNewest(posts), (options || {}).cursor, (options || {}).limit));
  }

  /** GET /api/timeline/all */
  function timelineAll(options) {
    var guard = requireAuth();
    if (guard) return guard;
    return ok(paginate(sortNewest(db.posts), (options || {}).cursor, (options || {}).limit));
  }

  /* ================================================================ F03 投稿 */

  /** POST /api/posts */
  function createPost(input) {
    var guard = requireAuth();
    if (guard) return guard;

    var body = String(input.body || '').trim();
    var images = input.images || [];

    if (!body && images.length === 0) {
      return fail(400, 'body', '本文を入力するか、画像を添付してください');
    }
    if (body.length > LIMITS.postBody) {
      return fail(400, 'body', '本文は' + LIMITS.postBody + '文字以内で入力してください');
    }
    if (images.length > LIMITS.postImages) {
      return fail(400, 'images', '画像は最大' + LIMITS.postImages + '枚までです');
    }

    var post = {
      id: nextId('post'),
      userId: currentUser().id,
      body: body,
      createdAt: new Date().toISOString()
    };
    db.posts.push(post);

    images.forEach(function (dataUrl, i) {
      db.postImages.push({
        id: nextId('postImage'),
        postId: post.id,
        dataUrl: dataUrl, // 本番は S3 にアップロードして s3_key を保存する
        position: i
      });
    });

    save();
    return ok(toPostView(post));
  }

  /** GET /api/posts/{id} */
  function getPost(postId) {
    var guard = requireAuth();
    if (guard) return guard;
    var post = db.posts.find(function (p) { return p.id === postId; });
    if (!post) return fail(404, '_form', '投稿が見つかりません');
    return ok(toPostView(post));
  }

  /** DELETE /api/posts/{id} - 本人のみ。配下の画像・コメント・いいねもカスケード削除 */
  function deletePost(postId) {
    var guard = requireAuth();
    if (guard) return guard;

    var post = db.posts.find(function (p) { return p.id === postId; });
    if (!post) return fail(404, '_form', '投稿が見つかりません');
    if (post.userId !== currentUser().id) {
      return fail(403, '_form', '自分の投稿のみ削除できます');
    }

    db.posts = db.posts.filter(function (p) { return p.id !== postId; });
    db.postImages = db.postImages.filter(function (i) { return i.postId !== postId; });
    db.comments = db.comments.filter(function (c) { return c.postId !== postId; });
    db.likes = db.likes.filter(function (l) { return l.postId !== postId; });
    save();
    return ok(null);
  }

  /* ============================================================== F04 コメント */

  function toCommentView(comment) {
    return {
      id: comment.id,
      postId: comment.postId,
      body: comment.body,
      author: publicUser(findUserById(comment.userId)),
      isMine: !!currentUser() && currentUser().id === comment.userId,
      createdAt: comment.createdAt
    };
  }

  /** GET /api/posts/{postId}/comments - 古い順で統一 */
  function listComments(postId) {
    var guard = requireAuth();
    if (guard) return guard;
    var items = db.comments
      .filter(function (c) { return c.postId === postId; })
      .sort(function (a, b) {
        if (a.createdAt === b.createdAt) return a.id - b.id;
        return a.createdAt < b.createdAt ? -1 : 1;
      })
      .map(toCommentView);
    return ok(items);
  }

  /** POST /api/posts/{postId}/comments */
  function createComment(postId, body) {
    var guard = requireAuth();
    if (guard) return guard;

    var text = String(body || '').trim();
    if (!text) return fail(400, 'body', 'コメントを入力してください');
    if (text.length > LIMITS.commentBody) {
      return fail(400, 'body', 'コメントは' + LIMITS.commentBody + '文字以内で入力してください');
    }
    if (!db.posts.some(function (p) { return p.id === postId; })) {
      return fail(404, '_form', '投稿が見つかりません');
    }

    var comment = {
      id: nextId('comment'),
      postId: postId,
      userId: currentUser().id,
      body: text,
      createdAt: new Date().toISOString()
    };
    db.comments.push(comment);
    save();
    return ok(toCommentView(comment));
  }

  /** DELETE /api/comments/{id} - 本人のみ */
  function deleteComment(commentId) {
    var guard = requireAuth();
    if (guard) return guard;

    var comment = db.comments.find(function (c) { return c.id === commentId; });
    if (!comment) return fail(404, '_form', 'コメントが見つかりません');
    if (comment.userId !== currentUser().id) {
      return fail(403, '_form', '自分のコメントのみ削除できます');
    }

    db.comments = db.comments.filter(function (c) { return c.id !== commentId; });
    save();
    return ok(null);
  }

  /* ============================================================== F05 いいね */

  /**
   * POST/DELETE /api/posts/{postId}/like のトグル。
   * (postId, userId) の複合 UNIQUE 相当のチェックを行い、二重いいねを防ぐ。
   * すでに同じ状態への要求は冪等に扱う。
   */
  function toggleLike(postId) {
    var guard = requireAuth();
    if (guard) return guard;
    if (!db.posts.some(function (p) { return p.id === postId; })) {
      return fail(404, '_form', '投稿が見つかりません');
    }

    var meId = currentUser().id;
    var existing = db.likes.find(function (l) { return l.postId === postId && l.userId === meId; });

    if (existing) {
      db.likes = db.likes.filter(function (l) { return l !== existing; });
    } else {
      db.likes.push({ id: nextId('like'), postId: postId, userId: meId, createdAt: new Date().toISOString() });
    }
    save();
    return ok({ likeCount: countLikes(postId), likedByMe: !existing });
  }

  /* ================================================== F06 フォロー・ユーザー検索 */

  function toUserCardView(user) {
    var meUser = currentUser();
    return {
      id: user.id,
      username: user.username,
      displayName: user.displayName,
      bio: user.bio || '',
      isMe: !!meUser && meUser.id === user.id,
      followingByMe: !!meUser && db.follows.some(function (f) {
        return f.followerId === meUser.id && f.followeeId === user.id;
      })
    };
  }

  /** GET /api/users/search - username / display_name の部分一致 */
  function searchUsers(query) {
    var guard = requireAuth();
    if (guard) return guard;

    var q = String(query || '').trim().slice(0, LIMITS.searchQueryMax).toLowerCase();
    var users = db.users.filter(function (u) {
      if (!q) return true; // 未入力時は発見用に全ユーザーを表示
      return u.username.toLowerCase().indexOf(q) !== -1
        || u.displayName.toLowerCase().indexOf(q) !== -1;
    });

    // 未入力時は新着ユーザー順、検索時は username 昇順
    users.sort(function (a, b) {
      if (!q) return a.createdAt < b.createdAt ? 1 : -1;
      return a.username < b.username ? -1 : 1;
    });

    return ok(users.map(toUserCardView));
  }

  /** GET /api/users/{username} */
  function getProfile(username) {
    var guard = requireAuth();
    if (guard) return guard;

    var user = findUserByUsername(username);
    if (!user) return fail(404, '_form', 'ユーザーが見つかりません');

    var view = toUserCardView(user);
    view.followCount = db.follows.filter(function (f) { return f.followerId === user.id; }).length;
    view.followerCount = db.follows.filter(function (f) { return f.followeeId === user.id; }).length;
    view.postCount = db.posts.filter(function (p) { return p.userId === user.id; }).length;
    return ok(view);
  }

  /** GET /api/users/{username}/posts */
  function listUserPosts(username, options) {
    var guard = requireAuth();
    if (guard) return guard;

    var user = findUserByUsername(username);
    if (!user) return fail(404, '_form', 'ユーザーが見つかりません');

    var posts = db.posts.filter(function (p) { return p.userId === user.id; });
    return ok(paginate(sortNewest(posts), (options || {}).cursor, (options || {}).limit));
  }

  /**
   * POST/DELETE /api/users/{userId}/follow のトグル。
   * 自分自身は拒否（follows.follower_id <> followee_id のチェック制約相当）。
   * (followerId, followeeId) の複合 UNIQUE で二重フォローを防ぐ。
   */
  function toggleFollow(userId) {
    var guard = requireAuth();
    if (guard) return guard;

    var meId = currentUser().id;
    if (meId === userId) return fail(422, '_form', '自分自身をフォローすることはできません');
    if (!findUserById(userId)) return fail(404, '_form', 'ユーザーが見つかりません');

    var existing = db.follows.find(function (f) {
      return f.followerId === meId && f.followeeId === userId;
    });

    if (existing) {
      db.follows = db.follows.filter(function (f) { return f !== existing; });
    } else {
      db.follows.push({
        id: nextId('follow'),
        followerId: meId,
        followeeId: userId,
        createdAt: new Date().toISOString()
      });
    }
    save();
    return ok({ followingByMe: !existing });
  }

  /* --------------------------------------------------------------- 公開 API */

  MT.store = {
    LIMITS: LIMITS,
    init: init,
    reset: reset,
    listUsers: function () { return db.users.map(publicUser); },

    // F01
    signup: signup,
    login: login,
    logout: logout,
    me: me,
    switchUser: switchUser,

    // F02
    timelineFollowing: timelineFollowing,
    timelineAll: timelineAll,

    // F03
    createPost: createPost,
    getPost: getPost,
    deletePost: deletePost,

    // F04
    listComments: listComments,
    createComment: createComment,
    deleteComment: deleteComment,

    // F05
    toggleLike: toggleLike,

    // F06
    searchUsers: searchUsers,
    getProfile: getProfile,
    listUserPosts: listUserPosts,
    toggleFollow: toggleFollow
  };

})(window);
