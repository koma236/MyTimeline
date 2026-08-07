/**
 * API のリクエスト・レスポンス DTO。
 *
 * <p>{@code @NullMarked} により、このパッケージでは「{@code @Nullable} を付けない限り
 * 非 null」が既定になる。API 仕様書（docs/api/openapi.json）の required / nullable は
 * この宣言から生成されるため、null を返しうるフィールドには必ず {@code @Nullable} を
 * 付けること（付け忘れるとフロントエンドの生成型が実態より強くなる）。</p>
 */
@NullMarked
package com.example.mytimeline.dto;

import org.jspecify.annotations.NullMarked;
