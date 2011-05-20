package com.intellij.flex.uiDesigner.libraries;

import com.intellij.facet.FacetManager;
import com.intellij.lang.javascript.flex.AutogeneratedLibraryUtils;
import com.intellij.lang.javascript.flex.FlexFacet;
import com.intellij.lang.javascript.flex.FlexUtils;
import com.intellij.lang.javascript.flex.sdk.AirMobileSdkType;
import com.intellij.lang.javascript.flex.sdk.AirSdkType;
import com.intellij.lang.javascript.flex.sdk.FlexSdkType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LibraryCollector {
  final List<Library> externalLibraries = new ArrayList<Library>();
  final List<Library> sdkLibraries = new ArrayList<Library>();
  private final LibraryManager libraryManager;

  // user can set flex sdk and autogenerated sdk for facet simultaneous, so we prevent duplicates in externalLibraries
  private boolean flexSdkRegistered = false;
  private String flexSdkVersion;
  private Sdk flexSdk;

  public LibraryCollector(LibraryManager libraryManager) {
    this.libraryManager = libraryManager;
  }

  public String getFlexSdkVersion() {
    return flexSdkVersion;
  }

  public Sdk getFlexSdk() {
    return flexSdk;
  }

  private static boolean isAutomationOrUselessLibrary(String name) {
    return name.startsWith("qtp") || name.startsWith("automation")
           || name.equals("flex.swc") /* flex.swc is only aggregation library */
           || name.equals("servicemonitor.swc")  /* aircore contains all classes */
           || name.equals("utilities.swc")  /* flex sdk 4.1 */
           || name.equals("core.swc") /* hero (4.5) aggregation library */
           || name.equals("applicationupdater.swc") /* applicationupdater_ui contains all classes */
           || name.equals("flash-integration.swc") || name.equals("authoringsupport.swc");
  }

  private static boolean isGlobalLibrary(String libFileName) {
    return libFileName.startsWith("airglobal") || libFileName.startsWith("playerglobal");
  }

  private static @Nullable VirtualFile getRealFileIfSwc(final VirtualFile jarFile) {
    if (jarFile.getFileSystem() instanceof JarFileSystem) {
      VirtualFile file = JarFileSystem.getInstance().getVirtualFileForJar(jarFile);
      if (file != null && !file.isDirectory() && "swc".equals(file.getExtension()) && !isGlobalLibrary(file.getName())) {
        return file;
      }
    }

    return null;
  }

  public static String getFlexVersion(Module module) {
    final Sdk sdk = FlexUtils.getFlexSdkForFlexModuleOrItsFlexFacets(module);
    if (sdk == null) {
      return null;
    }

    return sdk.getVersionString();
  }

  public void collect(Module module, Consumer<Library> initializer) {
    Sdk sdk = null;
    for (OrderEntry o : ModuleRootManager.getInstance(module).getOrderEntries()) {
      final DependencyScope scope = (o instanceof ExportableOrderEntry) ? ((ExportableOrderEntry)o).getScope() : DependencyScope.COMPILE;
      // IDEA set incorrect scope for playerglobal/airglobal — must be runtime, so it is meaningless check
      if (scope == DependencyScope.RUNTIME || scope == DependencyScope.TEST) {
        continue;
      }

      if (o instanceof LibraryOrderEntry) {
        collectFromLibraryOrderEnrty((LibraryOrderEntry)o, initializer);
      }
      else if (!flexSdkRegistered && o instanceof JdkOrderEntry) {
        if (libraryManager.isSdkRegistered(sdk, module)) {
          continue;
        }

        final JdkOrderEntry jdkOrderEntry = ((JdkOrderEntry)o);
        SdkType sdkType = jdkOrderEntry.getJdk().getSdkType();
        if (sdkType instanceof FlexSdkType || sdkType instanceof AirSdkType || sdkType instanceof AirMobileSdkType) {
          sdk = jdkOrderEntry.getJdk();
          for (VirtualFile jarFile : jdkOrderEntry.getRootFiles(OrderRootType.CLASSES)) {
            VirtualFile file = getRealFileIfSwc(jarFile);
            if (file != null && !isAutomationOrUselessLibrary(file.getName())) {
              sdkLibraries.add(libraryManager.createOriginalLibrary(file, jarFile, initializer));
            }
          }

          flexSdkRegistered = true;
        }
      }
      else if (o instanceof ModuleOrderEntry) {
        collectLibrariesFromModuleDependency(((ModuleOrderEntry)o).getModule());
      }
    }

    // flexmojos module has Java as module jdk, so, grab flex sdk from facet
    if (sdk == null) {
      FlexFacet facet = FacetManager.getInstance(module).getFacetByType(FlexFacet.ID);
      assert facet != null;
      sdk = facet.getConfiguration().getFlexSdk();
      assert sdk != null;
    }
    else {
      flexSdk = sdk;
    }

    flexSdkVersion = sdk.getVersionString();
    assert flexSdkVersion != null && flexSdkVersion.length() >= 3;
    flexSdkVersion = flexSdkVersion.substring(0, 3);
  }

  private void collectFromLibraryOrderEnrty(LibraryOrderEntry libraryOrderEntry, Consumer<Library> initializer) {
    final boolean isAutogeneratedLibrary = AutogeneratedLibraryUtils.isAutogeneratedLibrary(libraryOrderEntry);
    if (isAutogeneratedLibrary && flexSdkRegistered) {
      return;
    }

    for (VirtualFile jarFile : libraryOrderEntry.getRootFiles(OrderRootType.CLASSES)) {
      VirtualFile file = getRealFileIfSwc(jarFile);
      if (file != null && (!isAutogeneratedLibrary || !isAutomationOrUselessLibrary(file.getName()))) {
        externalLibraries.add(libraryManager.createOriginalLibrary(file, jarFile, initializer));
      }
    }
  }

  // 7
  private static void collectLibrariesFromModuleDependency(Module module) {
    for (OrderEntry o : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (!(o instanceof ExportableOrderEntry) || !((ExportableOrderEntry)o).isExported()) {
        continue;
      }

      final DependencyScope scope = ((ExportableOrderEntry)o).getScope();
      if (scope == DependencyScope.RUNTIME || scope == DependencyScope.TEST) {
        continue;
      }

      if (o instanceof LibraryOrderEntry) {
        //FlexUIDesignerApplicationManager.LOG.error("exported lib in module dependency is prohibited");
      }
    }
  }
}