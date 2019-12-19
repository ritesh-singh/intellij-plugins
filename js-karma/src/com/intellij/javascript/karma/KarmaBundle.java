package com.intellij.javascript.karma;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class KarmaBundle extends DynamicBundle {
  @NonNls private static final String BUNDLE = "com.intellij.javascript.karma.KarmaBundle";
  private static final KarmaBundle INSTANCE = new KarmaBundle();

  private KarmaBundle() { super(BUNDLE); }

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}
