package com.example.cutline;

import com.example.cutline.export.ExportController;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

@QualifierMetadata
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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<ExportController> exportControllerProvider;

  private MainActivity_MembersInjector(Provider<ExportController> exportControllerProvider) {
    this.exportControllerProvider = exportControllerProvider;
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectExportController(instance, exportControllerProvider.get());
  }

  public static MembersInjector<MainActivity> create(
      Provider<ExportController> exportControllerProvider) {
    return new MainActivity_MembersInjector(exportControllerProvider);
  }

  @InjectedFieldSignature("com.example.cutline.MainActivity.exportController")
  public static void injectExportController(MainActivity instance,
      ExportController exportController) {
    instance.exportController = exportController;
  }
}
