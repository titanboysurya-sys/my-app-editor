package com.example.cutline.export;

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
public final class ExportService_MembersInjector implements MembersInjector<ExportService> {
  private final Provider<ExportController> exportControllerProvider;

  private ExportService_MembersInjector(Provider<ExportController> exportControllerProvider) {
    this.exportControllerProvider = exportControllerProvider;
  }

  @Override
  public void injectMembers(ExportService instance) {
    injectExportController(instance, exportControllerProvider.get());
  }

  public static MembersInjector<ExportService> create(
      Provider<ExportController> exportControllerProvider) {
    return new ExportService_MembersInjector(exportControllerProvider);
  }

  @InjectedFieldSignature("com.example.cutline.export.ExportService.exportController")
  public static void injectExportController(ExportService instance,
      ExportController exportController) {
    instance.exportController = exportController;
  }
}
