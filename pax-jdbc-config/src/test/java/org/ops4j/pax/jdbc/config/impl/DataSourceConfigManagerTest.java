/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.jdbc.config.impl;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.matches;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;

import javax.sql.DataSource;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.jasypt.encryption.StringEncryptor;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.jdbc.DataSourceFactory;
import org.osgi.util.tracker.ServiceTracker;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class DataSourceConfigManagerTest {

    private static final String TESTPID = "testpid";
    private static final String H2_DRIVER_CLASS = "org.h2.Driver";

    @Test
    public void testUpdatedAndDeleted() throws Exception {
        IMocksControl c = EasyMock.createControl();
        BundleContext context = c.createMock(BundleContext.class);

        final DataSourceFactory dsf = c.createMock(DataSourceFactory.class);
        String expectedFilter = "(&(objectClass=org.osgi.service.jdbc.DataSourceFactory)(osgi.jdbc.driver.class=org.h2.Driver))";

        Filter filter = FrameworkUtil.createFilter(expectedFilter);
        expect(context.createFilter(expectedFilter)).andReturn(filter);
        expect(context.getProperty("org.osgi.framework.version")).andReturn("1.5.0");
        context.addServiceListener(EasyMock.anyObject(ServiceListener.class),
            EasyMock.eq(expectedFilter));
        expectLastCall();

        ServiceReference ref = c.createMock(ServiceReference.class);
        ServiceReference[] refs = new ServiceReference[] { ref };
        expect(context.getServiceReferences((String) null, expectedFilter)).andReturn(refs);

        expect(context.getService(ref)).andReturn(dsf);
        
        DataSource ds = c.createMock(DataSource.class);
        expect(dsf.createDataSource(EasyMock.anyObject(Properties.class))).andReturn(ds);

        ServiceRegistration sreg = c.createMock(ServiceRegistration.class);
        expect(context.registerService(anyString(), eq(ds), anyObject(Dictionary.class))).andReturn(sreg);

        Decryptor decryptor = c.createMock(Decryptor.class);

        Dictionary<String, String> properties = new Hashtable<String, String>();
        properties.put(DataSourceFactory.OSGI_JDBC_DRIVER_CLASS, H2_DRIVER_CLASS);
        properties.put(DataSourceFactory.JDBC_DATABASE_NAME, "mydbname");
        expect(decryptor.decrypt(anyObject(Dictionary.class))).andReturn(properties);

        DataSourceConfigManager dsManager = new DataSourceConfigManager(context, decryptor);

        // Test config created
        c.replay();
        dsManager.updated(TESTPID, properties);
        c.verify();

        c.reset();
        context.removeServiceListener(EasyMock.anyObject(ServiceListener.class));
        expectLastCall();
        expect(context.ungetService(ref)).andReturn(true);
        sreg.unregister();
        expectLastCall();
        
        // Test config removed
        c.replay();
        dsManager.updated(TESTPID, null);
        c.verify();
    }

    @Test
    public void testNotEnoughInfoToFindDriver() {
        IMocksControl c = EasyMock.createControl();
        BundleContext context = c.createMock(BundleContext.class);

        Decryptor decryptor = c.createMock(Decryptor.class);
        Dictionary<String, String> properties = new Hashtable<String, String>();
        properties.put("other", "value");
        expect(decryptor.decrypt(EasyMock.anyObject(Dictionary.class))).andReturn(properties);
        DataSourceConfigManager dsManager = new DataSourceConfigManager(context, decryptor);

        c.replay();
        try {
            dsManager.updated(TESTPID, properties);
        }
        catch (ConfigurationException e) {
            assertEquals("Could not determine driver to use. "
                + "Specify either osgi.jdbc.driver.class or osgi.jdbc.driver.name", e.getReason());
        }
        c.verify();
    }

    @Test
    public void testEncryptor() throws Exception {
        IMocksControl c = EasyMock.createControl();
        BundleContext context = c.createMock(BundleContext.class);

        final DataSourceFactory dsf = c.createMock(DataSourceFactory.class);
        String expectedFilter = "(&(objectClass=org.osgi.service.jdbc.DataSourceFactory)(osgi.jdbc.driver.class=org.h2.Driver))";

        Filter filter = FrameworkUtil.createFilter(expectedFilter);
        expect(context.createFilter(expectedFilter)).andReturn(filter);
        expect(context.getProperty("org.osgi.framework.version")).andReturn("1.5.0");
        context.addServiceListener(EasyMock.anyObject(ServiceListener.class),
            EasyMock.eq(expectedFilter));
        expectLastCall();

        ServiceReference ref = c.createMock(ServiceReference.class);
        ServiceReference[] refs = new ServiceReference[] { ref };
        expect(context.getServiceReferences((String) null, expectedFilter)).andReturn(refs);

        expect(context.getService(ref)).andReturn(dsf);

        DataSource ds = c.createMock(DataSource.class);
        expect(dsf.createDataSource(EasyMock.anyObject(Properties.class))).andReturn(ds);

        ServiceRegistration sreg = c.createMock(ServiceRegistration.class);
        expect(context.registerService(anyString(), eq(ds), anyObject(Dictionary.class))).andReturn(sreg);

        Decryptor decryptor = createDecryptor(c);
        DataSourceConfigManager dsManager = new DataSourceConfigManager(context, decryptor);

        // Test config created
        c.replay();
        Dictionary<String, String> properties = new Hashtable<String, String>();
        properties.put(DataSourceFactory.OSGI_JDBC_DRIVER_CLASS, H2_DRIVER_CLASS);
        properties.put(DataSourceFactory.JDBC_DATABASE_NAME, "mydbname");
        properties.put(DataSourceFactory.JDBC_PASSWORD, "ENC(ciphertext)");
        dsManager.updated(TESTPID, properties);
        c.verify();

        // the encrypted value is still encrypted
        Assert.assertEquals("ENC(ciphertext)", properties.get(DataSourceFactory.JDBC_PASSWORD));
    }

    private Decryptor createDecryptor(IMocksControl c) throws Exception {
        StringEncryptor encryptor = c.createMock(StringEncryptor.class);
        expect(encryptor.decrypt(matches("ciphertext"))).andReturn("plaintext");

        ServiceTracker encryptorServiceTracker = c.createMock(ServiceTracker.class);
        expect(encryptorServiceTracker.waitForService(anyInt())).andReturn(encryptor);
        Decryptor decryptor = new Decryptor(encryptorServiceTracker);
        return decryptor;
    }
}
