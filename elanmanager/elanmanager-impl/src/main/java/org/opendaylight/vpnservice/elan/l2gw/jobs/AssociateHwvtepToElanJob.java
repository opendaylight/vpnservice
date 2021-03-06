/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.elan.l2gw.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.vpnservice.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.vpnservice.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
* Created by ekvsver on 4/15/2016.
*/
public class AssociateHwvtepToElanJob implements Callable<List<ListenableFuture<Void>>> {
    DataBroker broker;
    L2GatewayDevice l2GatewayDevice;
    ElanInstance elanInstance;
    Devices l2Device;
    Integer defaultVlan;
    boolean createLogicalSwitch;
    private static final Logger LOG = LoggerFactory.getLogger(AssociateHwvtepToElanJob.class);

    public AssociateHwvtepToElanJob(DataBroker broker, L2GatewayDevice l2GatewayDevice, ElanInstance elanInstance,
            Devices l2Device, Integer defaultVlan, boolean createLogicalSwitch) {
        this.broker = broker;
        this.l2GatewayDevice = l2GatewayDevice;
        this.elanInstance = elanInstance;
        this.l2Device = l2Device;
        this.defaultVlan = defaultVlan;
        this.createLogicalSwitch = createLogicalSwitch;
        LOG.debug("created assosiate l2gw connection job for {} {} ", elanInstance.getElanInstanceName(),
                l2GatewayDevice.getHwvtepNodeId());
    }

    public String getJobKey() {
        return elanInstance.getElanInstanceName();
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        String hwvtepNodeId = l2GatewayDevice.getHwvtepNodeId();
        String elanInstanceName = elanInstance.getElanInstanceName();
        LOG.debug("running assosiate l2gw connection job for {} {} ", elanInstanceName, hwvtepNodeId);

        // Create Logical Switch if it's not created already in the device
        if (createLogicalSwitch) {
            LOG.info("creating logical switch {} for {} ", elanInstanceName, hwvtepNodeId);

            ListenableFuture<Void> lsCreateFuture = createLogicalSwitch(l2GatewayDevice, elanInstance);
            futures.add(lsCreateFuture);
        } else {
            String logicalSwitchName = ElanL2GatewayUtils.getLogicalSwitchFromElan(elanInstanceName);
            LOG.info("{} is already created in {}; adding remaining configurations", logicalSwitchName, hwvtepNodeId);

            LogicalSwitchAddedJob logicalSwitchAddedJob = new LogicalSwitchAddedJob(logicalSwitchName, l2Device,
                    l2GatewayDevice, defaultVlan);
            return logicalSwitchAddedJob.call();
        }

        return futures;
    }

    private ListenableFuture<Void> createLogicalSwitch(L2GatewayDevice l2GatewayDevice, ElanInstance elanInstance) {
        final String logicalSwitchName = ElanL2GatewayUtils.getLogicalSwitchFromElan(
                elanInstance.getElanInstanceName());
        String segmentationId = elanInstance.getVni().toString();

        if (LOG.isTraceEnabled()) {
            LOG.trace("logical switch {} is created on {} with VNI {}", logicalSwitchName,
                    l2GatewayDevice.getHwvtepNodeId(), segmentationId);
        }
        NodeId hwvtepNodeId = new NodeId(l2GatewayDevice.getHwvtepNodeId());
        InstanceIdentifier<LogicalSwitches> path = HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(hwvtepNodeId, new HwvtepNodeName(logicalSwitchName));
        LogicalSwitches logicalSwitch = HwvtepSouthboundUtils.createLogicalSwitch(logicalSwitchName,
                elanInstance.getDescription(), segmentationId);

        ListenableFuture<Void> lsCreateFuture = HwvtepUtils.addLogicalSwitch(broker, hwvtepNodeId, logicalSwitch);
        Futures.addCallback(lsCreateFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void noarg) {
                // Listener will be closed after all configuration completed
                // on hwvtep by
                // listener itself
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Successful in initiating logical switch {} creation", logicalSwitchName);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                LOG.error("Failed logical switch {} creation", logicalSwitchName, error);
            }
        });
        return lsCreateFuture;
    }
}
