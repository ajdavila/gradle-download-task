// Copyright 2013-2016 Michel Kraemer
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.handler.HandlerList;
import org.mortbay.jetty.handler.ResourceHandler;
import org.mortbay.resource.Resource;

/**
 * Base class for unit tests
 * @author Michel Kraemer
 */
public abstract class TestBase {
    /**
     * File name of the first test file
     */
    protected final static String TEST_FILE_NAME = "test.txt";
    
    /**
     * File name of the second test file
     */
    protected final static String TEST_FILE_NAME2 = "test2.txt";
    
    /**
     * Host name of the local machine
     */
    protected static String localHostName;
    
    /**
     * Parent directory of {@link #projectDir}
     */
    private File parentDir;
    
    /**
     * A temporary directory where a virtual test project is stored
     */
    protected File projectDir;
    
    /**
     * A folder for temporary files
     */
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    
    /**
     * The HTTP server to test against
     */
    private Server server;
    
    /**
     * Contents of the first test file with the name {@link #TEST_FILE_NAME}
     */
    protected byte[] contents;
    
    /**
     * Contents of the second test file with the name {@link #TEST_FILE_NAME2}
     */
    protected byte[] contents2;
    
    /**
     * @return the HTTP server used for testing
     */
    protected Server createServer() {
        //run server on any free port
        return new Server(0);
    }
    
    /**
     * Runs an embedded HTTP server and creates test files to serve
     * @throws Exception if the server could not be started
     */
    @Before
    public void setUp() throws Exception {
        server = createServer();
        
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(makeHandlers());
        server.setHandler(handlers);
        server.start();
        
        //create temporary files
        contents = new byte[4096];
        contents2 = new byte[4096];
        for (int i = 0; i < contents.length; ++i) {
            contents[i] = (byte)(Math.random() * 255);
            contents2[i] = (byte)(Math.random() * 255);
        }
        
        parentDir = folder.newFolder("test");
        projectDir = new File(parentDir, "project");
        
        File testFile = folder.newFile(TEST_FILE_NAME);
        FileUtils.writeByteArrayToFile(testFile, contents);
        File testFile2 = folder.newFile(TEST_FILE_NAME2);
        FileUtils.writeByteArrayToFile(testFile2, contents2);
    }
    
    /**
     * Gets the local host name to use for the tests
     * @throws UnknownHostException if the local host name could not be
     * resolved into an address
     * @throws SocketException if an I/O error occurs
     */
    @BeforeClass
    public static void setUpClass() throws UnknownHostException, SocketException {
        try {
            InetAddress.getByName("localhost.localdomain");
            localHostName = "localhost.localdomain";
        } catch (UnknownHostException e) {
            localHostName = findSiteLocal();
            if (localHostName == null) {
                localHostName = InetAddress.getLocalHost().getCanonicalHostName();
            }
        }
    }
    
    /**
     * Get a site local IP4 address from the current node's interfaces
     * @return the IP address or <code>null</code> if the address
     * could not be obtained
     * @throws SocketException if an I/O error occurs
     */
    private static String findSiteLocal() throws SocketException {
        Enumeration<NetworkInterface> interfaces =
                NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface n = interfaces.nextElement();
            Enumeration<InetAddress> addresses = n.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress i = addresses.nextElement();
                if (i.isSiteLocalAddress() && i instanceof Inet4Address) {
                    return i.getHostAddress();
                }
            }
        }
        return null;
    }
    
    /**
     * Make the handlers for the HTTP server to test against
     * @return the handlers
     * @throws IOException if the handlers could not be created
     */
    protected Handler[] makeHandlers() throws IOException {
        //serve resources from temporary folder
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setBaseResource(Resource.newResource(
                folder.getRoot().getAbsolutePath()));
        return new Handler[] { resourceHandler, new DefaultHandler() };
    }
    
    /**
     * Stops the embedded HTTP server
     * @throws Exception if the server could not be stopped
     */
    @After
    public void tearDown() throws Exception {
        server.stop();
    }
    
    /**
     * Find a free socket port
     * @return the number of the free port
     * @throws IOException if an IO error occurred
     */
    protected static int findPort() throws IOException {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            return socket.getLocalPort();
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }
    
    /**
     * Makes a Gradle project and creates a download task
     * @return the unconfigured download task
     */
    protected Download makeProjectAndTask() {
        Project parent = ProjectBuilder.builder().withProjectDir(parentDir).build();
        Project project = ProjectBuilder.builder().withParent(parent).withProjectDir(projectDir).build();
        
        Map<String, Object> applyParams = new HashMap<String, Object>();
        applyParams.put("plugin", "de.undercouch.download");
        project.apply(applyParams);
        
        Map<String, Object> taskParams = new HashMap<String, Object>();
        taskParams.put("type", Download.class);
        Download t = (Download)project.task(taskParams, "downloadFile");
        return t;
    }
    
    /**
     * @return the port the embedded HTTP server is listening to
     */
    protected int getServerPort() {
        return server.getConnectors()[0].getLocalPort();
    }
    
    /**
     * Makes a URL for a file provided by the embedded HTTP server
     * @param fileName the file's name
     * @return the URL
     */
    protected String makeSrc(String fileName) {
        return "http://" + localHostName + ":" + getServerPort() + "/" + fileName;
    }
}
