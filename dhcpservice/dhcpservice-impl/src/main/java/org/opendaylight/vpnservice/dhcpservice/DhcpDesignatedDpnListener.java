/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.dhcpservice;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.vpnservice.datastoreutils.AsyncClusteredDataChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.dhcp.rev160428.DesignatedSwitchesForExternalTunnels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.dhcp.rev160428.designated.switches._for.external.tunnels.DesignatedSwitchForTunnel;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpDesignatedDpnListener extends AsyncClusteredDataChangeListenerBase<DesignatedSwitchForTunnel, DhcpDesignatedDpnListener> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpDesignatedDpnListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private DataBroker broker;

    public DhcpDesignatedDpnListener(final DhcpExternalTunnelManager dhcpExternalTunnelManager, final DataBroker broker) {
        super(DesignatedSwitchForTunnel.class, DhcpDesignatedDpnListener.class);
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.broker = broker;
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DhcpDesignatedDpnListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.debug("DhcpDesignatedDpnListener Listener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<DesignatedSwitchForTunnel> identifier, DesignatedSwitchForTunnel del) {
        dhcpExternalTunnelManager.removeFromLocalCache(BigInteger.valueOf(del.getDpId()), del.getTunnelRemoteIpAddress(), del.getElanInstanceName());
        dhcpExternalTunnelManager.unInstallDhcpFlowsForVms(del.getElanInstanceName(), del.getTunnelRemoteIpAddress(), DhcpServiceUtils.getListOfDpns(broker));
    }

    @Override
    protected void update(InstanceIdentifier<DesignatedSwitchForTunnel> identifier, DesignatedSwitchForTunnel original,
            DesignatedSwitchForTunnel update) {
        BigInteger designatedDpnId = BigInteger.valueOf(update.getDpId());
        IpAddress tunnelRemoteIpAddress = update.getTunnelRemoteIpAddress();
        String elanInstanceName = update.getElanInstanceName();
        dhcpExternalTunnelManager.removeFromLocalCache(BigInteger.valueOf(original.getDpId()), original.getTunnelRemoteIpAddress(), original.getElanInstanceName());
        dhcpExternalTunnelManager.updateLocalCache(designatedDpnId, tunnelRemoteIpAddress, elanInstanceName);
        List<BigInteger> elanDpns = DhcpServiceUtils.getDpnsForElan(elanInstanceName, broker);
        if (elanDpns == null || elanDpns.isEmpty()) {
            dhcpExternalTunnelManager.installRemoteMcastMac(designatedDpnId, tunnelRemoteIpAddress, elanInstanceName);
        }
    }

    @Override
    protected void add(InstanceIdentifier<DesignatedSwitchForTunnel> identifier, DesignatedSwitchForTunnel add) {
        BigInteger designatedDpnId = BigInteger.valueOf(add.getDpId());
        IpAddress tunnelRemoteIpAddress = add.getTunnelRemoteIpAddress();
        String elanInstanceName = add.getElanInstanceName();
        dhcpExternalTunnelManager.updateLocalCache(designatedDpnId, tunnelRemoteIpAddress, elanInstanceName);
        List<BigInteger> elanDpns = DhcpServiceUtils.getDpnsForElan(elanInstanceName, broker);
        if (elanDpns == null || elanDpns.isEmpty()) {
            dhcpExternalTunnelManager.installRemoteMcastMac(designatedDpnId, tunnelRemoteIpAddress, elanInstanceName);
        }
    }

    @Override
    protected InstanceIdentifier<DesignatedSwitchForTunnel> getWildCardPath() {
        return InstanceIdentifier.create(DesignatedSwitchesForExternalTunnels.class).child(DesignatedSwitchForTunnel.class);
    }

    @Override
    protected ClusteredDataChangeListener getDataChangeListener() {
        return DhcpDesignatedDpnListener.this;
    }

    @Override
    protected DataChangeScope getDataChangeScope() {
        return AsyncDataBroker.DataChangeScope.SUBTREE;
    }
}
