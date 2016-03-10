/*
 * Copyright (c) Andreas Billmann <abi@geofroggerfx.de>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.client.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.ResourceBundle;

import org.client.MainApp;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

public abstract class StreamClientController
{
	protected Node view;
    protected String fxmlFilePath;
    protected String resourcePath;
    
    private MainApp application;
    
    /**
     * Constructor, is called before #initialize()
     */
    public StreamClientController() {}
    
   /**
    * Initializes the controller class. This method is automatically called after the fxml file has been loaded.
    */
    @FXML
    private void initialize()
    {
    	// TODO: do something usefu here
    }

    /**
     * Is called by the main application to give a reference back to itself.
     * @param app - main application reference
     */
    public void setApplication(MainApp app)
    {
    	this.application = app;
    }

    public abstract void setFxmlFilePath(String filePath);

/*    @Override
    public void afterPropertiesSet() throws Exception
    {
        loadFXML();
    }*/

    protected final void loadFXML() throws IOException {
        try (InputStream fxmlStream = getClass().getResourceAsStream(fxmlFilePath)) {
            FXMLLoader loader = new FXMLLoader();
            loader.setResources(ResourceBundle.getBundle(this.resourcePath));
            loader.setController(this);
            this.view = (loader.load(fxmlStream));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public Node getView() {
        return view;
    }
}