/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.renderer.ovs.statehelpers;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.utilities.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class OvsInterfaceTopologyStateUpdateHelper {
    private static final Logger LOG = LoggerFactory.getLogger(OvsInterfaceTopologyStateUpdateHelper.class);
    /*
     *  This code is used to handle only a dpnId change scenario for a particular change,
     * which is not expected to happen in usual cases.
     */
    public static List<ListenableFuture<Void>> updateBridgeRefEntry(InstanceIdentifier<OvsdbBridgeAugmentation> bridgeIid,
                                                                    OvsdbBridgeAugmentation bridgeNew,
                                                                    OvsdbBridgeAugmentation bridgeOld,
                                                                    DataBroker dataBroker) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();

        BigInteger dpnIdNew = IfmUtil.getDpnId(bridgeNew.getDatapathId());
        BigInteger dpnIdOld = IfmUtil.getDpnId(bridgeOld.getDatapathId());

        //delete bridge reference entry for the old dpn in interface meta operational DS
        InterfaceMetaUtils.deleteBridgeRefEntry(dpnIdOld, writeTransaction);

        // create bridge reference entry in interface meta operational DS
        InterfaceMetaUtils.createBridgeRefEntry(dpnIdNew, bridgeIid, writeTransaction);

        // handle pre-provisioning of tunnels for the newly connected dpn
        BridgeEntry bridgeEntry = InterfaceMetaUtils.getBridgeEntryFromConfigDS(dpnIdNew, dataBroker);
        if (bridgeEntry == null) {
            futures.add(writeTransaction.submit());
            return futures;
        }
        SouthboundUtils.addAllPortsToBridge(bridgeEntry, dataBroker, bridgeIid, bridgeNew, writeTransaction);

        futures.add(writeTransaction.submit());
        return futures;
    }
}