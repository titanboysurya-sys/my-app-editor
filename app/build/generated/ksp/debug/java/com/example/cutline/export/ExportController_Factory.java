package com.example.cutline.export;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class ExportController_Factory implements Factory<ExportController> {
  private final Provider<Context> contextProvider;

  private ExportController_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public ExportController get() {
    return newInstance(contextProvider.get());
  }

  public static ExportController_Factory create(Provider<Context> contextProvider) {
    return new ExportController_Factory(contextProvider);
  }

  public static ExportController newInstance(Context context) {
    return new ExportController(context);
  }
}
