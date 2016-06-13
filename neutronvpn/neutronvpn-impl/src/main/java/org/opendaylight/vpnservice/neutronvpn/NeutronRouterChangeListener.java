/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.neutronvpn;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.l3.attributes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


public class NeutronRouterChangeListener extends AbstractDataChangeListener<Router> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronRouterChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private NeutronvpnManager nvpnManager;


    public NeutronRouterChangeListener(final DataBroker db, NeutronvpnManager nVpnMgr) {
        super(Router.class);
        broker = db;
        nvpnManager = nVpnMgr;
        registerListener(db);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("N_Router listener Closed");
    }


    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.create(Neutron.class).child(Routers.class).child(Router.class),
                    NeutronRouterChangeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("Neutron Manager Router DataChange listener registration fail!", e);
            throw new IllegalStateException("Neutron Manager Router DataChange listener registration failed.", e);
        }
    }

    private Set<Port> getInterfaces(final Uuid deviceId) {
        final Set<Port> interfaces = new HashSet<>();
        InstanceIdentifier<Ports> path = InstanceIdentifier.create(Neutron.class).child(Ports.class);

        try (ReadOnlyTransaction tx = broker.newReadOnlyTransaction()) {
            final CheckedFuture<Optional<Ports>, ReadFailedException> future = tx.read(LogicalDatastoreType.CONFIGURATION, path);
            Optional<Ports> optional = future.checkedGet();
            if (optional.isPresent()) {
                for (final Port port : optional.get().getPort()) {
                    if (port.getDeviceOwner().equals("network:router_interface") && port.getDeviceId().equals(deviceId.getValue())) {
                        interfaces.add(port);
                    }
                }
            }
        } catch (final ReadFailedException e) {
            LOG.warn("Failed to read {}", path, e);
        }

        return interfaces;
    }

    @Override
    protected void add(InstanceIdentifier<Router> identifier, Router input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding Router : key: " + identifier + ", value=" + input);
        }
        // Create internal VPN
        nvpnManager.createL3Vpn(input.getUuid(), null, null, null, null, null, input.getUuid(), null);
    }

    @Override
    protected void remove(InstanceIdentifier<Router> identifier, Router input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Removing router : key: " + identifier + ", value=" + input);
        }
        Uuid routerId = input.getUuid();
        Set<Port> routerInterfaces = this.getInterfaces(input.getUuid());
        List<Uuid> routerSubnetIds = new ArrayList<>();
        if (!routerInterfaces.isEmpty()) {
            Set<Uuid> uuids = new HashSet<>();
            for (Port port : routerInterfaces) {
                for (FixedIps fixedIps : port.getFixedIps()) {
                    uuids.add(fixedIps.getSubnetId());
                }
            }
            routerSubnetIds.addAll(uuids);
        }
        nvpnManager.handleNeutronRouterDeleted(routerId, routerSubnetIds);
    }

    @Override
    protected void update(InstanceIdentifier<Router> identifier, Router original, Router update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Updating Router : key: " + identifier + ", original value=" + original + ", update value=" +
                    update);
        }
        Uuid routerId = update.getUuid();
        Uuid vpnId = NeutronvpnUtils.getVpnForRouter(broker, routerId, true);
        // internal vpn always present in case external vpn not found
        if (vpnId == null) {
            vpnId = routerId;
        }
        List<Routes> oldRoutes = (original.getRoutes() != null) ? original.getRoutes() : new ArrayList<Routes>();
        List<Routes> newRoutes = (update.getRoutes() != null) ? update.getRoutes() : new ArrayList<Routes>();
        if (!oldRoutes.equals(newRoutes)) {
            Iterator<Routes> iterator = newRoutes.iterator();
            while (iterator.hasNext()) {
                Routes route = iterator.next();
                if (oldRoutes.remove(route)) {
                    iterator.remove();
                }
            }
            nvpnManager.addAdjacencyforExtraRoute(newRoutes, true, null);
            if (!oldRoutes.isEmpty()) {
                nvpnManager.removeAdjacencyforExtraRoute(oldRoutes);
            }
        }
    }
}
