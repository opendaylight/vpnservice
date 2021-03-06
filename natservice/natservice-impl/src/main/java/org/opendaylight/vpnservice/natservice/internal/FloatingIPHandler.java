/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.natservice.internal;

import java.math.BigInteger;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public interface FloatingIPHandler {

    void onAddFloatingIp(BigInteger dpnId, String routerId, Uuid networkId, String interfaceName, String externalIp,
                         String internalIp);

    void onRemoveFloatingIp(BigInteger dpnId, String routerId, Uuid networkId, String externalIp, String internalIp,
                            long label);

}
