/*
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright (c) 2009 Pentaho Corporation..  All rights reserved.
 */
package org.pentaho.di.ui.repository.repositoryexplorer.abs;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.AbsSecurityManager;
import org.pentaho.di.repository.IAbsSecurityProvider;
import org.pentaho.di.repository.SecurityManagerRegistery;
import org.pentaho.di.repository.pur.PluginLicenseVerifier;
import org.pentaho.di.ui.repository.repositoryexplorer.RepositoryExplorer;
import org.pentaho.di.ui.repository.repositoryexplorer.abs.controller.AbsController;
import org.pentaho.di.ui.repository.repositoryexplorer.abs.controller.ChangedWarningController;
import org.pentaho.di.ui.repository.repositoryexplorer.abs.controller.RepositoryExplorerController;
import org.pentaho.di.ui.repository.repositoryexplorer.abs.model.UIAbsRepositoryRole;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIObjectRegistery;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.di.ui.spoon.SpoonLifecycleListener;
import org.pentaho.di.ui.spoon.SpoonPerspective;
import org.pentaho.di.ui.spoon.SpoonPlugin;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.XulOverlay;
import org.pentaho.ui.xul.components.XulMenuitem;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.components.XulToolbarbutton;
import org.pentaho.ui.xul.containers.XulMenu;
import org.pentaho.ui.xul.dom.Document;
import org.pentaho.ui.xul.impl.DefaultXulOverlay;
import org.pentaho.ui.xul.impl.XulEventHandler;

public class AbsSpoonPlugin implements SpoonPlugin, SpoonLifecycleListener{
  
  private XulDomContainer spoonXulContainer = null;
  private RepositoryExplorerController repositoryExplorerEventHandler = new RepositoryExplorerController();
  private ChangedWarningController transChangedWarningEventHandler = new ChangedWarningController() {
    @Override
    public String getXulDialogId() {
      return "trans-graph-changed-warning-dialog"; //$NON-NLS-1$
    }
  };
  
  private ChangedWarningController jobChangedWarningEventHandler = new ChangedWarningController() {
    @Override
    public String getXulDialogId() {
      return "changed-warning-dialog"; //$NON-NLS-1$
    }
  };
  
  private ResourceBundle messages = new ResourceBundle() {

    @Override
    public Enumeration<String> getKeys() {
      return null;
    }

    @Override
    protected Object handleGetObject(String key) {
      return BaseMessages.getString(AbsSpoonPlugin.class, key);
    }
    
  }; 
  
  public AbsSpoonPlugin() {
    PluginLicenseVerifier.verify();
    RepositoryExplorer.setSecurityControllerClass(AbsController.class);
  }
  public Map<String, List<XulEventHandler>> getEventHandlers() {
    HashMap<String, List<XulEventHandler>> handlerMap = new HashMap<String, List<XulEventHandler>>();
    handlerMap.put("repository-explorer", Collections.singletonList((XulEventHandler) repositoryExplorerEventHandler)); //$NON-NLS-1$
    
    handlerMap.put("trans-graph-changed-warning-dialog", Collections.singletonList((XulEventHandler) transChangedWarningEventHandler)); //$NON-NLS-1$
    
    handlerMap.put("job-graph-changed-warning-dialog", Collections.singletonList((XulEventHandler) jobChangedWarningEventHandler)); //$NON-NLS-1$
    return handlerMap;
  }

  public Map<String, List<XulOverlay>> getOverlays() {
  	HashMap<String, List<XulOverlay>> hash = new HashMap<String, List<XulOverlay>>();
  	
  	XulOverlay overlay = new DefaultXulOverlay("org/pentaho/di/ui/repository/repositoryexplorer/abs/xul/abs-layout-overlay.xul"); //$NON-NLS-1$
  	hash.put("action-based-security", Collections.singletonList((XulOverlay) overlay)); //$NON-NLS-1$
  
  	return hash;
  }

  public SpoonLifecycleListener getLifecycleListener() {
    return this;
  }

  public SpoonPerspective getPerspective() {
    return null;
  }
  public void onEvent(SpoonLifeCycleEvent evt) {
    try {
      switch(evt) {
        case MENUS_REFRESHED:
        case REPOSITORY_CHANGED:
        case REPOSITORY_CONNECTED:
          doOnSecurityUpdate();
          break;
        case REPOSITORY_DISCONNECTED:
          doOnSecurityCleanup();
          break;
        case STARTUP:
          doOnStartup();
          break;
        case SHUTDOWN:
          doOnShutdown();
          break;
      }
    } catch (KettleException e) {
      try {
        if(Spoon.getInstance() != null) { // Make sure spoon has been initialized first
          if(spoonXulContainer == null) {
            spoonXulContainer = Spoon.getInstance().getMainSpoonContainer();
          }
          XulMessageBox messageBox = (XulMessageBox) spoonXulContainer.getDocumentRoot().createElement("messagebox");//$NON-NLS-1$
          messageBox.setTitle(messages.getString("Dialog.Success"));//$NON-NLS-1$
          messageBox.setAcceptLabel(messages.getString("Dialog.Ok"));//$NON-NLS-1$
          messageBox.setMessage(messages.getString("AbsController.RoleActionPermission.Success"));//$NON-NLS-1$
          messageBox.open();
        }
      } catch (Exception ex) {
        e.printStackTrace();
      }
    }
  }
  
  private void doOnStartup() {
    SecurityManagerRegistery.getInstance().registerSecurityManager(AbsSecurityManager.class);
    UIObjectRegistery.getInstance().registerUIRepositoryRoleClass(UIAbsRepositoryRole.class);
  }
  
  private void doOnShutdown() {
  }
  
  /**
   * Override UI elements to reflect the users capabilities as described by their
   * permission levels
   */
  private void doOnSecurityUpdate() throws KettleException {
    if(Spoon.getInstance() != null) { // Make sure spoon has been initialized first
      if(spoonXulContainer == null) {
        spoonXulContainer = Spoon.getInstance().getMainSpoonContainer();
      }
      
      Object o = Spoon.getInstance().getSecurityManager();
      
      if(o instanceof IAbsSecurityProvider) {
        IAbsSecurityProvider securityProvider = (IAbsSecurityProvider)o;

        // Execute credential lookup
        enableCreatePermission(securityProvider.isAllowed(AbsSecurityManager.CREATE_CONTENT_ACTION));
        enableReadPermission(securityProvider.isAllowed(AbsSecurityManager.READ_CONTENT_ACTION));
        enableAdminPermission(securityProvider.isAllowed(AbsSecurityManager.ADMINISTER_SECURITY_ACTION));
      }
    }
  }
  private void doOnSecurityCleanup() {
  }
  
  private void enableCreatePermission(boolean createPermitted) {
    Document doc = spoonXulContainer.getDocumentRoot();
    
    // Main spoon toolbar
    ((XulToolbarbutton)doc.getElementById("toolbar-file-new")).setDisabled(!createPermitted); //$NON-NLS-1$
    ((XulToolbarbutton)doc.getElementById("toolbar-file-save")).setDisabled(!createPermitted); //$NON-NLS-1$
    ((XulToolbarbutton)doc.getElementById("toolbar-file-save-as")).setDisabled(!createPermitted); //$NON-NLS-1$
    
    // Popup menus
    ((XulMenuitem) doc.getElementById("trans-class-new")).setDisabled(!createPermitted); //$NON-NLS-1$
    ((XulMenuitem) doc.getElementById("job-class-new")).setDisabled(!createPermitted); //$NON-NLS-1$
    
    // Main spoon menu
    ((XulMenu) doc.getElementById("file-new")).setDisabled(!createPermitted); //$NON-NLS-1$
    ((XulMenuitem) doc.getElementById("file-save")).setDisabled(!createPermitted); //$NON-NLS-1$
    ((XulMenuitem) doc.getElementById("file-save-as")).setDisabled(!createPermitted); //$NON-NLS-1$
    ((XulMenuitem) doc.getElementById("file-close")).setDisabled(!createPermitted); //$NON-NLS-1$
    
    // Update repository explorer
    repositoryExplorerEventHandler.setCreatePermissionGranted(createPermitted);
    transChangedWarningEventHandler.setSavePermitted(createPermitted);
    jobChangedWarningEventHandler.setSavePermitted(createPermitted);
  }
  
  private void enableReadPermission(boolean readPermitted) {
    repositoryExplorerEventHandler.setReadPermissionGranted(readPermitted);
  }
  
  private void enableAdminPermission(boolean adminPermitted) {
  }
  
}