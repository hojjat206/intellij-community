/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ModuleVcsDetector {
  private final Project myProject;
  private final ProjectLevelVcsManagerImpl myVcsManager;

  public ModuleVcsDetector(@NotNull Project project, @NotNull ProjectLevelVcsManager vcsManager, @NotNull StartupManager startupManager) {
    myProject = project;
    myVcsManager = (ProjectLevelVcsManagerImpl) vcsManager;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    
    startupManager.registerStartupActivity(() -> {
      if (myVcsManager.needAutodetectMappings()) {
        autoDetectVcsMappings(true);
      }
      myVcsManager.updateActiveVcss();
    });
    startupManager.registerPostStartupActivity(() -> {
      MessageBusConnection connection = myProject.getMessageBus().connect();
      final MyModulesListener listener = new MyModulesListener();
      connection.subscribe(ProjectTopics.MODULES, listener);
      connection.subscribe(ProjectTopics.PROJECT_ROOTS, listener);
    });
  }

  private class MyModulesListener implements ModuleRootListener, ModuleListener {
    private final List<Pair<String, VcsDirectoryMapping>> myMappingsForRemovedModules = new ArrayList<>();

    @Override
    public void beforeRootsChange(ModuleRootEvent event) {
      myMappingsForRemovedModules.clear();
    }

    @Override
    public void rootsChanged(ModuleRootEvent event) {
      for (Pair<String, VcsDirectoryMapping> mapping : myMappingsForRemovedModules) {
        myVcsManager.removeDirectoryMapping(mapping.second);
      }
      // the check calculates to true only before user has done any change to mappings, i.e. in case modules are detected/added automatically
      // on start etc (look inside)
      if (myVcsManager.needAutodetectMappings()) {
        autoDetectVcsMappings(false);
      }
    }

    @Override
    public void moduleAdded(@NotNull final Project project, @NotNull final Module module) {
      myMappingsForRemovedModules.removeAll(getMappings(module));
      autoDetectModuleVcsMapping(module);
    }

    @Override
    public void beforeModuleRemoved(@NotNull final Project project, @NotNull final Module module) {
      myMappingsForRemovedModules.addAll(getMappings(module));
    }
  }

  private void autoDetectVcsMappings(final boolean tryMapPieces) {
    Set<AbstractVcs> usedVcses = new HashSet<>();
    Map<VirtualFile, AbstractVcs> vcsMap = new HashMap<>();
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for(Module module: moduleManager.getModules()) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      for(VirtualFile file: files) {
        AbstractVcs contentRootVcs = myVcsManager.findVersioningVcs(file);
        if (contentRootVcs != null) {
          vcsMap.put(file, contentRootVcs);
        }
        usedVcses.add(contentRootVcs);
      }
    }
    if (usedVcses.size() == 1) {
      // todo I doubt this is correct, see IDEA-50527
      final AbstractVcs[] abstractVcses = usedVcses.toArray(new AbstractVcs[1]);
      final Module[] modules = moduleManager.getModules();
      final Set<String> contentRoots = new HashSet<>();
      for (Module module : modules) {
        final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
        for (VirtualFile root : roots) {
          contentRoots.add(root.getPath());
        }
      }

      if (abstractVcses [0] != null) {
        final List<VcsDirectoryMapping> vcsDirectoryMappings = new ArrayList<>(myVcsManager.getDirectoryMappings());
        for (Iterator<VcsDirectoryMapping> iterator = vcsDirectoryMappings.iterator(); iterator.hasNext();) {
          final VcsDirectoryMapping mapping = iterator.next();
          if (! contentRoots.contains(mapping.getDirectory())) {
            iterator.remove();
          }
        }
        myVcsManager.setAutoDirectoryMapping("", abstractVcses [0].getName());
        for (VcsDirectoryMapping mapping : vcsDirectoryMappings) {
          myVcsManager.removeDirectoryMapping(mapping);
        }
        myVcsManager.cleanupMappings();
      }
    }
    else if (tryMapPieces) {
      for(Map.Entry<VirtualFile, AbstractVcs> entry: vcsMap.entrySet()) {
        myVcsManager.setAutoDirectoryMapping(entry.getKey().getPath(), entry.getValue() == null ? "" : entry.getValue().getName());
      }
      myVcsManager.cleanupMappings();
    }
  }

  private void autoDetectModuleVcsMapping(final Module module) {
    boolean mappingsUpdated = false;
    final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
    for(VirtualFile file: files) {
      AbstractVcs vcs = myVcsManager.findVersioningVcs(file);
      if (vcs != null && vcs != myVcsManager.getVcsFor(file)) {
        myVcsManager.setAutoDirectoryMapping(file.getPath(), vcs.getName());
        mappingsUpdated = true;
      }
    }
    if (mappingsUpdated) {
      myVcsManager.cleanupMappings();
    }
  }

  private List<Pair<String, VcsDirectoryMapping>> getMappings(final Module module) {
    List<Pair<String, VcsDirectoryMapping>> result = new ArrayList<>();
    final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
    final String moduleName = module.getName();
    for(final VirtualFile file: files) {
      for(final VcsDirectoryMapping mapping: myVcsManager.getDirectoryMappings()) {
        if (FileUtil.toSystemIndependentName(mapping.getDirectory()).equals(file.getPath())) {
          result.add(Pair.create(moduleName, mapping));
          break;
        }
      }
    }
    return result;
  }
}
